package com.jankowski.rafal.jobassistant.market

import java.time.Instant

/**
 * The market module's entire public surface.
 *
 * Ingestion only, deliberately. The dashboard this corpus exists for reads it separately and
 * computes profile coverage on the fly; nothing here stores a comparison against a profile, because
 * a stored one goes stale the moment the profile is edited and the computation is free.
 */
interface MarketOfferService {

    /**
     * Pulls every configured source once and upserts what it finds. Safe to call repeatedly: an
     * offer already in the corpus is refreshed rather than duplicated.
     */
    fun ingest(): IngestionReport

    /** What the corpus currently holds, per source. Bounds the window any statistic may claim. */
    fun corpusSummary(): List<CorpusSummary>
}

/**
 * Whether anything polls this corpus without being asked, and when it next will.
 *
 * Read by the dashboard so that "how did this data get here" is answered on the page rather than in
 * `application.yaml`. The distinction matters more than it looks: a corpus that refreshes itself
 * every night and a corpus that only ever changes when someone presses a button produce identical
 * numbers, but one of them is going stale while nobody is watching. A reader cannot tell which they
 * are looking at without being told.
 *
 * [nextPollAt] is computed from [cron] rather than stored, so it cannot drift from what the
 * scheduler will actually do, and it is null exactly when [scheduled] is false -- a next-run time
 * for a poll that will never run is worse than no time at all.
 */
data class IngestionSchedule(
    /** Whether the scheduled poll is switched on. A manual ingest works either way. */
    val scheduled: Boolean,
    /** The cron the scheduler runs, verbatim. Ground truth, next to the interpreted [nextPollAt]. */
    val cron: String?,
    val nextPollAt: Instant?,
    /** When the corpus last saw anything, scheduled or manual. Null on an empty corpus. */
    val lastPolledAt: Instant?,
)

/**
 * What one ingestion run did.
 *
 * Counts, not just rates. A resolution *rate* with a denominator of three says nothing, and the
 * project has been bitten by exactly that before -- so [skillResolutionRate] is null below a
 * minimum sample and the raw counts are always present alongside it.
 */
data class IngestionReport(
    val source: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    val pagesFetched: Int,
    val offersSeen: Int,
    val offersInserted: Int,
    val offersUpdated: Int,
    val skillMentions: Int,
    val skillsResolved: Int,
    val distinctUnresolvedTerms: Int,
    val error: String? = null,
) {
    val skillsUnresolved: Int get() = skillMentions - skillsResolved

    /** Null rather than a misleading 1.0 when too few mentions were seen to mean anything. */
    val skillResolutionRate: Double?
        get() = if (skillMentions < MIN_MENTIONS_FOR_A_RATE) null
        else skillsResolved.toDouble() / skillMentions

    companion object {
        /** Below this, a ratio is noise: 1 of 1 resolved is indistinguishable from 90 of 90. */
        const val MIN_MENTIONS_FOR_A_RATE = 20
    }
}

/**
 * The corpus as it stands for one source.
 *
 * [firstSeenAt] and [lastSeenAt] are the window every statistic drawn from this corpus has to
 * declare, in the same way a rate has to declare its denominator.
 */
data class CorpusSummary(
    val source: String,
    val offers: Int,
    val currentlyValid: Int,
    val firstSeenAt: Instant?,
    val lastSeenAt: Instant?,
)
