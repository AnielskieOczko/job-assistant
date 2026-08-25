package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.BulletImport
import com.jankowski.rafal.jobassistant.profile.ExperienceImport
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.SkillImport
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class ProfileCrudIntegrationTest(
    @Autowired private val profiles: ProfileService,
    @Autowired private val writes: ProfileWriteService,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun clearProfile() {
        listOf("work_experience", "profile_skill", "profile_link", "education", "language_skill")
            .forEach { jdbc.sql("delete from $it").update() }
        jdbc.sql("delete from profile_details").update()
    }

    private fun skillId(name: String) = catalog.resolve(name)!!.id

    private fun seed() = profiles.replace(
        ProfileImport(
            details = ProfileDetails(fullName = "Rafal Jankowski", headline = "Backend Engineer"),
            skills = listOf(
                SkillImport("Kotlin", Proficiency.EXPERT),
                SkillImport("PostgreSQL", Proficiency.WORKING),
            ),
            experiences = listOf(
                ExperienceImport(
                    company = "Acme",
                    roleTitle = "Senior Backend Engineer",
                    startedOn = LocalDate.of(2021, 1, 1),
                    bullets = listOf(
                        BulletImport("Built payment services in Kotlin.", listOf("Kotlin")),
                        BulletImport("Ran the Postgres migration.", listOf("PostgreSQL")),
                    ),
                ),
            ),
        )
    )

    // ------------------------------------------------------------ bullet ids

    /**
     * The reason bullets are their own aggregate root. While they hung off the experience, Spring
     * Data JDBC deleted and reinserted the whole collection on every save of the parent, so
     * correcting a company name renumbered them - and a CV generated earlier, which cites bullets
     * by id, would silently stop matching the profile it was built from.
     */
    @Test
    fun `editing an experience leaves its bullet ids alone`() {
        val before = seed().experiences.single()
        val bulletIds = before.bullets.map { it.id }

        val after = writes.updateExperience(
            before.id,
            ExperienceRequest(
                company = "Acme Corporation",
                roleTitle = before.roleTitle,
                startedOn = before.startedOn,
            ),
        ).experiences.single()

        assertEquals("Acme Corporation", after.company)
        assertEquals(bulletIds, after.bullets.map { it.id })
    }

    @Test
    fun `editing one bullet leaves its siblings' ids alone`() {
        val experience = seed().experiences.single()
        val (first, second) = experience.bullets

        writes.updateBullet(first.id, BulletRequest("Rewritten.", setOf(skillId("Kotlin"))))

        val after = profiles.require().experiences.single()
        assertEquals(listOf(first.id, second.id), after.bullets.map { it.id })
        assertEquals("Rewritten.", after.bullets.first().text)
        assertEquals("Ran the Postgres migration.", after.bullets.last().text)
    }

    @Test
    fun `deleting a bullet leaves the others alone`() {
        val experience = seed().experiences.single()
        val (first, second) = experience.bullets

        val after = writes.deleteBullet(first.id).experiences.single()

        assertEquals(listOf(second.id), after.bullets.map { it.id })
    }

    // ------------------------------------------------------------- invariants

    @Test
    fun `a skill still cited by a bullet cannot be deleted`() {
        val profile = seed()
        val kotlin = profile.skills.single { it.skillId == skillId("Kotlin") }

        val rejected = assertThrows<ProfileConflictException> { writes.deleteSkill(kotlin.id) }

        assertContains(rejected.message!!, "Kotlin")
        assertEquals(
            listOf("Built payment services in Kotlin."),
            rejected.blockingBullets.map { it.text },
        )
        // The refusal must not have half-applied.
        assertTrue(profiles.require().heldSkillIds.contains(skillId("Kotlin")))
    }

    @Test
    fun `a skill no bullet cites can be deleted`() {
        val profile = seed()
        writes.addSkill(SkillRequest(skillId = skillId("Docker"), proficiency = Proficiency.WORKING))
        val docker = profiles.require().skills.single { it.skillId == skillId("Docker") }

        val after = writes.deleteSkill(docker.id)

        assertNull(after.skills.firstOrNull { it.skillId == skillId("Docker") })
        assertEquals(profile.skills.size, after.skills.size)
    }

    @Test
    fun `a bullet cannot cite a skill the profile does not declare`() {
        val experience = seed().experiences.single()

        val rejected = assertThrows<ProfileConflictException> {
            writes.addBullet(experience.id, BulletRequest("Deployed with Kubernetes.", setOf(skillId("Kubernetes"))))
        }

        assertContains(rejected.message!!, "Kubernetes")
        assertEquals(2, profiles.require().experiences.single().bullets.size)
    }

    @Test
    fun `holding a skill twice is refused`() {
        seed()

        val rejected = assertThrows<ProfileConflictException> {
            writes.addSkill(SkillRequest(skillId = skillId("Kotlin"), proficiency = Proficiency.WORKING))
        }

        assertContains(rejected.message!!, "Kotlin")
    }

    /**
     * `language_skill` is unique on `lower(language)` because CandidateProfile.languageLevel()
     * matches case-insensitively - otherwise 'English' and 'english' could both exist and which one
     * the analysis pipeline compared against would be arbitrary.
     */
    @Test
    fun `a language already listed under different casing is refused`() {
        seed()
        writes.addLanguage(LanguageRequest("English", LanguageLevel.C1))

        val rejected = assertThrows<ProfileConflictException> {
            writes.addLanguage(LanguageRequest("english", LanguageLevel.B2))
        }

        assertContains(rejected.message!!, "English")
        assertEquals(1, profiles.require().languages.size)
    }

    @Test
    fun `a role cannot end before it starts`() {
        seed()

        assertThrows<ProfileConflictException> {
            writes.addExperience(
                ExperienceRequest(
                    company = "Initech",
                    roleTitle = "Engineer",
                    startedOn = LocalDate.of(2020, 1, 1),
                    endedOn = LocalDate.of(2019, 1, 1),
                )
            )
        }
    }

    @Test
    fun `an unknown id is reported as unknown rather than as a conflict`() {
        seed()
        assertThrows<UnknownProfileEntityException> { writes.deleteSkill(99_999) }
        assertThrows<UnknownProfileEntityException> { writes.updateBullet(99_999, BulletRequest("x")) }
        assertThrows<UnknownProfileEntityException> {
            writes.addBullet(99_999, BulletRequest("x"))
        }
    }

    // -------------------------------------------------------------- bootstrap

    @Test
    fun `a profile can be created without importing a document`() {
        assertNull(profiles.current())

        val created = writes.putDetails(DetailsRequest(fullName = "Rafal Jankowski", headline = "Consultant"))

        assertEquals("Rafal Jankowski", created.details.fullName)
        assertEquals("Consultant", created.details.headline)
        assertTrue(created.skills.isEmpty())
        assertNotNull(profiles.current())
    }

    @Test
    fun `entities added to a fresh profile land in insertion order`() {
        writes.putDetails(DetailsRequest(fullName = "Rafal Jankowski"))
        writes.addSkill(SkillRequest(skillId = skillId("Kotlin"), proficiency = Proficiency.EXPERT))
        writes.addSkill(SkillRequest(skillId = skillId("Docker"), proficiency = Proficiency.WORKING))
        val profile = writes.addSkill(SkillRequest(skillId = skillId("PostgreSQL"), proficiency = Proficiency.WORKING))

        assertEquals(
            listOf(skillId("Kotlin"), skillId("Docker"), skillId("PostgreSQL")),
            profile.skills.map { it.skillId },
        )
    }

    // ---------------------------------------------------------------- ordering

    @Test
    fun `reordering skills round-trips`() {
        seed()
        val ids = profiles.require().skills.map { it.id }

        val reordered = writes.reorderSkills(ids.reversed())

        assertEquals(ids.reversed(), reordered.skills.map { it.id })
        assertEquals(ids.reversed(), profiles.require().skills.map { it.id })
    }

    @Test
    fun `reordering bullets round-trips within their role`() {
        val experience = seed().experiences.single()
        val ids = experience.bullets.map { it.id }

        val reordered = writes.reorderBullets(experience.id, ids.reversed())

        assertEquals(ids.reversed(), reordered.experiences.single().bullets.map { it.id })
    }

    /** A partial list would silently leave the omitted rows wherever they happened to be. */
    @Test
    fun `a reorder naming only some of the collection is refused`() {
        seed()
        val ids = profiles.require().skills.map { it.id }

        assertThrows<ProfileConflictException> { writes.reorderSkills(listOf(ids.first())) }
        assertThrows<ProfileConflictException> { writes.reorderSkills(ids + ids.first()) }
        assertEquals(ids, profiles.require().skills.map { it.id })
    }

    // ---------------------------------------------------------------- revision

    @Test
    fun `every write path bumps the revision`() {
        val start = profiles.revision()

        writes.putDetails(DetailsRequest(fullName = "Rafal Jankowski"))
        assertEquals(start + 1, profiles.revision())

        writes.addSkill(SkillRequest(skillId = skillId("Kotlin"), proficiency = Proficiency.EXPERT))
        assertEquals(start + 2, profiles.revision())

        val experience = writes.addExperience(
            ExperienceRequest(company = "Acme", roleTitle = "Engineer", startedOn = LocalDate.of(2021, 1, 1))
        ).experiences.single()
        assertEquals(start + 3, profiles.revision())

        val bullet = writes.addBullet(experience.id, BulletRequest("Shipped things.", setOf(skillId("Kotlin"))))
            .experiences.single().bullets.single()
        assertEquals(start + 4, profiles.revision())

        writes.updateBullet(bullet.id, BulletRequest("Shipped better things.", setOf(skillId("Kotlin"))))
        assertEquals(start + 5, profiles.revision())

        writes.addLanguage(LanguageRequest("Polish", LanguageLevel.NATIVE))
        assertEquals(start + 6, profiles.revision())

        writes.reorderSkills(profiles.require().skills.map { it.id })
        assertEquals(start + 7, profiles.revision())

        writes.deleteBullet(bullet.id)
        assertEquals(start + 8, profiles.revision())
    }

    /**
     * Import is a full replace, so it strands every bullet id a stored CV cites. It has to bump the
     * revision for the same reason a single edit does - arguably more so.
     */
    @Test
    fun `importing a document bumps the revision too`() {
        val before = profiles.revision()

        val imported = seed()

        assertEquals(before + 1, imported.revision)
        assertEquals(before + 1, profiles.revision())
    }

    @Test
    fun `the revision is exposed on the profile itself`() {
        val created = writes.putDetails(DetailsRequest(fullName = "Rafal Jankowski"))
        assertEquals(profiles.revision(), created.revision)
    }
}
