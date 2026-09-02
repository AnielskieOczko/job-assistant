package com.jankowski.rafal.jobassistant.catalog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The gap report's determinism lives here: statusFor is a pure lookup, so the same inputs always
 * produce the same status.
 */
class SkillCoverageTest {

    private val coverage = SkillCoverage(
        held = setOf(1L),
        impliedBy = mapOf(2L to 1L),
        relatedBy = mapOf(3L to 1L),
    )

    @Test
    fun `directly held skill is MET`() {
        assertEquals(CoverageStatus.MET, coverage.statusFor(1L))
    }

    @Test
    fun `skill reached through IMPLIES is MET`() {
        assertEquals(CoverageStatus.MET, coverage.statusFor(2L))
    }

    @Test
    fun `skill reached only through RELATED is PARTIAL`() {
        assertEquals(CoverageStatus.PARTIAL, coverage.statusFor(3L))
    }

    @Test
    fun `unreachable skill is MISSING`() {
        assertEquals(CoverageStatus.MISSING, coverage.statusFor(99L))
    }

    @Test
    fun `empty coverage reports everything MISSING`() {
        assertEquals(CoverageStatus.MISSING, SkillCoverage.EMPTY.statusFor(1L))
    }

    @Test
    fun `a held skill accounts for itself`() {
        assertEquals(1L, coverage.coveringSkillFor(1L))
    }

    @Test
    fun `provenance names the held skill behind an implied or related verdict`() {
        assertEquals(1L, coverage.coveringSkillFor(2L))
        assertEquals(1L, coverage.coveringSkillFor(3L))
    }

    @Test
    fun `a missing skill has no covering skill to name`() {
        assertNull(coverage.coveringSkillFor(99L))
    }

    /**
     * Stated against a shuffled list rather than [CoverageStatus.entries] in order, so that
     * reordering the enum's declaration cannot make this pass vacuously. That is the failure the
     * ordering exists to prevent: the declaration order happens to agree with the ranking today,
     * and a reader tidying the enum would otherwise invert the market dashboard silently.
     */
    @Test
    fun `the unmet ordering leads with MISSING and ends with MET`() {
        val shuffled = listOf(CoverageStatus.MET, CoverageStatus.MISSING, CoverageStatus.PARTIAL)

        assertEquals(
            listOf(CoverageStatus.MISSING, CoverageStatus.PARTIAL, CoverageStatus.MET),
            shuffled.sortedWith(CoverageStatus.UNMET_FIRST),
        )
    }

    @Test
    fun `every status has a distinct rank, so the ordering is total`() {
        assertEquals(
            CoverageStatus.entries.size,
            CoverageStatus.entries.map { it.unmetRank }.distinct().size,
        )
    }

    @Test
    fun `derived sets stay in step with the provenance maps`() {
        assertEquals(setOf(2L), coverage.impliedCovered)
        assertEquals(setOf(3L), coverage.relatedCovered)
    }
}
