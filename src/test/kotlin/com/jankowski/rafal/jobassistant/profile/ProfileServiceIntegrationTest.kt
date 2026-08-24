package com.jankowski.rafal.jobassistant.profile

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class ProfileServiceIntegrationTest(
    @Autowired private val profiles: ProfileService,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun clearProfile() {
        listOf("work_experience", "profile_skill", "profile_link", "education", "language_skill")
            .forEach { jdbc.sql("delete from $it").update() }
        jdbc.sql("delete from profile_details").update()
    }

    private fun sampleImport() = ProfileImport(
        details = ProfileDetails(
            fullName = "Rafal Jankowski",
            headline = "Backend Engineer",
            email = "rafal@example.com",
            location = "Poland",
            summary = "Backend engineer working mostly on the JVM.",
        ),
        links = listOf(LinkImport("GitHub", "https://github.com/example")),
        skills = listOf(
            SkillImport("Kotlin", Proficiency.EXPERT, yearsOfExperience = "6".toBigDecimal(), lastUsedYear = 2026),
            SkillImport("Spring Boot", Proficiency.PROFICIENT, yearsOfExperience = "5".toBigDecimal()),
            SkillImport("PostgreSQL", Proficiency.WORKING),
        ),
        experiences = listOf(
            ExperienceImport(
                company = "Acme",
                roleTitle = "Senior Backend Engineer",
                startedOn = LocalDate.of(2021, 1, 1),
                bullets = listOf(
                    BulletImport("Built payment services in Kotlin.", listOf("Kotlin")),
                    BulletImport("Ran the Postgres migration.", listOf("PostgreSQL", "Kotlin")),
                ),
            ),
            ExperienceImport(
                company = "Initech",
                roleTitle = "Backend Engineer",
                startedOn = LocalDate.of(2018, 3, 1),
                endedOn = LocalDate.of(2020, 12, 31),
                bullets = listOf(BulletImport("Maintained a Spring Boot monolith.", listOf("Spring Boot"))),
            ),
        ),
        education = listOf(
            EducationImport("Some University", "BSc", "Computer Science", LocalDate.of(2014, 10, 1), LocalDate.of(2018, 6, 30)),
        ),
        languages = listOf(
            LanguageImport("Polish", LanguageLevel.NATIVE),
            LanguageImport("English", LanguageLevel.C1),
        ),
    )

    @Test
    fun `no profile is absent rather than empty`() {
        assertNull(profiles.current())
        assertThrows<IllegalStateException> { profiles.require() }
    }

    @Test
    fun `imports and reads back the whole document`() {
        val saved = profiles.replace(sampleImport())

        assertEquals("Rafal Jankowski", saved.details.fullName)
        assertEquals(1, saved.links.size)
        assertEquals(3, saved.skills.size)
        assertEquals(2, saved.experiences.size)
        assertEquals(1, saved.education.size)
        assertEquals(2, saved.languages.size)
    }

    @Test
    fun `experiences keep import order and their bullets`() {
        val saved = profiles.replace(sampleImport())

        assertEquals(listOf("Acme", "Initech"), saved.experiences.map { it.company })
        assertEquals(
            listOf("Built payment services in Kotlin.", "Ran the Postgres migration."),
            saved.experiences.first().bullets.map { it.text },
        )
    }

    @Test
    fun `bullets carry the canonical skill ids they evidence`() {
        val saved = profiles.replace(sampleImport())
        val kotlinId = assertNotNull(catalog.resolve("Kotlin")).id
        val postgresId = assertNotNull(catalog.resolve("PostgreSQL")).id

        val migrationBullet = saved.bullets.single { it.text.startsWith("Ran the Postgres") }
        assertEquals(setOf(kotlinId, postgresId), migrationBullet.skillIds)

        assertEquals(2, saved.bulletsEvidencing(kotlinId).size)
    }

    @Test
    fun `held skill ids are the allowlist for everything downstream`() {
        val saved = profiles.replace(sampleImport())
        val expected = setOf("Kotlin", "Spring Boot", "PostgreSQL")
            .map { assertNotNull(catalog.resolve(it)).id }
            .toSet()

        assertEquals(expected, saved.heldSkillIds)
    }

    @Test
    fun `skill names resolve through aliases so the document can use any spelling`() {
        val saved = profiles.replace(
            sampleImport().copy(
                skills = listOf(SkillImport("postgres", Proficiency.WORKING)),
                experiences = emptyList(),
            )
        )

        assertEquals(assertNotNull(catalog.resolve("PostgreSQL")).id, saved.skills.single().skillId)
    }

    @Test
    fun `replace is a full replace, not a merge`() {
        profiles.replace(sampleImport())

        val replaced = profiles.replace(
            sampleImport().copy(
                skills = listOf(SkillImport("Kotlin", Proficiency.EXPERT)),
                experiences = emptyList(),
                education = emptyList(),
                languages = emptyList(),
                links = emptyList(),
            )
        )

        assertEquals(1, replaced.skills.size)
        assertTrue(replaced.experiences.isEmpty())
        assertTrue(replaced.languages.isEmpty())
    }

    @Test
    fun `an unknown skill name rejects the whole import`() {
        val failure = assertThrows<ProfileImportException> {
            profiles.replace(
                sampleImport().copy(
                    skills = sampleImport().skills + SkillImport("Frobnication", Proficiency.EXPERT)
                )
            )
        }

        assertEquals(listOf("Frobnication"), failure.unresolvedSkills)
        assertNull(profiles.current(), "a rejected import must not leave partial data behind")
    }

    @Test
    fun `a bullet cannot evidence a skill the profile does not declare`() {
        val failure = assertThrows<ProfileImportException> {
            profiles.replace(
                sampleImport().copy(
                    experiences = listOf(
                        ExperienceImport(
                            company = "Acme",
                            roleTitle = "Engineer",
                            startedOn = LocalDate.of(2021, 1, 1),
                            bullets = listOf(BulletImport("Deployed to Kubernetes.", listOf("Kubernetes"))),
                        )
                    )
                )
            )
        }

        assertEquals(listOf("Kubernetes"), failure.undeclaredBulletSkills)
    }

    @Test
    fun `language levels are queryable for offer requirements`() {
        val saved = profiles.replace(sampleImport())

        assertEquals(LanguageLevel.C1, saved.languageLevel("English"))
        assertEquals(LanguageLevel.C1, saved.languageLevel("english"))
        assertNull(saved.languageLevel("German"))
        assertTrue(assertNotNull(saved.languageLevel("English")).atLeast(LanguageLevel.B2))
    }

    @Test
    fun `current experience has no end date`() {
        val saved = profiles.replace(sampleImport())

        assertTrue(saved.experiences.first { it.company == "Acme" }.isCurrent)
        assertTrue(!saved.experiences.first { it.company == "Initech" }.isCurrent)
    }
}
