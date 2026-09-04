package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.offer.Application
import com.jankowski.rafal.jobassistant.offer.JobOffer
import java.time.Instant

/**
 * The score an offer currently carries, taken from its latest completed analysis.
 *
 * Only ever constructed when there is a number to report. A DONE analysis whose `matchScore` is
 * null - no resolvable technical must-have to score against - leaves [ShortlistEntry.score] null
 * rather than producing an entry claiming a score of zero, which is the same rule
 * `RequirementMatcher` already applies one level down.
 *
 * [scoringRule] travels with the number because it changes what the number means. Historical rows
 * are never recomputed, so a shortlist is the one surface where a V1 and a V2 score sit next to each
 * other, and comparing them as though they measured the same thing is precisely the mistake the
 * versioning exists to prevent.
 */
data class OfferScore(
    val analysisId: Long,
    val matchScore: Double,
    val scoringRule: ScoringRule,
    /** When the analysis that produced it finished, so a stale score can be seen to be stale. */
    val completedAt: Instant?,
)

/** One offer on the shortlist: what the offer list already showed, plus what it never did. */
data class ShortlistEntry(
    val offer: JobOffer,
    val application: Application,
    /** Null when this offer has never been analysed against this profile, or scored nothing. */
    val score: OfferScore?,
)

/**
 * Every saved offer, ranked by how well the candidate matches it.
 *
 * The cross-offer question - *which of these should I apply to first* - as opposed to
 * [AggregateGapReport], which answers *what should I learn*. Both are views over the same latest
 * analysis per offer.
 *
 * [entries] arrive in rank order and that order is **total**: score descending, unscored last, then
 * offer id descending. Sorting on the score alone would let two equally matched offers swap between
 * requests, which is the trap `CoverageStatus.UNMET_FIRST` documents for the market dashboard.
 *
 * [scored] and [total] are both returned for the reason `priced_calls` and the triage queue's
 * `matching`/`pending` are: a ranked list of ten offers built from three analyses is a ranking of
 * three, and a caller given only the rows cannot say so.
 */
data class OfferShortlist(
    val entries: List<ShortlistEntry>,
    /** Offers carrying a score. The numerator. */
    val scored: Int,
    /** Offers in the list at all. The denominator - unanalysed offers are listed, not hidden. */
    val total: Int,
    /**
     * The profile the scores were measured against, echoed so a caller never has to assume which
     * one the fallback picked. Null on an install with no persona yet, where every entry is
     * unscored - true rather than an error, the rule `ProfileCoverage` states for the same case.
     */
    val profileId: Long?,
)
