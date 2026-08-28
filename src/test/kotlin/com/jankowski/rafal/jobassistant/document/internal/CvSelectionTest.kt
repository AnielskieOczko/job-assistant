package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTerm
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ExperienceBullet
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileSkill
import com.jankowski.rafal.jobassistant.profile.WorkExperience
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure logic, so it runs in the fast tier with no container.
 *
 * These cases exist because `TailoredCv` arrives from LangChain4j, which builds it reflectively
 * without the Jackson Kotlin module and so never enforces Kotlin's non-nullability. A property
 * declared `List<T> = emptyList()` is genuinely null when the model emits `"bullets": null` — the
 * default value only covers a *missing* key. Kotlin's types are not a guarantee at that boundary.
 */
class CvSelectionTest {

    private val kotlinSkill = CanonicalSkill(1, "Kotlin", SkillCategory.LANGUAGE)
    private val springBoot = CanonicalSkill(2, "Spring Boot", SkillCategory.FRAMEWORK)

    private val catalog = object : SkillCatalog {
        private val all = listOf(kotlinSkill, springBoot)
        override fun findById(id: Long) = all.firstOrNull { it.id == id }
        override fun findAllById(ids: Collection<Long>) = all.filter { it.id in ids }
        override fun findAll() = all
        override fun resolve(term: String) = all.firstOrNull { it.name.equals(term, ignoreCase = true) }
        override fun resolveAll(terms: Collection<String>) = terms.associateWith { resolve(it) }
        override fun coverageFor(heldSkillIds: Set<Long>): SkillCoverage = unused()
        override fun recordUnmatched(term: String) = unused()
        override fun recordUnmatchedFromMarket(mentions: Map<String, Int>) = unused()
        override fun pendingUnmatchedTerms(limit: Int): List<UnmatchedTerm> = unused()
        override fun approveUnmatchedTerm(termId: Long, skillId: Long): CanonicalSkill = unused()
        override fun rejectUnmatchedTerm(termId: Long) = unused()
        override fun createSkill(name: String, category: SkillCategory, aliases: Collection<String>): CanonicalSkill = unused()
        override fun updateSkill(id: Long, name: String, category: SkillCategory): CanonicalSkill = unused()
        override fun deleteSkill(id: Long) = unused()
        private fun unused(): Nothing = error("not used by CvSelection")
    }

    private val firstBullet = ExperienceBullet(10, "Shipped the payments service", setOf(kotlinSkill.id))
    private val secondBullet = ExperienceBullet(11, "Cut p99 latency by half", setOf(springBoot.id))

    private val profile = CandidateProfile(
        details = ProfileDetails(fullName = "A Candidate"),
        links = emptyList(),
        skills = listOf(
            ProfileSkill(1, kotlinSkill.id, Proficiency.EXPERT),
            ProfileSkill(2, springBoot.id, Proficiency.PROFICIENT),
        ),
        experiences = listOf(
            WorkExperience(
                id = 1,
                company = "Some Company",
                roleTitle = "Engineer",
                startedOn = LocalDate.of(2020, 1, 1),
                bullets = listOf(firstBullet, secondBullet),
            )
        ),
        education = emptyList(),
        languages = emptyList(),
    )

    /**
     * Reproduces what LangChain4j hands back for `{"summaryLine": null, "bullets": null}`.
     *
     * It has to go through the backing field rather than the constructor, and that detail is the
     * whole point: Kotlin emits an intrinsic null check in the constructor, so a null cannot be
     * passed there at all. LangChain4j's JSON codec never calls it — it allocates the instance and
     * writes fields reflectively, which is exactly how a value the type forbids ends up inside one.
     */
    private fun TailoredCv.withNulled(vararg fields: String): TailoredCv = apply {
        fields.forEach { name ->
            javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(this, null)
        }
    }

    @Test
    fun `a wholly null tailoring falls back to the untailored profile instead of throwing`() {
        val tailored = TailoredCv().withNulled("summaryLine", "skillNames", "bullets")

        val selection = CvSelection.from(tailored, profile, catalog)

        // An empty selection must render the whole profile: a CV with no experience at all is
        // worse than an untailored one.
        assertEquals(listOf(10L, 11L), selection.bulletOrder)
        assertEquals(listOf("Kotlin", "Spring Boot"), selection.skillNames)
        assertNull(selection.summaryLine)
        assertEquals(emptyList(), selection.droppedBulletIds)
        assertEquals(emptyList(), selection.droppedSkillNames)
    }

    @Test
    fun `a null bullet list alone still keeps the skills the model chose`() {
        val tailored = TailoredCv(
            summaryLine = "Backend engineer",
            skillNames = listOf("Kotlin"),
        ).withNulled("bullets")

        val selection = CvSelection.from(tailored, profile, catalog)

        assertEquals(listOf(10L, 11L), selection.bulletOrder)
        assertEquals(listOf("Kotlin"), selection.skillNames)
        assertEquals("Backend engineer", selection.summaryLine)
    }

    @Test
    fun `a well-formed tailoring is unaffected by the null handling`() {
        val tailored = TailoredCv(
            summaryLine = "  Backend engineer  ",
            skillNames = listOf("Kotlin", "Terraform"),
            bullets = listOf(
                TailoredBullet(11, "Halved p99 latency"),
                TailoredBullet(99, "Ran the Kubernetes migration"),
            ),
        )

        val selection = CvSelection.from(tailored, profile, catalog)

        assertEquals(listOf(11L), selection.bulletOrder)
        assertEquals("Backend engineer", selection.summaryLine)
        assertEquals(listOf("Kotlin"), selection.skillNames)
        assertEquals(mapOf(11L to "Halved p99 latency"), selection.rewrittenText)
        // The bullet the profile cannot back and the skill it does not hold are both counted.
        assertEquals(listOf(99L), selection.droppedBulletIds)
        assertEquals(listOf("Terraform"), selection.droppedSkillNames)
    }
}
