package com.jankowski.rafal.jobassistant.support

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.model.output.TokenUsage
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A [ChatModel] that returns responses you queued up front.
 *
 * Only `doChat` is overridden, so LangChain4j's default `chat` still runs the listener pipeline -
 * which means the audit trail is exercised by tests exactly as it is in production, without a
 * network call.
 */
class ScriptedChatModel(
    private val listeners: List<ChatModelListener> = emptyList(),
    private val modelName: String = "scripted-model",
) : ChatModel {

    private val scripted = ConcurrentLinkedQueue<Reply>()
    private val seen = ConcurrentLinkedQueue<ChatRequest>()

    val requests: List<ChatRequest> get() = seen.toList()

    fun enqueue(vararg responses: String) = apply {
        responses.forEach { scripted.add(Reply.Text(it)) }
    }

    fun enqueueFailure(error: RuntimeException) = apply { scripted.add(Reply.Failure(error)) }

    fun reset() = apply {
        scripted.clear()
        seen.clear()
    }

    override fun listeners(): List<ChatModelListener> = listeners

    override fun doChat(chatRequest: ChatRequest): ChatResponse {
        seen.add(chatRequest)

        return when (val reply = scripted.poll()) {
            null -> error("ScriptedChatModel ran out of responses after ${seen.size} request(s)")
            is Reply.Failure -> throw reply.error
            is Reply.Text -> ChatResponse.builder()
                .aiMessage(AiMessage.from(reply.value))
                .modelName(modelName)
                .tokenUsage(TokenUsage(11, 22))
                .finishReason(FinishReason.STOP)
                .build()
        }
    }

    private sealed interface Reply {
        data class Text(val value: String) : Reply
        data class Failure(val error: RuntimeException) : Reply
    }
}
