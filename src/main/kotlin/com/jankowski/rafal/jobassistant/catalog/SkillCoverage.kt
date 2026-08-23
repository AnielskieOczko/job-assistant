package com.jankowski.rafal.jobassistant.catalog

/**
 * What a given set of held skills covers, expanded once through the relation graph.
 *
 * This is the whole reason the gap report is reproducible: the analysis module asks this object
 * for a status instead of asking a model, so the same offer scores identically on every run.
 *
 * Coverage carries provenance, not just a verdict — knowing *which* held skill accounts for a
 * PARTIAL is what lets the report say "you have Quarkus, they want Spring Boot" instead of an
 * unexplained amber light.
 */
data class SkillCoverage(
    val held: Set<Long>,
    /** Covered skill id -> the held skill that implies it. */
    val impliedBy: Map<Long, Long> = emptyMap(),
    /** Adjacent skill id -> the held skill related to it. */
    val relatedBy: Map<Long, Long> = emptyMap(),
) {
    val impliedCovered: Set<Long> get() = impliedBy.keys
    val relatedCovered: Set<Long> get() = relatedBy.keys

    fun statusFor(skillId: Long): CoverageStatus = when {
        skillId in held || skillId in impliedBy -> CoverageStatus.MET
        skillId in relatedBy -> CoverageStatus.PARTIAL
        else -> CoverageStatus.MISSING
    }

    /**
     * The held skill accounting for [skillId]'s status, or null when it is MISSING. Returns
     * [skillId] itself when it is held directly.
     */
    fun coveringSkillFor(skillId: Long): Long? = when {
        skillId in held -> skillId
        else -> impliedBy[skillId] ?: relatedBy[skillId]
    }

    companion object {
        val EMPTY = SkillCoverage(emptySet())
    }
}
