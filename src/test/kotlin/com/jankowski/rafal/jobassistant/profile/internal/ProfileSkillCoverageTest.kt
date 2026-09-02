package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.catalog.SkillSuggestion
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTerm
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileIdentity
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfilePortrait
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProfileSkill
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The part of the coverage seam that has a rule in it: what "there is no profile" means.
 *
 * The delegation half is deliberately not asserted here - checking that a profile's held ids reach
 * the catalog is asserting implementation, and `AnalysisFlowIntegrationTest` and
 * `MarketInsightsIntegrationTest` already prove it end to end. What is worth pinning is the answer
 * on a fresh install, which used to be a private detail of the market dashboard and is the reason
 * this type exists.
 */
class ProfileSkillCoverageTest {

    private val coverageOfHeld = SkillCoverage(held = setOf(1L), impliedBy = mapOf(2L to 1L))

    private val catalog = object : FakeSkillCatalog() {
        override fun coverageFor(heldSkillIds: Set<Long>) =
            if (heldSkillIds.isEmpty()) SkillCoverage.EMPTY else coverageOfHeld
    }

    private fun profiles(
        defaultId: Long? = 7L,
        stored: Map<Long, CandidateProfile> = emptyMap(),
    ) = object : FakeProfileService() {
        override fun defaultProfileId(): Long = defaultId ?: error("no profile exists yet")
        override fun current(profileId: Long): CandidateProfile? = stored[profileId]
    }

    private fun profile(vararg skillIds: Long) = CandidateProfile(
        details = ProfileDetails(fullName = "A Candidate"),
        links = emptyList(),
        skills = skillIds.mapIndexed { index, id ->
            ProfileSkill(id = index + 1L, skillId = id, proficiency = Proficiency.WORKING)
        },
        experiences = emptyList(),
        education = emptyList(),
        credentials = emptyList(),
        projects = emptyList(),
        consentClauses = emptyList(),
        languages = emptyList(),
    )

    @Test
    fun `a null id resolves through the default profile`() {
        val coverage = ProfileSkillCoverage(profiles(stored = mapOf(7L to profile(1L))), catalog)

        assertEquals(coverageOfHeld, coverage.of(null as Long?))
    }

    @Test
    fun `no persona at all yields empty coverage rather than an error`() {
        val coverage = ProfileSkillCoverage(profiles(defaultId = null), catalog)

        assertEquals(SkillCoverage.EMPTY, coverage.of(null as Long?))
    }

    @Test
    fun `a profile with no details yet yields empty coverage`() {
        val coverage = ProfileSkillCoverage(profiles(stored = emptyMap()), catalog)

        assertEquals(SkillCoverage.EMPTY, coverage.of(7L))
    }

    @Test
    fun `an unknown explicit id is never quietly answered by the default profile`() {
        val coverage = ProfileSkillCoverage(
            profiles(defaultId = 7L, stored = mapOf(7L to profile(1L))),
            catalog,
        )

        assertEquals(SkillCoverage.EMPTY, coverage.of(8L))
    }

    @Test
    fun `a profile already in hand is expanded without being read again`() {
        val coverage = ProfileSkillCoverage(
            object : FakeProfileService() {
                override fun current(profileId: Long): CandidateProfile =
                    error("the caller already holds the profile")
            },
            catalog,
        )

        assertEquals(coverageOfHeld, coverage.of(profile(1L)))
    }
}

private abstract class FakeProfileService : ProfileService {
    override fun current(profileId: Long): CandidateProfile? = unused()
    override fun require(profileId: Long): CandidateProfile = unused()
    override fun replace(profileId: Long, import: ProfileImport): CandidateProfile = unused()
    override fun portrait(profileId: Long): ProfilePortrait? = unused()
    override fun revision(profileId: Long): Long = unused()
    override fun defaultProfileId(): Long = unused()
    override fun identities(): List<ProfileIdentity> = unused()
    private fun unused(): Nothing = error("not used by ProfileSkillCoverage")
}

private abstract class FakeSkillCatalog : SkillCatalog {
    override fun findById(id: Long): CanonicalSkill? = unused()
    override fun findAllById(ids: Collection<Long>): List<CanonicalSkill> = unused()
    override fun findAll(): List<CanonicalSkill> = unused()
    override fun resolve(term: String): CanonicalSkill? = unused()
    override fun resolveAll(terms: Collection<String>): Map<String, CanonicalSkill?> = unused()
    override fun suggest(term: String, limit: Int): List<SkillSuggestion> = unused()
    override fun suggestAll(terms: Collection<String>, limit: Int): Map<String, List<SkillSuggestion>> = unused()
    override fun coverageFor(heldSkillIds: Set<Long>): SkillCoverage = unused()
    override fun recordUnmatched(term: String) = unused()
    override fun recordUnmatchedFromMarket(mentions: Map<String, Int>) = unused()
    override fun pendingUnmatchedTerms(limit: Int): List<UnmatchedTerm> = unused()
    override fun allPendingUnmatchedTerms(): List<UnmatchedTerm> = unused()
    override fun approveUnmatchedTerm(termId: Long, skillId: Long): CanonicalSkill = unused()
    override fun rejectUnmatchedTerm(termId: Long) = unused()
    override fun createSkill(name: String, category: SkillCategory, aliases: Collection<String>): CanonicalSkill =
        unused()

    override fun updateSkill(id: Long, name: String, category: SkillCategory): CanonicalSkill = unused()
    override fun deleteSkill(id: Long) = unused()
    private fun unused(): Nothing = error("not used by ProfileSkillCoverage")
}
