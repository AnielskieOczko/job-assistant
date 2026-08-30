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
    @Autowired private val management: ProfileManagementService,
    @Autowired private val writes: ProfileWriteService,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val jdbc: JdbcClient,
) {

    private var profileId: Long = 0

    @BeforeEach
    fun clearProfile() {
        jdbc.sql("delete from profile").update()
        profileId = management.create("Test").id
    }

    private fun skillId(name: String) = catalog.resolve(name)!!.id

    private fun seed() = profiles.replace(
        profileId,
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
        ),
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
            profileId,
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

        writes.updateBullet(profileId, first.id, BulletRequest("Rewritten.", setOf(skillId("Kotlin"))))

        val after = profiles.require(profileId).experiences.single()
        assertEquals(listOf(first.id, second.id), after.bullets.map { it.id })
        assertEquals("Rewritten.", after.bullets.first().text)
        assertEquals("Ran the Postgres migration.", after.bullets.last().text)
    }

    @Test
    fun `deleting a bullet leaves the others alone`() {
        val experience = seed().experiences.single()
        val (first, second) = experience.bullets

        val after = writes.deleteBullet(profileId, first.id).experiences.single()

        assertEquals(listOf(second.id), after.bullets.map { it.id })
    }

    // ------------------------------------------------------------- invariants

    @Test
    fun `a skill still cited by a bullet cannot be deleted`() {
        val profile = seed()
        val kotlin = profile.skills.single { it.skillId == skillId("Kotlin") }

        val rejected = assertThrows<ProfileConflictException> { writes.deleteSkill(profileId, kotlin.id) }

        assertContains(rejected.message!!, "Kotlin")
        assertEquals(
            listOf("Built payment services in Kotlin."),
            rejected.blockingBullets.map { it.text },
        )
        // The refusal must not have half-applied.
        assertTrue(profiles.require(profileId).heldSkillIds.contains(skillId("Kotlin")))
    }

    @Test
    fun `a skill no bullet cites can be deleted`() {
        val profile = seed()
        writes.addSkill(profileId, SkillRequest(skillId = skillId("Docker"), proficiency = Proficiency.WORKING))
        val docker = profiles.require(profileId).skills.single { it.skillId == skillId("Docker") }

        val after = writes.deleteSkill(profileId, docker.id)

        assertNull(after.skills.firstOrNull { it.skillId == skillId("Docker") })
        assertEquals(profile.skills.size, after.skills.size)
    }

    @Test
    fun `a bullet cannot cite a skill the profile does not declare`() {
        val experience = seed().experiences.single()

        val rejected = assertThrows<ProfileConflictException> {
            writes.addBullet(profileId, experience.id, BulletRequest("Deployed with Kubernetes.", setOf(skillId("Kubernetes"))))
        }

        assertContains(rejected.message!!, "Kubernetes")
        assertEquals(2, profiles.require(profileId).experiences.single().bullets.size)
    }

    @Test
    fun `holding a skill twice is refused`() {
        seed()

        val rejected = assertThrows<ProfileConflictException> {
            writes.addSkill(profileId, SkillRequest(skillId = skillId("Kotlin"), proficiency = Proficiency.WORKING))
        }

        assertContains(rejected.message!!, "Kotlin")
    }

    /**
     * `language_skill` is unique on `(profile_id, lower(language))` because
     * CandidateProfile.languageLevel() matches case-insensitively - otherwise 'English' and
     * 'english' could both exist and which one the analysis pipeline compared against would be
     * arbitrary.
     */
    @Test
    fun `a language already listed under different casing is refused`() {
        seed()
        writes.addLanguage(profileId, LanguageRequest("English", LanguageLevel.C1))

        val rejected = assertThrows<ProfileConflictException> {
            writes.addLanguage(profileId, LanguageRequest("english", LanguageLevel.B2))
        }

        assertContains(rejected.message!!, "English")
        assertEquals(1, profiles.require(profileId).languages.size)
    }

    @Test
    fun `two profiles can each hold a language under the same name`() {
        seed()
        writes.addLanguage(profileId, LanguageRequest("English", LanguageLevel.C1))

        val other = management.create("Other").id
        writes.putDetails(other, DetailsRequest(fullName = "Someone Else"))
        val added = writes.addLanguage(other, LanguageRequest("English", LanguageLevel.B1))

        assertEquals(1, added.languages.size)
    }

    @Test
    fun `a role cannot end before it starts`() {
        seed()

        assertThrows<ProfileConflictException> {
            writes.addExperience(
                profileId,
                ExperienceRequest(
                    company = "Initech",
                    roleTitle = "Engineer",
                    startedOn = LocalDate.of(2020, 1, 1),
                    endedOn = LocalDate.of(2019, 1, 1),
                ),
            )
        }
    }

    @Test
    fun `an unknown id is reported as unknown rather than as a conflict`() {
        seed()
        assertThrows<UnknownProfileEntityException> { writes.deleteSkill(profileId, 99_999) }
        assertThrows<UnknownProfileEntityException> { writes.updateBullet(profileId, 99_999, BulletRequest("x")) }
        assertThrows<UnknownProfileEntityException> {
            writes.addBullet(profileId, 99_999, BulletRequest("x"))
        }
    }

    @Test
    fun `an id belonging to another profile is reported as unknown`() {
        seed()
        val kotlin = profiles.require(profileId).skills.single { it.skillId == skillId("Kotlin") }

        val other = management.create("Other").id
        writes.putDetails(other, DetailsRequest(fullName = "Someone Else"))

        assertThrows<UnknownProfileEntityException> { writes.deleteSkill(other, kotlin.id) }
        // Untouched on the profile it actually belongs to.
        assertTrue(profiles.require(profileId).heldSkillIds.contains(skillId("Kotlin")))
    }

    // ---------------------------------------------------------------- projects

    @Test
    fun `a skill still cited by a project's own skill badge cannot be deleted`() {
        val profile = seed()
        val kotlin = profile.skills.single { it.skillId == skillId("Kotlin") }
        writes.addProject(profileId, ProjectRequest(name = "Side project", skillIds = setOf(skillId("Kotlin"))))

        val rejected = assertThrows<ProfileConflictException> { writes.deleteSkill(profileId, kotlin.id) }

        assertContains(rejected.message!!, "Kotlin")
        assertEquals(listOf("Side project"), rejected.blockingProjects.map { it.name })
        // The refusal must not have half-applied.
        assertTrue(profiles.require(profileId).heldSkillIds.contains(skillId("Kotlin")))
    }

    @Test
    fun `a project bullet cannot cite a skill the profile does not declare`() {
        seed()
        val project = writes.addProject(profileId, ProjectRequest(name = "Side project")).projects.single()

        val rejected = assertThrows<ProfileConflictException> {
            writes.addProjectBullet(
                profileId, project.id,
                BulletRequest("Deployed with Kubernetes.", setOf(skillId("Kubernetes"))),
            )
        }

        assertContains(rejected.message!!, "Kubernetes")
        assertTrue(profiles.require(profileId).projects.single().bullets.isEmpty())
    }

    @Test
    fun `a project skill badge cannot cite a skill the profile does not declare`() {
        seed()

        val rejected = assertThrows<ProfileConflictException> {
            writes.addProject(profileId, ProjectRequest(name = "Side project", skillIds = setOf(skillId("Kubernetes"))))
        }

        assertContains(rejected.message!!, "Kubernetes")
        assertTrue(profiles.require(profileId).projects.isEmpty())
    }

    @Test
    fun `editing a project leaves its bullet ids alone`() {
        seed()
        val project = writes.addProject(
            profileId,
            ProjectRequest(name = "Side project", skillIds = setOf(skillId("Kotlin"))),
        ).projects.single()
        val bulletId = writes.addProjectBullet(
            profileId, project.id,
            BulletRequest("Built a CLI in Kotlin.", setOf(skillId("Kotlin"))),
        ).projects.single().bullets.single().id

        val after = writes.updateProject(
            profileId,
            project.id,
            ProjectRequest(name = "Side project v2", skillIds = setOf(skillId("Kotlin"))),
        ).projects.single()

        assertEquals("Side project v2", after.name)
        assertEquals(listOf(bulletId), after.bullets.map { it.id })
    }

    /** Proves the owner-exclusive bullet queries don't cross-leak between the two kinds of owner. */
    @Test
    fun `a project's bullets stay independent of an experience's bullets`() {
        val experience = seed().experiences.single()
        val project = writes.addProject(
            profileId,
            ProjectRequest(name = "Side project", skillIds = setOf(skillId("Kotlin"))),
        ).projects.single()
        val projectBulletId = writes.addProjectBullet(
            profileId, project.id,
            BulletRequest("Built a CLI in Kotlin.", setOf(skillId("Kotlin"))),
        ).projects.single().bullets.single().id

        val afterDeletingExperienceBullet = writes.deleteBullet(profileId, experience.bullets.first().id)
        assertEquals(
            listOf(projectBulletId),
            afterDeletingExperienceBullet.projects.single().bullets.map { it.id },
        )

        val afterDeletingProjectBullet = writes.deleteBullet(profileId, projectBulletId)
        assertEquals(
            listOf(experience.bullets[1].id),
            afterDeletingProjectBullet.experiences.single().bullets.map { it.id },
        )
        assertTrue(afterDeletingProjectBullet.projects.single().bullets.isEmpty())
    }

    // ------------------------------------------------------- consent clauses

    @Test
    fun `a consent clause can be added, edited and deleted`() {
        seed()

        val added = writes.addConsentClause(
            profileId, ConsentClauseRequest(language = "English", text = "I consent to processing of my data."),
        ).consentClauses.single()
        assertEquals("English", added.language)

        val updated = writes.updateConsentClause(
            profileId, added.id, ConsentClauseRequest(language = "English", text = "Updated wording."),
        ).consentClauses.single()
        assertEquals("Updated wording.", updated.text)

        val afterDelete = writes.deleteConsentClause(profileId, added.id)
        assertTrue(afterDelete.consentClauses.isEmpty())
    }

    @Test
    fun `a second consent clause for the same language cannot be added`() {
        seed()
        writes.addConsentClause(profileId, ConsentClauseRequest(language = "Polish", text = "Zgoda."))

        val rejected = assertThrows<ProfileConflictException> {
            writes.addConsentClause(profileId, ConsentClauseRequest(language = "polish", text = "Inna zgoda."))
        }

        assertContains(rejected.message!!, "Polish")
        assertEquals(1, profiles.require(profileId).consentClauses.size)
    }

    @Test
    fun `editing a consent clause into another language's slot is rejected`() {
        seed()
        writes.addConsentClause(profileId, ConsentClauseRequest(language = "English", text = "English wording."))
        val polish = writes.addConsentClause(
            profileId, ConsentClauseRequest(language = "Polish", text = "Zgoda."),
        ).consentClauses.single { it.language == "Polish" }

        assertThrows<ProfileConflictException> {
            writes.updateConsentClause(profileId, polish.id, ConsentClauseRequest(language = "English", text = "Zgoda."))
        }
    }

    // -------------------------------------------------------------- bootstrap

    @Test
    fun `a fresh profile can have its details filled in without importing a document`() {
        assertNull(profiles.current(profileId))

        val created = writes.putDetails(profileId, DetailsRequest(fullName = "Rafal Jankowski", headline = "Consultant"))

        assertEquals("Rafal Jankowski", created.details.fullName)
        assertEquals("Consultant", created.details.headline)
        assertTrue(created.skills.isEmpty())
        assertNotNull(profiles.current(profileId))
    }

    @Test
    fun `a career goal round-trips and can be cleared`() {
        val withGoal = writes.putDetails(
            profileId,
            DetailsRequest(fullName = "Rafal Jankowski", careerGoal = "I'm moving from QA into backend development."),
        )
        assertEquals("I'm moving from QA into backend development.", withGoal.details.careerGoal)

        val cleared = writes.putDetails(profileId, DetailsRequest(fullName = "Rafal Jankowski", careerGoal = null))
        assertNull(cleared.details.careerGoal)
    }

    @Test
    fun `entities added to a fresh profile land in insertion order`() {
        writes.putDetails(profileId, DetailsRequest(fullName = "Rafal Jankowski"))
        writes.addSkill(profileId, SkillRequest(skillId = skillId("Kotlin"), proficiency = Proficiency.EXPERT))
        writes.addSkill(profileId, SkillRequest(skillId = skillId("Docker"), proficiency = Proficiency.WORKING))
        val profile = writes.addSkill(profileId, SkillRequest(skillId = skillId("PostgreSQL"), proficiency = Proficiency.WORKING))

        assertEquals(
            listOf(skillId("Kotlin"), skillId("Docker"), skillId("PostgreSQL")),
            profile.skills.map { it.skillId },
        )
    }

    // ---------------------------------------------------------------- ordering

    @Test
    fun `reordering skills round-trips`() {
        seed()
        val ids = profiles.require(profileId).skills.map { it.id }

        val reordered = writes.reorderSkills(profileId, ids.reversed())

        assertEquals(ids.reversed(), reordered.skills.map { it.id })
        assertEquals(ids.reversed(), profiles.require(profileId).skills.map { it.id })
    }

    @Test
    fun `reordering bullets round-trips within their role`() {
        val experience = seed().experiences.single()
        val ids = experience.bullets.map { it.id }

        val reordered = writes.reorderBullets(profileId, experience.id, ids.reversed())

        assertEquals(ids.reversed(), reordered.experiences.single().bullets.map { it.id })
    }

    /** A partial list would silently leave the omitted rows wherever they happened to be. */
    @Test
    fun `a reorder naming only some of the collection is refused`() {
        seed()
        val ids = profiles.require(profileId).skills.map { it.id }

        assertThrows<ProfileConflictException> { writes.reorderSkills(profileId, listOf(ids.first())) }
        assertThrows<ProfileConflictException> { writes.reorderSkills(profileId, ids + ids.first()) }
        assertEquals(ids, profiles.require(profileId).skills.map { it.id })
    }

    // ---------------------------------------------------------------- revision

    @Test
    fun `every write path bumps the revision`() {
        val start = profiles.revision(profileId)

        writes.putDetails(profileId, DetailsRequest(fullName = "Rafal Jankowski"))
        assertEquals(start + 1, profiles.revision(profileId))

        writes.addSkill(profileId, SkillRequest(skillId = skillId("Kotlin"), proficiency = Proficiency.EXPERT))
        assertEquals(start + 2, profiles.revision(profileId))

        val experience = writes.addExperience(
            profileId,
            ExperienceRequest(company = "Acme", roleTitle = "Engineer", startedOn = LocalDate.of(2021, 1, 1)),
        ).experiences.single()
        assertEquals(start + 3, profiles.revision(profileId))

        val bullet = writes.addBullet(profileId, experience.id, BulletRequest("Shipped things.", setOf(skillId("Kotlin"))))
            .experiences.single().bullets.single()
        assertEquals(start + 4, profiles.revision(profileId))

        writes.updateBullet(profileId, bullet.id, BulletRequest("Shipped better things.", setOf(skillId("Kotlin"))))
        assertEquals(start + 5, profiles.revision(profileId))

        writes.addLanguage(profileId, LanguageRequest("Polish", LanguageLevel.NATIVE))
        assertEquals(start + 6, profiles.revision(profileId))

        writes.reorderSkills(profileId, profiles.require(profileId).skills.map { it.id })
        assertEquals(start + 7, profiles.revision(profileId))

        writes.deleteBullet(profileId, bullet.id)
        assertEquals(start + 8, profiles.revision(profileId))
    }

    /**
     * Import is a full replace, so it strands every bullet id a stored CV cites. It has to bump the
     * revision for the same reason a single edit does - arguably more so.
     */
    @Test
    fun `importing a document bumps the revision too`() {
        val before = profiles.revision(profileId)

        val imported = seed()

        assertEquals(before + 1, imported.revision)
        assertEquals(before + 1, profiles.revision(profileId))
    }

    @Test
    fun `the revision is exposed on the profile itself`() {
        val created = writes.putDetails(profileId, DetailsRequest(fullName = "Rafal Jankowski"))
        assertEquals(profiles.revision(profileId), created.revision)
    }
}
