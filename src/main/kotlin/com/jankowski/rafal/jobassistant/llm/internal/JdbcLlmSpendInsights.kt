package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.BudgetStatus
import com.jankowski.rafal.jobassistant.llm.LlmSpendInsights
import com.jankowski.rafal.jobassistant.llm.SpendBucket
import com.jankowski.rafal.jobassistant.llm.SpendReport
import com.jankowski.rafal.jobassistant.llm.SpendSeries
import com.jankowski.rafal.jobassistant.llm.SpendSummary
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Assembles the spend dashboard out of [LlmSpendRepository].
 *
 * All windows are UTC days, the same boundary the rollup is bucketed on and the same one the
 * providers' dashboards use - a locally-shifted "today" would disagree with the invoice by a few
 * hours' spend and there would be no way to tell that from a bug.
 */
@Service
internal class JdbcLlmSpendInsights(
    private val spend: LlmSpendRepository,
    private val properties: LlmProperties,
) : LlmSpendInsights {

    override fun report(windowDays: Int, bucket: SpendBucket): SpendReport {
        val days = windowDays.coerceIn(1, LlmSpendInsights.MAX_WINDOW_DAYS)
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.minusDays((days - 1).toLong())

        return SpendReport(
            summary = summary(today),
            series = SpendSeries(
                bucket = bucket,
                from = from,
                to = today,
                points = spend.series(from, today, bucket),
            ),
            windowDays = days,
            byTask = spend.groups(from, today, LlmSpendRepository.TASK),
            byModel = spend.groups(from, today, LlmSpendRepository.MODEL_NAME),
            byProfile = spend.groups(from, today, LlmSpendRepository.MODEL_PROFILE),
            // The denominator for every share the breakdowns imply. Recomputed rather than summed
            // from byTask so that a breakdown row lost to a filter cannot silently shrink the base.
            windowTotal = spend.total(from, today),
        )
    }

    private fun summary(today: LocalDate) = SpendSummary(
        today = spend.total(today, today),
        last7Days = spend.total(today.minusDays(6), today),
        last30Days = spend.total(today.minusDays(29), today),
        lifetime = spend.total(null, null),
        recordedSince = spend.earliestDay(),
        budget = budgetStatus(today),
    )

    /**
     * What the caps are and how much of each period is already gone.
     *
     * Read here as well as in the guard so the UI can warn *before* a button is pressed rather
     * than only explain a refusal afterwards.
     */
    private fun budgetStatus(today: LocalDate): BudgetStatus {
        val limits = properties.budget
        val daily = spend.total(today, today).costUsd
        val monthly = spend.total(today.withDayOfMonth(1), today).costUsd

        return BudgetStatus(
            dailyLimitUsd = limits.dailyUsd,
            dailySpentUsd = daily,
            monthlyLimitUsd = limits.monthlyUsd,
            monthlySpentUsd = monthly,
            exhausted = limits.dailyUsd?.let { daily >= it } == true ||
                limits.monthlyUsd?.let { monthly >= it } == true,
        )
    }
}
