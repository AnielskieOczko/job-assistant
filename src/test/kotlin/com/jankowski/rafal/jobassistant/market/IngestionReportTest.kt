package com.jankowski.rafal.jobassistant.market

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The rate rule, applied to ingestion.
 *
 * The eval tier learned this the hard way: a ratio with a denominator of one is indistinguishable
 * from a ratio with a denominator of ninety, and reading it as quality is how a broken run passes.
 */
class IngestionReportTest {

    @Test
    fun `a resolution rate needs a sample before it means anything`() {
        assertThat(report(mentions = 3, resolved = 3).skillResolutionRate).isNull()
        assertThat(report(mentions = 3, resolved = 3).skillsResolved).isEqualTo(3)
    }

    @Test
    fun `above the minimum the rate is reported alongside its counts`() {
        val r = report(mentions = 40, resolved = 30)
        assertThat(r.skillResolutionRate).isEqualTo(0.75)
        assertThat(r.skillsUnresolved).isEqualTo(10)
    }

    private fun report(mentions: Int, resolved: Int) = IngestionReport(
        source = "solid.jobs",
        startedAt = Instant.EPOCH,
        finishedAt = Instant.EPOCH,
        pagesFetched = 1,
        offersSeen = 1,
        offersInserted = 1,
        offersUpdated = 0,
        skillMentions = mentions,
        skillsResolved = resolved,
        distinctUnresolvedTerms = mentions - resolved,
    )
}
