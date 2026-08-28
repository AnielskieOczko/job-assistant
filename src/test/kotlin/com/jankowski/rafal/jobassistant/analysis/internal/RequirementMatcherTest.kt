package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure logic: no Spring, no database, no model. If the gap report is ever wrong, it is either
 * this or the catalog - and both are covered without a network call.
 */
class RequirementMatcherTest {

    private val kotlin = 1L
    private val springBoot = 2L
    private val kubernetes = 3L
    private val quarkus = 4L

    private fun requirement(
        skillId: Long?,
        importance: Importance = Importance.MUST_HAVE,
        name: String = "skill-$skillId",
        rawText: String = "needs $name",
        category: SkillCategory? = SkillCategory.LANGUAGE,
    ) = ResolvedRequirement(rawText, skillId, skillId?.let { name }, importance, null, category)

    private val coverage = SkillCoverage(
        held = setOf(kotlin),
        impliedBy = mapOf(springBoot to kotlin),
        relatedBy = mapOf(quarkus to kotlin),
    )

    private fun match(vararg requirements: ResolvedRequirement) =
        RequirementMatcher.match(requirements.toList(), coverage) { id, status -> "evidence:$id:$status" }

    @Test
    fun `a held skill is MET and carries evidence`() {
        val result = match(requirement(kotlin)).single()

        assertEquals(RequirementStatus.MET, result.status)
        assertEquals("evidence:$kotlin:MET", result.evidence)
    }

    @Test
    fun `an implied skill is MET`() {
        assertEquals(RequirementStatus.MET, match(requirement(springBoot)).single().status)
    }

    @Test
    fun `an adjacent skill is PARTIAL and still carries evidence`() {
        val result = match(requirement(quarkus)).single()

        assertEquals(RequirementStatus.PARTIAL, result.status)
        assertEquals("evidence:$quarkus:PARTIAL", result.evidence)
    }

    @Test
    fun `an uncovered skill is MISSING with no evidence`() {
        val result = match(requirement(kubernetes)).single()

        assertEquals(RequirementStatus.MISSING, result.status)
        assertNull(result.evidence)
    }

    @Test
    fun `a requirement with no catalog match is UNRESOLVED, not MISSING`() {
        val result = match(requirement(skillId = null)).single()

        assertEquals(RequirementStatus.UNRESOLVED, result.status)
        assertNull(result.evidence)
    }

    @Test
    fun `requirement order is preserved`() {
        val result = match(requirement(kubernetes), requirement(kotlin), requirement(quarkus))

        assertEquals(listOf(kubernetes, kotlin, quarkus), result.map { it.skillId })
    }

    @Test
    fun `matching is reproducible across runs`() {
        val requirements = listOf(requirement(kotlin), requirement(kubernetes), requirement(quarkus))

        assertEquals(
            RequirementMatcher.match(requirements, coverage) { id, _ -> "e$id" },
            RequirementMatcher.match(requirements, coverage) { id, _ -> "e$id" },
        )
    }

    // ---- scoring ----

    @Test
    fun `score counts met fully and partial at half`() {
        val matched = match(
            requirement(kotlin),      // MET
            requirement(quarkus),     // PARTIAL
            requirement(kubernetes),  // MISSING
            requirement(springBoot),  // MET
        )

        // (2 met + 0.5 * 1 partial) / 4 = 0.625
        assertEquals(0.625, RequirementMatcher.score(matched))
    }

    @Test
    fun `nice-to-haves do not affect the score`() {
        val withoutExtras = match(requirement(kotlin))
        val withExtras = match(
            requirement(kotlin),
            requirement(kubernetes, importance = Importance.NICE_TO_HAVE),
            requirement(quarkus, importance = Importance.NICE_TO_HAVE),
        )

        assertEquals(RequirementMatcher.score(withoutExtras), RequirementMatcher.score(withExtras))
    }

    @Test
    fun `unresolved requirements are excluded rather than counted as missing`() {
        val matched = match(requirement(kotlin), requirement(skillId = null))

        // The unresolved one says nothing about the candidate, so the score stays 1.0.
        assertEquals(1.0, RequirementMatcher.score(matched))
    }

