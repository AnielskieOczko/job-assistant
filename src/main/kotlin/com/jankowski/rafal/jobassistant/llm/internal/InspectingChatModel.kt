package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.OutboundPromptInspector
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
 * Runs every [OutboundPromptInspector] over a request before letting it through.
 *
 * Wraps rather than extends, and inspects *above* the delegate's own `chat` entry point, so a
 * refusal happens before the listener pipeline runs. That ordering matters: were the check to fail
 * further down, the audit listener would already have fired and written the offending prompt into
 * `llm_call`, storing the very data the check exists to stop.
 *
 * A LangChain4j `InputGuardrail` cannot do this job: `InputGuardrailRequest` exposes only the
 * `UserMessage`, so a system prompt that interpolated something sensitive would sail past it.
 */
internal class InspectingChatModel(
    private val delegate: ChatModel,
    private val inspectors: List<OutboundPromptInspector>,
) : ChatModel {

    override fun chat(chatRequest: ChatRequest): ChatResponse {
        inspect(chatRequest)
        return delegate.chat(chatRequest)
    }

    override fun chat(chatRequest: ChatRequest, options: ChatRequestOptions): ChatResponse {
        inspect(chatRequest)
        return delegate.chat(chatRequest, options)
    }

    /** Also guarded, so calling the lower entry point directly cannot slip past the check. */
    override fun doChat(chatRequest: ChatRequest): ChatResponse {
        inspect(chatRequest)
        return delegate.doChat(chatRequest)
    }

    override fun listeners(): List<ChatModelListener> = delegate.listeners()

    override fun defaultRequestParameters(): ChatRequestParameters = delegate.defaultRequestParameters()

    override fun supportedCapabilities(): Set<Capability> = delegate.supportedCapabilities()

    override fun provider() = delegate.provider()

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
