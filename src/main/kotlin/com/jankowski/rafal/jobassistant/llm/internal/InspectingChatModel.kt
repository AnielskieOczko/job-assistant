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
 * Runs every [OutboundPromptInspector] over a request before letting it through, re-asks once when
 * the answer comes back empty, and normalises an empty response on the way back.
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
        return normalised(askAgainIfSilent(delegate.chat(chatRequest)) { delegate.chat(chatRequest) })
    }

    override fun chat(chatRequest: ChatRequest, options: ChatRequestOptions): ChatResponse {
        inspect(chatRequest)
        return normalised(
            askAgainIfSilent(delegate.chat(chatRequest, options)) { delegate.chat(chatRequest, options) }
        )
    }

    /** Also guarded, so calling the lower entry point directly cannot slip past the check. */
    override fun doChat(chatRequest: ChatRequest): ChatResponse {
        inspect(chatRequest)
        return normalised(askAgainIfSilent(delegate.doChat(chatRequest)) { delegate.doChat(chatRequest) })
    }

    override fun listeners(): List<ChatModelListener> = delegate.listeners()

    override fun defaultRequestParameters(): ChatRequestParameters = delegate.defaultRequestParameters()

    override fun supportedCapabilities(): Set<Capability> = delegate.supportedCapabilities()

    override fun provider() = delegate.provider()

    /**
     * Asks the same question once more when the model answered with nothing at all.
     *
     * **This is the only layer that can repair a bad answer, because it is the only one still
     * holding the question.** `JsonOutputGuardrail` used to reprompt, but a guardrail reprompt is
     * built from `chatMemory()`, and these AI services deliberately have none - so LangChain4j sent
     * the correction instruction as the sole message and the model answered a question it had never
     * been asked. That answer was well-formed JSON and reached a user as a suggestion. Here the
     * original [ChatRequest] is in hand, so the retry carries the system prompt and the request
     * verbatim.
     *
     * Once, and only for a *silent* answer - reasoning models routinely spend a whole completion on
     * `thinking` and emit no content, which is a wasted call rather than a considered refusal.
     * Anything with text in it is passed on: whether prose is acceptable is the guardrail's
     * question, not this one's, and re-asking a model that answered the wrong way is a different
     * bet from re-asking one that did not answer.
     *
     * The retry goes through [delegate], so it lands in `llm_call` as its own row. Two rows for one
     * logical call is the honest record: it cost two.
     */
    private inline fun askAgainIfSilent(first: ChatResponse, retry: () -> ChatResponse): ChatResponse =
        if (isSilent(first)) retry() else first

    private fun isSilent(response: ChatResponse): Boolean =
        response.aiMessage()?.text().isNullOrBlank()

    /**
     * Replaces a null response text with an empty string.
     *
     * A model that emits no content at all - which reasoning models do when they produce only
     * `thinking` - hands back an `AiMessage` whose `text()` is null. LangChain4j 1.19 dereferences
     * that null in `OutputGuardrailExecutor.rewriteResult`, which compares
     * `originalText.equals(validatedText)` without checking the original, so a null text can take
     * the call down with a `NullPointerException` rather than the guardrail's own verdict.
     *
     * Handing the guardrail `""` instead costs nothing - it fails through its "contained no JSON"
     * branch rather than its "returned no text" branch, and both say the same thing to the caller -
     * and leaves `rewriteResult` a non-null string to compare. Remove this when the upstream null
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
