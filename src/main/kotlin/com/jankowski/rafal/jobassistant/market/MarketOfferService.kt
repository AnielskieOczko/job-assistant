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
