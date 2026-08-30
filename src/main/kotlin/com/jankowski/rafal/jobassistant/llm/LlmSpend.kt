package com.jankowski.rafal.jobassistant.llm

import java.math.BigDecimal
import java.time.LocalDate

/**
 * What the models cost, read from the daily rollup rather than from the audit log.
 *
 * Separate from [LlmCallLog] because they answer different questions over different lifetimes: the
 * call log is per-call debugging evidence that ages out after thirty days, and this is a total that
 * has to still be right in a year. Nothing here ever reads `llm_call` - that is precisely what
 * makes these figures survive a purge.
 *
 * **Every figure carries its coverage.** A total is only a total if the calls behind it were
 * priced; where they were not, it is a floor. See [SpendTotal.pricedCalls].
 */
interface LlmSpendInsights {

    /**
     * Everything one dashboard needs, in one read.
     *
     * @param windowDays how far back the series and the breakdowns look. The summary's own figures
     *   are fixed windows and ignore it, so a narrow view cannot make the lifetime total shrink.
     */
    fun report(windowDays: Int = DEFAULT_WINDOW_DAYS, bucket: SpendBucket = SpendBucket.DAY): SpendReport

    companion object {
        const val DEFAULT_WINDOW_DAYS: Int = 30
        const val MAX_WINDOW_DAYS: Int = 730
    }
}

/**
 * Spend over some slice, and the counts that say how much of it is measured.
 *
 * [pricedCalls] below [calls] means [costUsd] is a **floor**: the remaining calls went to a
 * provider that reported no price at all (a local model), and pricing them would require guessing.
 * Rendering the money without this pair is the mistake this type exists to make awkward.
 */
data class SpendTotal(
    val costUsd: BigDecimal,
    val calls: Int,
    val pricedCalls: Int,
    val failedCalls: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    /** Part of [inputTokens], billed at a discount. A cache that stopped working shows up here. */
    val cachedInputTokens: Long,
    /** Part of [outputTokens]. Paid for, and never visible in any response text. */
    val reasoningOutputTokens: Long,
) {
    companion object {
        val NONE = SpendTotal(BigDecimal.ZERO, 0, 0, 0, 0, 0, 0, 0)
    }
}

/** Fixed windows, plus what limits are in force. */
data class SpendSummary(
    val today: SpendTotal,
    val last7Days: SpendTotal,
    val last30Days: SpendTotal,
    val lifetime: SpendTotal,
    /**
     * The first day the rollup holds.
     *
     * "Lifetime" means since this day, not since the application was written: spend before cost
     * capture existed was never recorded and cannot be recovered. Saying so is the difference
     * between a total and an understatement presented as a total.
     */
    val recordedSince: LocalDate?,
    val budget: BudgetStatus,
)

/**
 * The configured caps and how close the current periods are to them.
 *
 * A limit of null is not a limit of zero - it means no cap is configured and nothing will be
 * refused.
 */
data class BudgetStatus(
    val dailyLimitUsd: BigDecimal?,
    val dailySpentUsd: BigDecimal,
    val monthlyLimitUsd: BigDecimal?,
    val monthlySpentUsd: BigDecimal,
    /** True when a cap is set and already reached, so the next call will be refused. */
    val exhausted: Boolean,
)

enum class SpendBucket { DAY, WEEK, MONTH }

data class SpendPoint(val periodStart: LocalDate, val total: SpendTotal)

/**
 * Spend over time.
 *
 * Buckets with no calls are present and zero rather than absent. A chart that silently omits quiet
 * days compresses its own x-axis and makes an idle fortnight look like continuous spending.
 */
data class SpendSeries(
    val bucket: SpendBucket,
    val from: LocalDate,
    val to: LocalDate,
    val points: List<SpendPoint>,
)

/** One row of a breakdown - a task name, a model name or a profile name. */
data class SpendGroup(val key: String, val total: SpendTotal)

data class SpendReport(
    val summary: SpendSummary,
    val series: SpendSeries,
    val windowDays: Int,
    val byTask: List<SpendGroup>,
    val byModel: List<SpendGroup>,
    val byProfile: List<SpendGroup>,
    /** Spend inside [windowDays], the denominator for every share the breakdowns imply. */
    val windowTotal: SpendTotal,
)
