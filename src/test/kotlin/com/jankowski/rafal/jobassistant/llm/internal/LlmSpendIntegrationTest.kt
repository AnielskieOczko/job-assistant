package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmSpendInsights
import com.jankowski.rafal.jobassistant.llm.SpendBucket
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rollup exists for exactly one reason, and one of these tests is it: a total must still be
 * right after the audit log it was accrued from has been purged.
 */
@IntegrationTest
internal class LlmSpendIntegrationTest(
    @Autowired private val auditor: LlmCallAuditor,
    @Autowired private val spend: LlmSpendInsights,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun reset() {
        jdbc.sql("delete from llm_call").update()
        jdbc.sql("delete from llm_spend_daily").update()
    }

    private fun entry(
        task: String = "EXTRACTION",
        modelProfile: String = "openrouter",
        modelName: String? = "minimax/minimax-m3",
        costUsd: BigDecimal? = BigDecimal("0.00100000"),
        error: String? = null,
    ) = LlmCallAuditor.AuditEntry(
        task = task,
        modelProfile = modelProfile,
        modelName = modelName,
        requestJson = "[]",
        responseText = "{}",
        error = error,
        costUsd = costUsd,
        inputTokens = 100,
        outputTokens = 50,
        cachedInputTokens = 40,
        reasoningOutputTokens = 10,
        latencyMs = 1_000,
        profileId = null,
    )

    @Test
    fun `each audited call is folded into the day's bucket`() {
        repeat(3) { auditor.record(entry()) }

        val lifetime = spend.report().summary.lifetime

        assertEquals(3, lifetime.calls)
        assertEquals(3, lifetime.pricedCalls)
        assertEquals(0, BigDecimal("0.00300000").compareTo(lifetime.costUsd))
        assertEquals(300, lifetime.inputTokens)
        assertEquals(120, lifetime.cachedInputTokens)
        assertEquals(30, lifetime.reasoningOutputTokens)
    }

    /**
     * The whole design in one assertion.
     *
     * `LlmCallRetention` deletes rows after thirty days and V11 cascade-deletes them with a
     * profile, so a total read out of `llm_call` would quietly shrink while still being labelled a
     * total. If this test ever fails, the read side has started consulting the audit log again.
     */
    @Test
    fun `purging the audit log does not change the accumulated total`() {
        repeat(4) { auditor.record(entry()) }
        val before = spend.report().summary.lifetime

        jdbc.sql("delete from llm_call").update()

        val after = spend.report().summary.lifetime
        assertEquals(0, jdbc.sql("select count(*) from llm_call").query(Int::class.java).single())
        assertEquals(before, after)
        assertEquals(4, after.calls)
    }

    /**
     * A local model reports no price. Counting the call but not claiming a price for it is what
     * makes the difference between a total and a floor visible - see `priced_calls` in V25.
     */
    @Test
    fun `an unpriced call is counted without inventing a price`() {
        auditor.record(entry(costUsd = BigDecimal("0.002"), modelProfile = "openrouter"))
        auditor.record(entry(costUsd = null, modelProfile = "local", modelName = "qwen3:8b"))

        val lifetime = spend.report().summary.lifetime

        assertEquals(2, lifetime.calls)
        assertEquals(1, lifetime.pricedCalls)
        assertEquals(0, BigDecimal("0.002").compareTo(lifetime.costUsd))
    }

    @Test
    fun `a failed call is counted as failed and costs nothing`() {
        auditor.record(entry(costUsd = null, error = "timeout"))

        val lifetime = spend.report().summary.lifetime

        assertEquals(1, lifetime.calls)
        assertEquals(1, lifetime.failedCalls)
        assertEquals(0, lifetime.pricedCalls)
        assertEquals(0, BigDecimal.ZERO.compareTo(lifetime.costUsd))
    }

    @Test
    fun `spend is broken down by task, model and profile`() {
        auditor.record(entry(task = "EXTRACTION", costUsd = BigDecimal("0.005")))
        auditor.record(entry(task = "NARRATIVE", costUsd = BigDecimal("0.001")))

        val report = spend.report()

        assertEquals(listOf("EXTRACTION", "NARRATIVE"), report.byTask.map { it.key })
        assertEquals(0, BigDecimal("0.005").compareTo(report.byTask.first().total.costUsd))
        assertEquals(listOf("minimax/minimax-m3"), report.byModel.map { it.key })
        assertEquals(listOf("openrouter"), report.byProfile.map { it.key })
        assertEquals(0, BigDecimal("0.006").compareTo(report.windowTotal.costUsd))
    }

    /**
     * A chart that omits quiet days compresses its own axis and makes an idle fortnight look like
     * continuous spending, so an empty bucket has to be present and zero rather than missing.
     */
    @Test
    fun `the series carries a point for every bucket, including empty ones`() {
        auditor.record(entry())

        val series = spend.report(windowDays = 7, bucket = SpendBucket.DAY).series
        val today = LocalDate.now(ZoneOffset.UTC)

        assertEquals(7, series.points.size)
        assertEquals(today, series.points.last().periodStart)
        assertEquals(1, series.points.last().total.calls)
        assertTrue(series.points.dropLast(1).all { it.total.calls == 0 })
    }

    /** "Lifetime" means since the rollup began, and the summary has to be able to say when that was. */
    @Test
    fun `the summary names the first day it holds`() {
        auditor.record(entry())

        assertEquals(LocalDate.now(ZoneOffset.UTC), spend.report().summary.recordedSince)
    }
}
