package com.jankowski.rafal.jobassistant.catalog

/**
 * A catalog entry a queued term might mean.
 *
 * **A candidate for a human, never an answer.** `resolve` does not consult these and never will:
 * resolution is a lookup, and a term that "looks like" Kubernetes is exactly the kind of guess
 * `unmatched_term` exists to keep out of the catalog. A suggestion becomes truth only when someone
 * clicks approve, at which point it goes through the same alias write as any other decision.
 */
data class SkillSuggestion(
    val skillId: Long,
    val skillName: String,
    val category: SkillCategory,
    /**
     * The catalog spelling that actually matched, which is often an alias rather than [skillName].
     *
     * Shown to the reviewer because it is the explanation: "Spring Boot" suggested for
     * `spring boot framework` is obvious, whereas the same suggestion arriving because of the alias
     * "springboot" is worth being able to see.
     */
    val matchedAlias: String,
    /** 0-1. Ordering only - it is not a probability and must not be read as confidence. */
    val score: Double,
)
