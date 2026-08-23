package com.jankowski.rafal.jobassistant.catalog

import java.time.Instant

/** A requirement phrase no catalog entry matched. Reviewing these is how the catalog grows. */
data class UnmatchedTerm(
    val id: Long,
    val term: String,
    val occurrences: Int,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val status: UnmatchedTermStatus,
    val resolvedSkillId: Long?,
)

enum class UnmatchedTermStatus { PENDING, APPROVED, REJECTED }
