package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmCall
import com.jankowski.rafal.jobassistant.llm.LlmCallDetail
import com.jankowski.rafal.jobassistant.llm.LlmCallLog
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.sql.ResultSet

@Service
internal class JdbcLlmCallLog(private val jdbc: JdbcClient) : LlmCallLog {

    override fun recent(limit: Int): List<LlmCall> =
        jdbc.sql("$SUMMARY_COLUMNS order by created_at desc limit :limit")
            .param("limit", limit)
            .query { rs, _ -> rs.toCall() }
            .list()

    override fun detail(id: Long): LlmCallDetail? =
        jdbc.sql("select *, extract(epoch from created_at) from llm_call where id = :id")
            .param("id", id)
            .query { rs, _ ->
                LlmCallDetail(
                    call = rs.toCall(),
                    requestJson = rs.getString("request_json"),
                    responseText = rs.getString("response_text"),
                )
            }
            .optional()
            .orElse(null)

    private fun ResultSet.toCall() = LlmCall(
        id = getLong("id"),
        task = getString("task"),
        modelProfile = getString("model_profile"),
        modelName = getString("model_name"),
        servingProvider = getString("serving_provider"),
        providerCallId = getString("provider_call_id"),
        costUsd = getBigDecimal("cost_usd"),
        inputTokens = getObject("input_tokens") as Int?,
        outputTokens = getObject("output_tokens") as Int?,
        cachedInputTokens = getObject("cached_input_tokens") as Int?,
        reasoningOutputTokens = getObject("reasoning_output_tokens") as Int?,
        finishReason = getString("finish_reason"),
        latencyMs = getObject("latency_ms") as Long?,
        error = getString("error"),
        subjectKind = getString("subject_kind"),
        // A nullable bigint read as getLong is 0, not null - JdbcClient carries none of Spring
        // Data's converters.
        subjectId = getLong("subject_id").takeUnless { wasNull() },
        createdAt = getTimestamp("created_at").toInstant(),
    )

    private companion object {
        const val SUMMARY_COLUMNS = """
            select id, task, model_profile, model_name, serving_provider, provider_call_id,
                   cost_usd, input_tokens, output_tokens, cached_input_tokens,
                   reasoning_output_tokens, finish_reason, latency_ms, error, subject_kind,
                   subject_id, created_at
            from llm_call
        """
    }
}
