package com.jankowski.rafal.jobassistant.catalog

import java.time.Instant

/** A requirement phrase no catalog entry matched. Reviewing these is how the catalog grows. */
data class UnmatchedTerm(
    val id: Long,
    val term: String,
    /** Times this phrasing appeared in an offer the candidate actually read. Ranks the queue. */
    val occurrences: Int,
    /**
     * Times it appeared in the ingested market corpus. Counted apart from [occurrences] because one
     * poll sees hundreds of distinct terms, and a single counter would rank the queue by the market
     * rather than by what the candidate looked at. Shown as context: "seen once in your offers,
     * asked for 47 times by the market" is a better prompt for a decision than either number alone.
     */
    val marketOccurrences: Int,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val status: UnmatchedTermStatus,
    val resolvedSkillId: Long?,
)

enum class UnmatchedTermStatus { PENDING, APPROVED, REJECTED }
