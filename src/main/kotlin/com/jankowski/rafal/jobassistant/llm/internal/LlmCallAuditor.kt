package com.jankowski.rafal.jobassistant.llm.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

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
                (task, model_profile, model_name, serving_provider, request_json, response_text,
                 error, input_tokens, output_tokens, latency_ms, profile_id)
            values
                (:task, :profile, :modelName, :servingProvider, :request, :response,
                 :error, :inputTokens, :outputTokens, :latencyMs, :profileId)
            """
        )
            .param("task", entry.task)
            .param("profile", entry.modelProfile)
            .param("modelName", entry.modelName)
            .param("servingProvider", entry.servingProvider)
            .param("request", entry.requestJson)
            .param("response", entry.responseText)
            .param("error", entry.error)
            .param("inputTokens", entry.inputTokens)
            .param("outputTokens", entry.outputTokens)
            .param("latencyMs", entry.latencyMs)
            .param("profileId", entry.profileId)
            .update()
    }

    internal data class AuditEntry(
        val task: String,
        val modelProfile: String,
        val modelName: String?,
        /** Upstream provider behind a router, when it reports one. Null for direct providers. */
        val servingProvider: String? = null,
        val requestJson: String,
        val responseText: String?,
        val error: String?,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val latencyMs: Long?,
        /** Whose data this call concerned, so the row is erased with them. Null outside a scope. */
        val profileId: Long?,
    )
}
