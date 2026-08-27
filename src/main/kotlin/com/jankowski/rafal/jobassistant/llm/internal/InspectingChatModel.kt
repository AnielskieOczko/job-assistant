package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.OutboundPromptInspector
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.ChatRequestOptions
import dev.langchain4j.model.chat.Capability
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ChatRequestParameters
import dev.langchain4j.model.chat.response.ChatResponse

/**
 * Runs every [OutboundPromptInspector] over a request before letting it through, and normalises
 * an empty response on the way back.
 *
 * Wraps rather than extends, and inspects *above* the delegate's own `chat` entry point, so a
 * refusal happens before the listener pipeline runs. That ordering matters: were the check to fail
 * further down, the audit listener would already have fired and written the offending prompt into
 * `llm_call`, storing the very data the check exists to stop.
 *
 * A LangChain4j `InputGuardrail` cannot do this job: `InputGuardrailRequest` exposes only the
 * `UserMessage`, so a system prompt that interpolated something sensitive would sail past it.
 *
 * The response side is a workaround, and deliberately lives here because this is the only place
 * every model call already passes through. See [normalised].
 */
internal class InspectingChatModel(
    private val delegate: ChatModel,
    private val inspectors: List<OutboundPromptInspector>,
) : ChatModel {

    override fun chat(chatRequest: ChatRequest): ChatResponse {
        inspect(chatRequest)
        return normalised(delegate.chat(chatRequest))
    }

    override fun chat(chatRequest: ChatRequest, options: ChatRequestOptions): ChatResponse {
        inspect(chatRequest)
        return normalised(delegate.chat(chatRequest, options))
    }

    /** Also guarded, so calling the lower entry point directly cannot slip past the check. */
    override fun doChat(chatRequest: ChatRequest): ChatResponse {
        inspect(chatRequest)
        return normalised(delegate.doChat(chatRequest))
    }

    override fun listeners(): List<ChatModelListener> = delegate.listeners()

    override fun defaultRequestParameters(): ChatRequestParameters = delegate.defaultRequestParameters()

    override fun supportedCapabilities(): Set<Capability> = delegate.supportedCapabilities()

    override fun provider() = delegate.provider()

    /**
     * Replaces a null response text with an empty string.
     *
     * A model that emits no content at all - which reasoning models do when they produce only
     * `thinking` - hands back an `AiMessage` whose `text()` is null. `JsonOutputGuardrail` handles
     * that correctly and reprompts, and the reprompt usually succeeds. LangChain4j 1.19 then
     * throws the recovery away: `OutputGuardrailExecutor.rewriteResult` compares the reprompted
     * text against the original with `originalText.equals(validatedText)` without checking the
     * original for null, so the successful second call dies as a `NullPointerException`.
     *
     * Handing the guardrail `""` instead costs nothing - it reprompts through its "contained no
     * JSON" branch rather than its "returned no text" branch - and leaves `rewriteResult` a
     * non-null string to compare, so the reprompt survives. Remove this when the upstream null
     * check lands.
     */
    private fun normalised(response: ChatResponse): ChatResponse {
        val message: AiMessage? = response.aiMessage()
        if (message == null || message.text() != null) return response
        return ChatResponse.Builder(response).aiMessage(message.withText("")).build()
    }

    private fun inspect(chatRequest: ChatRequest) {
        if (inspectors.isEmpty()) return
        val rendered = chatRequest.messages().joinToString("\n", transform = ::textOf)
        inspectors.forEach { it.inspect(rendered) }
    }

    private fun textOf(message: ChatMessage): String = when (message) {
        is SystemMessage -> message.text()
        is UserMessage -> message.singleText()
        else -> message.toString()
    }
}
