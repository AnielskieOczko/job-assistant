package com.jankowski.rafal.jobassistant.llm.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Persists one `llm_call` row per model invocation.
 *
 * Runs in its own transaction: when an analysis job fails and rolls back, the record of what the
 * model actually returned is exactly what you need, so it must survive the rollback.
 */
@Component
internal class LlmCallAuditor(private val jdbc: JdbcClient) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(entry: AuditEntry) {
        jdbc.sql(
            """
            insert into llm_call
                (task, model_profile, model_name, serving_provider, provider_call_id, request_json,
                 response_text, error, cost_usd, input_tokens, output_tokens, cached_input_tokens,
                 reasoning_output_tokens, finish_reason, latency_ms, profile_id, subject_kind,
                 subject_id)
            values
                (:task, :profile, :modelName, :servingProvider, :providerCallId, :request,
                 :response, :error, :costUsd, :inputTokens, :outputTokens, :cachedInputTokens,
                 :reasoningOutputTokens, :finishReason, :latencyMs, :profileId, :subjectKind,
                 :subjectId)
            """
        )
            .param("task", entry.task)
            .param("profile", entry.modelProfile)
            .param("modelName", entry.modelName)
            .param("servingProvider", entry.servingProvider)
            .param("providerCallId", entry.providerCallId)
            .param("request", entry.requestJson)
            .param("response", entry.responseText)
            .param("error", entry.error)
            .param("costUsd", entry.costUsd)
            .param("inputTokens", entry.inputTokens)
            .param("outputTokens", entry.outputTokens)
            .param("cachedInputTokens", entry.cachedInputTokens)
            .param("reasoningOutputTokens", entry.reasoningOutputTokens)
            .param("finishReason", entry.finishReason)
            .param("latencyMs", entry.latencyMs)
            .param("profileId", entry.profileId)
            .param("subjectKind", entry.subjectKind)
            .param("subjectId", entry.subjectId)
            .update()
    }

    @Suppress("LongParameterList")
    internal data class AuditEntry(
        val task: String,
        val modelProfile: String,
        val modelName: String?,
        /** Upstream provider behind a router, when it reports one. Null for direct providers. */
        val servingProvider: String? = null,
        /** The provider's own generation id, and the join key to their billing dashboard. */
        val providerCallId: String? = null,
        val requestJson: String,
        val responseText: String?,
        val error: String?,
        /** What the account was charged. Null when the provider reports no price at all. */
        val costUsd: BigDecimal? = null,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val cachedInputTokens: Int? = null,
        val reasoningOutputTokens: Int? = null,
        val finishReason: String? = null,
        val latencyMs: Long?,
        /** Whose data this call concerned, so the row is erased with them. Null outside a scope. */
        val profileId: Long?,
        /** What caused the call, as an opaque label - see `LlmCallScope`. */
        val subjectKind: String? = null,
        val subjectId: Long? = null,
    )
}
