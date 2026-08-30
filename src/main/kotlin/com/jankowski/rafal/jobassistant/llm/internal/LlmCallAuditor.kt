package com.jankowski.rafal.jobassistant.llm.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Persists one `llm_call` row per model invocation, and folds it into the day's spend bucket.
 *
 * Runs in its own transaction: when an analysis job fails and rolls back, the record of what the
 * model actually returned is exactly what you need, so it must survive the rollback. The rollup
 * shares that transaction rather than getting its own, so the detail row and the total can never
 * disagree about whether a call happened.
 */
@Component
internal class LlmCallAuditor(private val jdbc: JdbcClient) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(entry: AuditEntry) {
        insertCall(entry)
        accrueSpend(entry)
    }

    private fun insertCall(entry: AuditEntry) {
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

    /**
     * Adds this call to its day's bucket, which is what survives `LlmCallRetention`.
     *
     * Incrementing rather than recomputing is the only option available: by the time a total is
     * asked for, the rows it would have been recomputed from are typically deleted. That is sound
     * here for a reason it would not be elsewhere - a model call happens exactly once and is never
     * re-observed, unlike a market offer that a daily poll sees again and again.
     *
     * `priced_calls` moves only when the provider actually reported a price, so a total can always
     * say how much of itself is measured rather than assumed.
     */
    private fun accrueSpend(entry: AuditEntry) {
        jdbc.sql(
            """
            insert into llm_spend_daily
                (day, task, model_profile, model_name, calls, failed_calls, priced_calls,
                 input_tokens, output_tokens, cached_input_tokens, reasoning_output_tokens,
                 cost_usd)
            values
                ((now() at time zone 'UTC')::date, :task, :profile, coalesce(:modelName, ''), 1,
                 :failed, :priced, :inputTokens, :outputTokens, :cachedInputTokens,
                 :reasoningOutputTokens, :costUsd)
            on conflict (day, task, model_profile, model_name) do update set
                calls                   = llm_spend_daily.calls + 1,
                failed_calls            = llm_spend_daily.failed_calls + excluded.failed_calls,
                priced_calls            = llm_spend_daily.priced_calls + excluded.priced_calls,
                input_tokens            = llm_spend_daily.input_tokens + excluded.input_tokens,
                output_tokens           = llm_spend_daily.output_tokens + excluded.output_tokens,
                cached_input_tokens     = llm_spend_daily.cached_input_tokens
                                            + excluded.cached_input_tokens,
                reasoning_output_tokens = llm_spend_daily.reasoning_output_tokens
                                            + excluded.reasoning_output_tokens,
                cost_usd                = llm_spend_daily.cost_usd + excluded.cost_usd
            """
        )
            .param("task", entry.task)
            .param("profile", entry.modelProfile)
            .param("modelName", entry.modelName)
            .param("failed", if (entry.error != null) 1 else 0)
            .param("priced", if (entry.costUsd != null) 1 else 0)
            .param("inputTokens", entry.inputTokens ?: 0)
            .param("outputTokens", entry.outputTokens ?: 0)
            .param("cachedInputTokens", entry.cachedInputTokens ?: 0)
            .param("reasoningOutputTokens", entry.reasoningOutputTokens ?: 0)
            .param("costUsd", entry.costUsd ?: BigDecimal.ZERO)
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
