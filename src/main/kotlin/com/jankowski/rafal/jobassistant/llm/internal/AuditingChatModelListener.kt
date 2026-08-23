package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmTask
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

/**
 * Writes an audit row for every call. One listener instance is bound to one task, which is why
 * models are built per task rather than per profile - it is the only way the row knows what the
 * call was for.
 */
internal class AuditingChatModelListener(
    private val task: LlmTask,
    private val profileName: String,
    private val auditor: LlmCallAuditor,
    private val json: JsonMapper,
) : ChatModelListener {

    private val log = LoggerFactory.getLogger(AuditingChatModelListener::class.java)

    override fun onRequest(requestContext: ChatModelRequestContext) {
        requestContext.attributes()[START_NANOS] = System.nanoTime()
    }

    override fun onResponse(responseContext: ChatModelResponseContext) {
        val usage = responseContext.chatResponse().metadata().tokenUsage()
        audit(
            requestJson = renderRequest(responseContext.chatRequest().messages()),
            responseText = responseContext.chatResponse().aiMessage()?.text(),
            error = null,
            modelName = responseContext.chatResponse().metadata().modelName(),
            inputTokens = usage?.inputTokenCount(),
            outputTokens = usage?.outputTokenCount(),
            elapsedNanos = responseContext.attributes()[START_NANOS] as Long?,
        )
    }

    override fun onError(errorContext: ChatModelErrorContext) {
        audit(
            requestJson = renderRequest(errorContext.chatRequest().messages()),
            responseText = null,
            error = errorContext.error().toString(),
            modelName = null,
            inputTokens = null,
            outputTokens = null,
            elapsedNanos = errorContext.attributes()[START_NANOS] as Long?,
        )
    }

    private fun audit(
        requestJson: String,
        responseText: String?,
        error: String?,
        modelName: String?,
        inputTokens: Int?,
        outputTokens: Int?,
        elapsedNanos: Long?,
    ) {
        // Auditing must never be the reason a call fails.
        runCatching {
            auditor.record(
                LlmCallAuditor.AuditEntry(
                    task = task.name,
                    modelProfile = profileName,
                    modelName = modelName,
                    requestJson = requestJson,
                    responseText = responseText,
                    error = error,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    latencyMs = elapsedNanos?.let { (System.nanoTime() - it) / 1_000_000 },
                )
            )
        }.onFailure { log.warn("Failed to audit {} call: {}", task, it.message) }
    }

    private fun renderRequest(messages: List<ChatMessage>): String =
        json.writeValueAsString(
            messages.map { message ->
                mapOf(
                    "role" to when (message) {
                        is SystemMessage -> "system"
                        is UserMessage -> "user"
                        else -> message.type().name.lowercase()
                    },
                    "text" to when (message) {
                        is SystemMessage -> message.text()
                        is UserMessage -> message.singleText()
                        else -> message.toString()
                    },
                )
            }
        )

    private companion object {
        const val START_NANOS = "job-assistant.start-nanos"
    }
}
