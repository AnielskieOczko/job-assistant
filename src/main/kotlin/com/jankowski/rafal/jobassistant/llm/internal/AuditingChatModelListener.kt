package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmCallScope
import com.jankowski.rafal.jobassistant.llm.LlmTask
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import dev.langchain4j.model.chat.response.ChatResponseMetadata
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata
import dev.langchain4j.model.openai.OpenAiTokenUsage
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal

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
        val metadata = responseContext.chatResponse().metadata()
        val usage = metadata.tokenUsage()
        val reported = reportedBy(metadata)
        val details = usage as? OpenAiTokenUsage

        audit(
            requestJson = renderRequest(responseContext.chatRequest().messages()),
            responseText = responseContext.chatResponse().aiMessage()?.text(),
            error = null,
            modelName = metadata.modelName(),
            servingProvider = reported.servingProvider,
            providerCallId = reported.generationId,
            costUsd = reported.costUsd,
            inputTokens = usage?.inputTokenCount(),
            outputTokens = usage?.outputTokenCount(),
            // Prefer LangChain4j's typed breakdown, which also works against a plain OpenAI
            // endpoint that has no router extensions, and fall back to the raw body.
            cachedInputTokens = details?.inputTokensDetails()?.cachedTokens()
                ?: reported.cachedInputTokens,
            reasoningOutputTokens = details?.outputTokensDetails()?.reasoningTokens()
                ?: reported.reasoningOutputTokens,
            finishReason = metadata.finishReason()?.name,
            elapsedNanos = responseContext.attributes()[START_NANOS] as Long?,
        )
    }

    /**
     * Everything the provider reported that LangChain4j's parsed types do not model.
     *
     * Cost, the generation id and the serving provider are all extensions on the OpenAI schema
     * rather than part of it, so a provider-neutral library drops them on the floor - deliberately,
     * because the same types have to work against an endpoint that has none of them. The raw body
     * they were dropped from is kept, so a permissive read off it is the whole mechanism. See
     * `docs/research/11-model-call-cost.md`.
     *
     * Best effort by design: absent for every provider that is not a router, and a failure to read
     * it must never cost us the rest of the audit row.
     */
    private fun reportedBy(metadata: ChatResponseMetadata): CompletionMetadata =
        completionMetadataIn((metadata as? OpenAiChatResponseMetadata)?.rawHttpResponse()?.body(), json)

    override fun onError(errorContext: ChatModelErrorContext) {
        // No response body, so no cost: nothing was completed and nothing was charged.
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

    @Suppress("LongParameterList")
    private fun audit(
        requestJson: String,
        responseText: String?,
        error: String?,
        modelName: String?,
        servingProvider: String? = null,
        providerCallId: String? = null,
        costUsd: BigDecimal? = null,
        inputTokens: Int?,
        outputTokens: Int?,
        cachedInputTokens: Int? = null,
        reasoningOutputTokens: Int? = null,
        finishReason: String? = null,
        elapsedNanos: Long?,
    ) {
        // Auditing must never be the reason a call fails.
        runCatching {
            auditor.record(
                LlmCallAuditor.AuditEntry(
                    task = task.name,
                    modelProfile = profileName,
                    modelName = modelName,
                    servingProvider = servingProvider,
                    providerCallId = providerCallId,
                    requestJson = requestJson,
                    responseText = responseText,
                    error = error,
                    costUsd = costUsd,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    cachedInputTokens = cachedInputTokens,
                    reasoningOutputTokens = reasoningOutputTokens,
                    finishReason = finishReason,
                    latencyMs = elapsedNanos?.let { (System.nanoTime() - it) / 1_000_000 },
                    profileId = LlmCallScope.currentProfileId(),
                    subjectKind = LlmCallScope.currentSubjectKind(),
                    subjectId = LlmCallScope.currentSubjectId(),
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

/**
 * What a completion body said about itself, beyond the answer.
 *
 * Every field is nullable and every field is optional: a local model reports no price, a direct
 * provider reports no routing, and an OpenAI-compatible endpoint that predates any of this reports
 * neither. Absent is the normal case, never an error.
 */
internal data class CompletionMetadata(
    /** The upstream provider a router says actually answered. */
    val servingProvider: String? = null,
    /** The provider's own id for this generation, and the join key to their dashboard. */
    val generationId: String? = null,
    /** What the account was charged, in the provider's own billing unit. */
    val costUsd: BigDecimal? = null,
    val cachedInputTokens: Int? = null,
    val reasoningOutputTokens: Int? = null,
) {
    internal companion object {
        /** Nothing was reported - the outcome for every provider that is not a router. */
        val NONE = CompletionMetadata()
    }
}

/**
 * Reads the provider's own extensions out of a raw completion body.
 *
 * Best effort by design. The body is whatever the far end chose to send, so a parse failure must
 * cost nothing - losing the whole audit row to a malformed response would discard exactly the
 * evidence the row exists to keep, and a missing cost is worth strictly less than a missing prompt.
 *
 * Pure, and tested directly rather than through the listener: `ScriptedChatModel` builds a base
 * `ChatResponse` with no raw body at all, so no integration test can reach this path.
 */
internal fun completionMetadataIn(body: String?, json: JsonMapper): CompletionMetadata {
    if (body.isNullOrBlank()) return CompletionMetadata.NONE

    return runCatching {
        val root = json.readTree(body)
        val usage = root.field("usage")
        CompletionMetadata(
            servingProvider = root.stringField("provider"),
            generationId = root.stringField("id"),
            costUsd = usage?.decimalField("cost"),
            cachedInputTokens = usage?.field("prompt_tokens_details")?.intField("cached_tokens"),
            reasoningOutputTokens = usage?.field("completion_tokens_details")
                ?.intField("reasoning_tokens"),
        )
    }.getOrDefault(CompletionMetadata.NONE)
}

private fun JsonNode.field(name: String): JsonNode? = get(name)?.takeUnless { it.isNull }

private fun JsonNode.stringField(name: String): String? =
    field(name)?.takeIf { it.isString }?.asString()?.ifBlank { null }

private fun JsonNode.decimalField(name: String): BigDecimal? =
    field(name)?.takeIf { it.isNumber }?.decimalValue()

private fun JsonNode.intField(name: String): Int? =
    field(name)?.takeIf { it.isIntegralNumber }?.asInt()
