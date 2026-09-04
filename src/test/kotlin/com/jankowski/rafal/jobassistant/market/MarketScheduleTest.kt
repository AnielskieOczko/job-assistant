package com.jankowski.rafal.jobassistant.market

import com.jankowski.rafal.jobassistant.market.internal.MarketController
import com.jankowski.rafal.jobassistant.market.internal.MarketProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * What the dashboard is told about the schedule.
 *
 * Pure logic, so no container: the whole question is whether a configuration value is reported
 * faithfully. It matters because the page draws a conclusion from it — a corpus that refreshes
 * itself nightly and one that only moves when someone presses a button look identical in the
 * numbers, and the reader is told which they are looking at.
 */
class MarketScheduleTest {

    private fun controllerFor(properties: MarketProperties, lastSeen: Instant? = null) =
        MarketController(
            market = object : MarketOfferService {
                override fun ingest() = error("no test here polls a source")
                override fun corpusSummary() = listOfNotNull(
                    lastSeen?.let { CorpusSummary("solid.jobs", 1, 1, it, it) },
                )
            },
            // No test here promotes anything: this class is about what the schedule reports.
            promotion = object : MarketPromotion {
                override fun promote(marketOfferId: Long) = error("no test here promotes an offer")
            },
            properties = properties,
        )

    @Test
    fun `a scheduled poll reports its cron and the next run it implies`() {
        val schedule = controllerFor(MarketProperties(enabled = true, cron = "0 20 4 * * *")).ingestion()

        assertThat(schedule.scheduled).isTrue()
        assertThat(schedule.cron).isEqualTo("0 20 4 * * *")
        assertThat(schedule.nextPollAt).isNotNull().isAfter(Instant.now())
    }

    /*
      The cron is withheld rather than shown greyed out, for the same reason the dashboard's salary
      tiles report a count instead of an empty box: a next-run time for a poll that will never run
      is a worse answer than no time at all.
    */
    @Test
    fun `a disabled schedule reports no cron and no next run`() {
        val schedule = controllerFor(MarketProperties(enabled = false, cron = "0 20 4 * * *")).ingestion()

        assertThat(schedule.scheduled).isFalse()
        assertThat(schedule.cron).isNull()
        assertThat(schedule.nextPollAt).isNull()
    }

    /*
      A cron this malformed would have failed the scheduler at startup, so this branch should be
      unreachable in a running application. It is still parsed defensively: turning a configuration
      typo into a 500 on every dashboard load would hide the typo behind a broken page.
    */
    @Test
    fun `an unparseable cron yields no next run rather than an error`() {
        val schedule = controllerFor(MarketProperties(enabled = true, cron = "not a cron")).ingestion()

        assertThat(schedule.scheduled).isTrue()
        assertThat(schedule.cron).isEqualTo("not a cron")
        assertThat(schedule.nextPollAt).isNull()
    }

    @Test
    fun `the last poll is the corpus's own most recent observation`() {
        val seen = Instant.parse("2026-08-28T10:47:04Z")

        assertThat(controllerFor(MarketProperties(), lastSeen = seen).ingestion().lastPolledAt)
            .isEqualTo(seen)
        assertThat(controllerFor(MarketProperties()).ingestion().lastPolledAt).isNull()
    }
}