    @Test
    fun `nothing scoreable yields null rather than a misleading zero`() {
        assertNull(RequirementMatcher.score(emptyList()))
        assertNull(RequirementMatcher.score(match(requirement(kotlin, importance = Importance.NICE_TO_HAVE))))
        assertNull(RequirementMatcher.score(match(requirement(skillId = null))))
    }

    @Test
    fun `all missing scores zero`() {
        assertEquals(0.0, RequirementMatcher.score(match(requirement(kubernetes))))
    }

    // ---- languages ----

    private fun languages(vararg required: Pair<String, LanguageLevel>, held: Map<String, LanguageLevel>) =
        RequirementMatcher.matchLanguages(required.toList()) { held[it] }

    @Test
    fun `an exceeded language requirement is MET`() {
        val result = languages("English" to LanguageLevel.B2, held = mapOf("English" to LanguageLevel.C1))

        assertEquals(RequirementStatus.MET, result.single().status)
        assertEquals(LanguageLevel.C1, result.single().heldLevel)
    }

    @Test
    fun `an exactly matched language requirement is MET`() {
        val result = languages("English" to LanguageLevel.B2, held = mapOf("English" to LanguageLevel.B2))

        assertEquals(RequirementStatus.MET, result.single().status)
    }

    @Test
    fun `one CEFR level short is PARTIAL, not MISSING`() {
        val result = languages("German" to LanguageLevel.B2, held = mapOf("German" to LanguageLevel.B1))

        assertEquals(RequirementStatus.PARTIAL, result.single().status)
    }

    @Test
    fun `two levels short is MISSING`() {
        val result = languages("German" to LanguageLevel.C1, held = mapOf("German" to LanguageLevel.B1))

        assertEquals(RequirementStatus.MISSING, result.single().status)
    }

    @Test
    fun `a language not spoken at all is MISSING with no held level`() {
        val result = languages("French" to LanguageLevel.B1, held = emptyMap())

        assertEquals(RequirementStatus.MISSING, result.single().status)
        assertNull(result.single().heldLevel)
    }

    @Test
    fun `native satisfies any requirement`() {
        val result = languages("Polish" to LanguageLevel.C2, held = mapOf("Polish" to LanguageLevel.NATIVE))

        assertEquals(RequirementStatus.MET, result.single().status)
    }

    // --------------------------------------------------- soft skills

    private val communication = 5L

    /**
     * The point of the change: a soft must-have the profile does not hold must not drag down a
     * number that claims to say how *technically* qualified someone is. No catalog lookup can tell
     * you whether a person communicates well.
     */
    @Test
    fun `a missing soft must-have does not move the score`() {
        val technicalOnly = match(requirement(kotlin))
        val withSoftGap = match(
            requirement(kotlin),
            requirement(communication, name = "Communication", category = SkillCategory.SOFT),
        )

        assertEquals(RequirementMatcher.score(technicalOnly), RequirementMatcher.score(withSoftGap))
        assertEquals(1.0, RequirementMatcher.score(withSoftGap))
    }

    /** Excluded from the score, but never hidden: the offer really did ask for it. */
    @Test
    fun `a soft requirement is still reported with its real status`() {
        val result = match(
            requirement(communication, name = "Communication", category = SkillCategory.SOFT),
        ).single()

        assertEquals(RequirementStatus.MISSING, result.status)
        assertEquals("Communication", result.skillName)
        assertEquals(SkillCategory.SOFT, result.category)
    }

    /**
     * An offer asking only for soft skills scores null rather than 0.0. Zero would read as "you
     * match nothing", which is a claim about the candidate the data cannot support.
     */
    @Test
    fun `an all-soft offer has no score rather than a zero`() {
        val result = match(
            requirement(communication, name = "Communication", category = SkillCategory.SOFT),
        )

        assertNull(RequirementMatcher.score(result))
    }

    /** The explanation and the score must be computed over the same list, or they disagree. */
    @Test
    fun `scoreable is the denominator the score actually uses`() {
        val matched = match(
            requirement(kotlin),
            requirement(kubernetes),
            requirement(communication, name = "Communication", category = SkillCategory.SOFT),
            requirement(null, name = "Unplaceable"),
            requirement(springBoot, importance = Importance.NICE_TO_HAVE),
        )

        val scoreable = RequirementMatcher.scoreable(matched)

        assertEquals(listOf("skill-1", "skill-3"), scoreable.map { it.skillName })
        assertEquals(0.5, RequirementMatcher.score(matched))
    }
}
