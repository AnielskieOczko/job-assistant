package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.SpendBucket
import com.jankowski.rafal.jobassistant.llm.SpendGroup
import com.jankowski.rafal.jobassistant.llm.SpendPoint
import com.jankowski.rafal.jobassistant.llm.SpendTotal
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate

/**
 * Reads `llm_spend_daily`, and only `llm_spend_daily`.
 *
 * Never `llm_call`. That is not a style preference: the audit log is purged after thirty days and
 * cascade-deleted with a profile, so a total computed from it would quietly shrink over time while
 * still being labelled a total. Every figure here comes from the rollup, which is written in the
 * same transaction as each audit row and never deleted.
 *
 * Separate from [JdbcLlmCallLog] on the principle that separates `MarketStatisticsRepository` from
 * `MarketOfferRepository`: recording calls and describing what they cost are different jobs, and
 * only one of them aggregates.
 */
@Repository
internal class LlmSpendRepository(private val jdbc: JdbcClient) {

    /** Everything spent between [from] and [to] inclusive. Null bounds mean unbounded. */
    fun total(from: LocalDate?, to: LocalDate?): SpendTotal =
        jdbc.sql("select $TOTALS from llm_spend_daily where ${dayBetween(from, to)}")
            .bindRange(from, to)
            .query { rs, _ -> rs.toTotal() }
            .single()

    /**
     * One point per bucket in the range, including the empty ones.
     *
     * `generate_series` supplies the buckets and the data is left-joined onto it, so a fortnight
     * with no calls renders as a fortnight of zeroes rather than vanishing from the axis and making
     * the surrounding spend look continuous.
     */
    fun series(from: LocalDate, to: LocalDate, bucket: SpendBucket): List<SpendPoint> =
        jdbc.sql(
            """
            with periods as (
                select generate_series(
                    date_trunc(:unit, cast(:from as timestamp)),
                    date_trunc(:unit, cast(:to as timestamp)),
                    cast(:step as interval)
                )::date as period
            )
            select p.period, $TOTALS
            from periods p
            left join llm_spend_daily s
                on date_trunc(:unit, cast(s.day as timestamp))::date = p.period
            group by p.period
            order by p.period
            """
        )
            .param("unit", bucket.unit)
            .param("step", bucket.step)
            .param("from", from)
            .param("to", to)
            .query { rs, _ -> SpendPoint(rs.getDate("period").toLocalDate(), rs.toTotal()) }
            .list()

    /**
     * A breakdown of the range by one column, biggest spender first.
     *
     * [column] is not user input - it comes from a closed set of column names below - so
     * interpolating it is safe where a bind parameter is not even syntactically possible.
     */
    fun groups(from: LocalDate?, to: LocalDate?, column: String): List<SpendGroup> {
        require(column in GROUPABLE) { "not a groupable column: $column" }
        return jdbc.sql(
            """
            select $column as group_key, $TOTALS
            from llm_spend_daily
            where ${dayBetween(from, to)}
            group by $column
            order by sum(cost_usd) desc, sum(calls) desc
            """
        )
            .bindRange(from, to)
            .query { rs, _ ->
                SpendGroup(
                    // model_name is '' rather than null in the rollup so the primary key holds;
                    // it becomes a legible label only here, at the edge.
                    key = rs.getString("group_key").ifBlank { "unknown" },
                    total = rs.toTotal(),
                )
            }
            .list()
    }

    /** The first day the rollup holds, which is what "lifetime" actually means. */
    fun earliestDay(): LocalDate? =
        jdbc.sql("select min(day) as day from llm_spend_daily")
            .query { rs, _ -> rs.getDate("day")?.toLocalDate() }
            .optional()
            .orElse(null)

    private fun dayBetween(from: LocalDate?, to: LocalDate?): String = buildString {
        append("true")
        if (from != null) append(" and day >= cast(:from as date)")
        if (to != null) append(" and day <= cast(:to as date)")
    }

    private fun JdbcClient.StatementSpec.bindRange(from: LocalDate?, to: LocalDate?) =
        let { if (from == null) it else it.param("from", from) }
            .let { if (to == null) it else it.param("to", to) }

    private fun ResultSet.toTotal() = SpendTotal(
        costUsd = getBigDecimal("cost_usd") ?: BigDecimal.ZERO,
        calls = getInt("calls"),
        pricedCalls = getInt("priced_calls"),
        failedCalls = getInt("failed_calls"),
        inputTokens = getLong("input_tokens"),
        outputTokens = getLong("output_tokens"),
        cachedInputTokens = getLong("cached_input_tokens"),
        reasoningOutputTokens = getLong("reasoning_output_tokens"),
    )

    internal companion object {
        const val TASK = "task"
        const val MODEL_NAME = "model_name"
        const val MODEL_PROFILE = "model_profile"

        private val GROUPABLE = setOf(TASK, MODEL_NAME, MODEL_PROFILE)

        /**
         * Aggregates every caller needs, in one place so the column list cannot drift between the
         * total, the series and the breakdowns and make three views of one day disagree.
         *
         * `coalesce` throughout because a left-joined empty bucket sums to null, and a bucket with
         * no calls costs zero rather than costing nothing-in-particular.
         */
        private const val TOTALS = """
            coalesce(sum(cost_usd), 0)                as cost_usd,
            coalesce(sum(calls), 0)                   as calls,
            coalesce(sum(priced_calls), 0)            as priced_calls,
            coalesce(sum(failed_calls), 0)            as failed_calls,
            coalesce(sum(input_tokens), 0)            as input_tokens,
            coalesce(sum(output_tokens), 0)           as output_tokens,
            coalesce(sum(cached_input_tokens), 0)     as cached_input_tokens,
            coalesce(sum(reasoning_output_tokens), 0) as reasoning_output_tokens
        """
    }
}

private val SpendBucket.unit: String
    get() = when (this) {
        SpendBucket.DAY -> "day"
        SpendBucket.WEEK -> "week"
        SpendBucket.MONTH -> "month"
    }

private val SpendBucket.step: String
    get() = "1 ${unit}"
