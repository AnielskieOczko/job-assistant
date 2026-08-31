package com.jankowski.rafal.jobassistant.profile

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Everything per-entity profile editing promises, asserted at the only seam that is a contract:
 * HTTP.
 *
 * The status codes and `ProblemDetail` extension names are what the SPA reads, and a test that
 * calls the write service directly can catch neither a handler that never fires nor an extension
 * property the frontend spells differently. Just as important, this seam says nothing about *how*
 * the nine collections are implemented, so the implementation stays free to change underneath it -
 * which is the whole point of having the safety net here rather than around a service interface.
 *
 * Read-back uses [ProfileService], the module's public read API, purely to obtain ids conveniently;
 * every assertion about behaviour is on the response of a request.
 */
@IntegrationTest
internal class ProfileCrudHttpTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val jdbc: JdbcClient,
) {

    /**
     * Built from the context rather than injected: Boot 4 moved the MockMvc autoconfiguration out
     * of `spring-boot-test-autoconfigure`, and `spring-test` alone is enough for this.
     */
    private lateinit var mvc: MockMvc

    private var profileId: Long = 0

    @BeforeEach
    fun clearProfile() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbc.sql("delete from profile").update()
        profileId = createProfile()
    }

    // ------------------------------------------------------------------ setup

    private fun skillId(name: String) = catalog.resolve(name)!!.id

    private fun createProfile(name: String = "Test") = management.create(name).id

    private fun profile() = profiles.require(profileId)

    /** Two skills and one role with two bullets - enough for every citation rule to have a subject. */
    private fun seed(id: Long = profileId) = profiles.replace(
        id,
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

    private fun post(path: String, body: String, id: Long = profileId): ResultActions =
        mvc.perform(post("/api/profiles/$id/$path").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun put(path: String, body: String, id: Long = profileId): ResultActions =
        mvc.perform(put("/api/profiles/$id/$path").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun remove(path: String, id: Long = profileId): ResultActions =
        mvc.perform(delete("/api/profiles/$id/$path"))

    private fun reorder(collection: String, ids: List<Long>, id: Long = profileId): ResultActions =
        put("$collection/order", """{"ids":[${ids.joinToString(",")}]}""", id)

    // ---------------------------------------------------------------- reading

    @Test
    fun `an empty profile is 204 rather than 404`() {
        mvc.perform(get("/api/profiles/$profileId")).andExpect(status().isNoContent)
    }

    @Test
    fun `a filled profile is 200`() {
        seed()
        mvc.perform(get("/api/profiles/$profileId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.details.fullName").value("Rafal Jankowski"))
    }

    // ---------------------------------------------------------------- details

    @Test
    fun `putting details fills in an already-created profile and returns it`() {
        put("details", """{"fullName":"Rafal Jankowski","headline":"Consultant"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.details.fullName").value("Rafal Jankowski"))
            .andExpect(jsonPath("$.details.headline").value("Consultant"))
            .andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.skills.length()").value(0))

        mvc.perform(get("/api/profiles/$profileId")).andExpect(status().isOk)
    }

    /** A full-entity PUT, so an omitted optional field clears the stored value rather than keeping it. */
    @Test
    fun `a career goal round-trips and can be cleared`() {
        put("details", """{"fullName":"Rafal Jankowski","careerGoal":"Moving from QA into backend."}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.details.careerGoal").value("Moving from QA into backend."))

        put("details", """{"fullName":"Rafal Jankowski"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.details.careerGoal").isEmpty)
    }

    @Test
    fun `a blank required field is a 400 naming the field`() {
        seed()
        put("details", """{"fullName":"  "}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid profile edit"))
            .andExpect(jsonPath("$.fieldErrors.fullName").exists())
    }

    @Test
    fun `an edit against an unknown profile is a 404`() {
        put("details", """{"fullName":"Rafal Jankowski"}""", id = 99_999)
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------ links

    @Test
    fun `a link can be added, edited, reordered and deleted`() {
        seed()
        post("links", """{"label":"GitHub","url":"https://github.com/example"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.links.length()").value(1))
            .andExpect(jsonPath("$.links[0].label").value("GitHub"))
        post("links", """{"label":"LinkedIn","url":"https://linkedin.com/in/example"}""")
            .andExpect(status().isCreated)

        val ids = profile().links.map { it.id }

        put("links/${ids.first()}", """{"label":"GitHub profile","url":"https://github.com/example"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.links[0].label").value("GitHub profile"))

        reorder("links", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.links[0].id").value(ids.last()))

        remove("links/${ids.first()}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.links.length()").value(1))
            .andExpect(jsonPath("$.links[0].id").value(ids.last()))
    }

    @Test
    fun `a link with a blank label is a 400`() {
        seed()
        post("links", """{"label":"","url":"https://github.com/example"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.label").exists())
    }

    // ----------------------------------------------------------------- skills

    @Test
    fun `adding a skill answers 201 with the whole profile`() {
        seed()
        post("skills", """{"skillId":${skillId("Docker")},"proficiency":"WORKING"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.skills.length()").value(3))
            .andExpect(jsonPath("$.heldSkillIds").isArray)
    }

    @Test
    fun `a skill can be edited without changing which skill it is`() {
        seed()
        val kotlin = profile().skills.single { it.skillId == skillId("Kotlin") }

        put("skills/${kotlin.id}", """{"proficiency":"PROFICIENT","yearsOfExperience":7,"lastUsedYear":2026}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.skills[0].proficiency").value("PROFICIENT"))
            .andExpect(jsonPath("$.skills[0].lastUsedYear").value(2026))
            .andExpect(jsonPath("$.skills[0].skillId").value(kotlin.skillId))
    }

    @Test
    fun `holding a skill twice is a 409`() {
        seed()
        post("skills", """{"skillId":${skillId("Kotlin")},"proficiency":"WORKING"}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Profile edit rejected"))
    }

    @Test
    fun `a skill the catalog does not have is a 404`() {
        seed()
        post("skills", """{"skillId":99999,"proficiency":"WORKING"}""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Not on this profile"))
    }

    /** The frontend renders `blockingBullets` directly, so both the status and the name matter. */
    @Test
    fun `deleting a cited skill is a 409 naming the bullets in the way`() {
        val profile = seed()
        val kotlin = profile.skills.single { it.skillId == skillId("Kotlin") }

        remove("skills/${kotlin.id}")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.blockingBullets.length()").value(1))
            .andExpect(jsonPath("$.blockingBullets[0].text").value("Built payment services in Kotlin."))
            .andExpect(jsonPath("$.blockingBullets[0].id").exists())

        // The refusal must not have half-applied.
        assertTrue(skillId("Kotlin") in profile().heldSkillIds)
    }

    @Test
    fun `deleting a cited skill names the projects in the way too`() {
        val profile = seed()
        val kotlin = profile.skills.single { it.skillId == skillId("Kotlin") }
        post("projects", """{"name":"Side project","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)

        remove("skills/${kotlin.id}")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.blockingProjects.length()").value(1))
            .andExpect(jsonPath("$.blockingProjects[0].name").value("Side project"))
    }

    @Test
    fun `a skill nothing cites can be deleted`() {
        seed()
        post("skills", """{"skillId":${skillId("Docker")},"proficiency":"WORKING"}""")
            .andExpect(status().isCreated)
        val docker = profile().skills.single { it.skillId == skillId("Docker") }

        remove("skills/${docker.id}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.skills.length()").value(2))

        assertNull(profile().skills.firstOrNull { it.skillId == skillId("Docker") })
    }

    @Test
    fun `an unknown id is a 404`() {
        seed()
        remove("skills/99999")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Not on this profile"))
    }

    // ------------------------------------------------------------ experiences

    @Test
    fun `an experience can be added, edited, reordered and deleted`() {
        seed()
        post("experiences", """{"company":"Initech","roleTitle":"Engineer","startedOn":"2018-03-01","endedOn":"2020-12-31"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.experiences.length()").value(2))

        val ids = profile().experiences.map { it.id }

        put("experiences/${ids.last()}", """{"company":"Initech LLC","roleTitle":"Engineer","startedOn":"2018-03-01"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences[1].company").value("Initech LLC"))
            .andExpect(jsonPath("$.experiences[1].isCurrent").value(true))

        reorder("experiences", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences[0].id").value(ids.last()))

        remove("experiences/${ids.last()}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences.length()").value(1))
    }

    @Test
    fun `a role that ends before it starts is a 409`() {
        seed()
        post("experiences", """{"company":"Initech","roleTitle":"Engineer","startedOn":"2020-01-01","endedOn":"2019-01-01"}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Profile edit rejected"))
    }

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

        put(
            "experiences/${before.id}",
            """{"company":"Acme Corporation","roleTitle":"${before.roleTitle}","startedOn":"${before.startedOn}"}""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences[0].company").value("Acme Corporation"))

        assertEquals(bulletIds, profile().experiences.single().bullets.map { it.id })
    }

    /** Bullets and their skill tags cascade from the `work_experience` row. */
    @Test
    fun `deleting an experience takes its bullets with it`() {
        val experience = seed().experiences.single()

        remove("experiences/${experience.id}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences.length()").value(0))

        assertTrue(profile().bullets.isEmpty())
    }

    // ---------------------------------------------------------------- bullets

    @Test
    fun `a bullet can be added, edited, reordered and deleted`() {
        val experience = seed().experiences.single()
        val existing = experience.bullets.map { it.id }

        post(
            "experiences/${experience.id}/bullets",
            """{"text":"Tuned queries.","skillIds":[${skillId("PostgreSQL")}]}""",
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.experiences[0].bullets.length()").value(3))

        val ids = profile().experiences.single().bullets.map { it.id }
        assertEquals(existing, ids.take(2), "a new bullet lands at the end of its role")

        put("bullets/${ids.last()}", """{"text":"Tuned the slow queries.","skillIds":[${skillId("PostgreSQL")}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences[0].bullets[2].text").value("Tuned the slow queries."))

        reorder("experiences/${experience.id}/bullets", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences[0].bullets[0].id").value(ids.last()))

        remove("bullets/${ids.last()}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences[0].bullets.length()").value(2))
    }

    @Test
    fun `a bullet citing an undeclared skill is a 409`() {
        val experience = seed().experiences.single()
        post(
            "experiences/${experience.id}/bullets",
            """{"text":"Deployed with Kubernetes.","skillIds":[${skillId("Kubernetes")}]}""",
        )
            .andExpect(status().isConflict)

        assertEquals(2, profile().experiences.single().bullets.size)
    }

    @Test
    fun `editing a bullet to cite an undeclared skill is a 409`() {
        val bullet = seed().experiences.single().bullets.first()
        put("bullets/${bullet.id}", """{"text":"Deployed with Kubernetes.","skillIds":[${skillId("Kubernetes")}]}""")
            .andExpect(status().isConflict)

        assertEquals("Built payment services in Kotlin.", profile().bullets.first().text)
    }

    @Test
    fun `editing one bullet leaves its siblings' ids alone`() {
        val experience = seed().experiences.single()
        val (first, second) = experience.bullets

        put("bullets/${first.id}", """{"text":"Rewritten.","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isOk)

        val after = profile().experiences.single()
        assertEquals(listOf(first.id, second.id), after.bullets.map { it.id })
        assertEquals("Rewritten.", after.bullets.first().text)
        assertEquals("Ran the Postgres migration.", after.bullets.last().text)
    }

    @Test
    fun `deleting a bullet leaves the others alone`() {
        val experience = seed().experiences.single()
        val (first, second) = experience.bullets

        remove("bullets/${first.id}").andExpect(status().isOk)

        assertEquals(listOf(second.id), profile().experiences.single().bullets.map { it.id })
    }

    @Test
    fun `a bullet added under an unknown experience is a 404`() {
        seed()
        post("experiences/99999/bullets", """{"text":"Shipped things."}""")
            .andExpect(status().isNotFound)
    }

    @Test
    fun `editing or deleting an unknown bullet is a 404`() {
        seed()
        put("bullets/99999", """{"text":"Shipped things."}""").andExpect(status().isNotFound)
        remove("bullets/99999").andExpect(status().isNotFound)
    }

    @Test
    fun `reordering the bullets of an unknown experience is a 404`() {
        val bullet = seed().experiences.single().bullets.first()
        reorder("experiences/99999/bullets", listOf(bullet.id))
            .andExpect(status().isNotFound)
    }

    // -------------------------------------------------------------- education

    @Test
    fun `an education entry can be added, edited, reordered and deleted`() {
        seed()
        post("education", """{"institution":"AGH","degree":"BSc","fieldOfStudy":"Computer Science"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.education[0].institution").value("AGH"))
        post("education", """{"institution":"Coursera","degree":"Specialisation"}""")
            .andExpect(status().isCreated)

        val ids = profile().education.map { it.id }

        put("education/${ids.first()}", """{"institution":"AGH UST","degree":"BSc","startedOn":"2012-10-01"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.education[0].institution").value("AGH UST"))
            .andExpect(jsonPath("$.education[0].fieldOfStudy").isEmpty)

        reorder("education", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.education[0].id").value(ids.last()))

        remove("education/${ids.first()}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.education.length()").value(1))
    }

    @Test
    fun `an unknown education id is a 404`() {
        seed()
        put("education/99999", """{"institution":"AGH","degree":"BSc"}""")
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------ credentials

    @Test
    fun `a credential can be added, edited, reordered and deleted`() {
        seed()
        post(
            "credentials",
            """{"title":"CKAD","issuer":"CNCF","kind":"CERTIFICATION","issuedOn":"2024-01-01","expiresOn":"2027-01-01"}""",
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.credentials[0].kind").value("CERTIFICATION"))
        post("credentials", """{"title":"Kotlin for Java Developers","issuer":"JetBrains","kind":"COURSE"}""")
            .andExpect(status().isCreated)

        val ids = profile().credentials.map { it.id }

        put("credentials/${ids.first()}", """{"title":"CKAD (renewed)","issuer":"CNCF","kind":"CERTIFICATION"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.credentials[0].title").value("CKAD (renewed)"))

        reorder("credentials", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.credentials[0].id").value(ids.last()))

        remove("credentials/${ids.first()}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.credentials.length()").value(1))
    }

    @Test
    fun `a credential that expires before it was issued is a 409`() {
        seed()
        post(
            "credentials",
            """{"title":"CKAD","issuer":"CNCF","kind":"CERTIFICATION","issuedOn":"2024-01-01","expiresOn":"2023-01-01"}""",
        )
            .andExpect(status().isConflict)

        assertTrue(profile().credentials.isEmpty())
    }

    // --------------------------------------------------------------- projects

    @Test
    fun `a project can be added, edited, reordered and deleted`() {
        seed()
        post("projects", """{"name":"Job assistant","url":"https://example.com","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.projects[0].name").value("Job assistant"))
            .andExpect(jsonPath("$.projects[0].skillIds.length()").value(1))
        post("projects", """{"name":"Second project"}""").andExpect(status().isCreated)

        val ids = profile().projects.map { it.id }

        put("projects/${ids.first()}", """{"name":"Job assistant v2","skillIds":[${skillId("PostgreSQL")}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projects[0].name").value("Job assistant v2"))
            .andExpect(jsonPath("$.projects[0].skillIds[0]").value(skillId("PostgreSQL")))

        reorder("projects", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projects[0].id").value(ids.last()))

        remove("projects/${ids.first()}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projects.length()").value(1))
    }

    @Test
    fun `a project that ends before it starts is a 409`() {
        seed()
        post("projects", """{"name":"Side project","startedOn":"2024-01-01","endedOn":"2023-01-01"}""")
            .andExpect(status().isConflict)

        assertTrue(profile().projects.isEmpty())
    }

    @Test
    fun `a project skill badge citing an undeclared skill is a 409`() {
        seed()
        post("projects", """{"name":"Side project","skillIds":[${skillId("Kubernetes")}]}""")
            .andExpect(status().isConflict)

        assertTrue(profile().projects.isEmpty())
    }

    @Test
    fun `a project bullet can be added and reordered, and cannot cite an undeclared skill`() {
        seed()
        post("projects", """{"name":"Side project","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)
        val project = profile().projects.single()

        post(
            "projects/${project.id}/bullets",
            """{"text":"Deployed with Kubernetes.","skillIds":[${skillId("Kubernetes")}]}""",
        )
            .andExpect(status().isConflict)
        assertTrue(profile().projects.single().bullets.isEmpty())

        post("projects/${project.id}/bullets", """{"text":"Built a CLI in Kotlin.","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)
        post("projects/${project.id}/bullets", """{"text":"Wrote the docs."}""")
            .andExpect(status().isCreated)

        val bulletIds = profile().projects.single().bullets.map { it.id }
        reorder("projects/${project.id}/bullets", bulletIds.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projects[0].bullets[0].id").value(bulletIds.last()))
    }

    @Test
    fun `editing a project leaves its bullet ids alone`() {
        seed()
        post("projects", """{"name":"Side project","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)
        val project = profile().projects.single()
        post("projects/${project.id}/bullets", """{"text":"Built a CLI in Kotlin.","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)
        val bulletId = profile().projects.single().bullets.single().id

        put("projects/${project.id}", """{"name":"Side project v2","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projects[0].name").value("Side project v2"))
            .andExpect(jsonPath("$.projects[0].bullets[0].id").value(bulletId))
    }

    /** Proves the owner-exclusive bullet queries don't cross-leak between the two kinds of owner. */
    @Test
    fun `a project's bullets stay independent of an experience's bullets`() {
        val experience = seed().experiences.single()
        post("projects", """{"name":"Side project","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)
        val project = profile().projects.single()
        post("projects/${project.id}/bullets", """{"text":"Built a CLI in Kotlin.","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(status().isCreated)
        val projectBulletId = profile().projects.single().bullets.single().id

        remove("bullets/${experience.bullets.first().id}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projects[0].bullets[0].id").value(projectBulletId))

        remove("bullets/$projectBulletId")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projects[0].bullets.length()").value(0))
            .andExpect(jsonPath("$.experiences[0].bullets[0].id").value(experience.bullets[1].id))
    }

    @Test
    fun `a bullet added under an unknown project is a 404`() {
        seed()
        post("projects/99999/bullets", """{"text":"Shipped things."}""")
            .andExpect(status().isNotFound)
    }

    // -------------------------------------------------------- consent clauses

    @Test
    fun `a consent clause can be added, edited and deleted`() {
        seed()
        post("consent-clauses", """{"language":"English","text":"I consent to processing of my data."}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.consentClauses[0].language").value("English"))

        val id = profile().consentClauses.single().id

        put("consent-clauses/$id", """{"language":"English","text":"Updated wording."}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.consentClauses[0].text").value("Updated wording."))

        remove("consent-clauses/$id")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.consentClauses.length()").value(0))
    }

    /**
     * A CV renders exactly one clause for the language it is written in, so a second one for the
     * same language would leave the generator choosing between two.
     */
    @Test
    fun `a second consent clause for the same language is a 409`() {
        seed()
        post("consent-clauses", """{"language":"Polish","text":"Zgoda."}""").andExpect(status().isCreated)

        post("consent-clauses", """{"language":"polish","text":"Inna zgoda."}""")
            .andExpect(status().isConflict)

        assertEquals(1, profile().consentClauses.size)
    }

    @Test
    fun `editing a consent clause into another language's slot is a 409`() {
        seed()
        post("consent-clauses", """{"language":"English","text":"English wording."}""").andExpect(status().isCreated)
        post("consent-clauses", """{"language":"Polish","text":"Zgoda."}""").andExpect(status().isCreated)
        val polish = profile().consentClauses.single { it.language == "Polish" }

        put("consent-clauses/${polish.id}", """{"language":"English","text":"Zgoda."}""")
            .andExpect(status().isConflict)
    }

    // -------------------------------------------------------------- languages

    @Test
    fun `a language can be added, edited, reordered and deleted`() {
        seed()
        post("languages", """{"language":"Polish","level":"NATIVE"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.languages[0].level").value("NATIVE"))
        post("languages", """{"language":"English","level":"B2"}""").andExpect(status().isCreated)

        val ids = profile().languages.map { it.id }

        put("languages/${ids.last()}", """{"language":"English","level":"C1"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.languages[1].level").value("C1"))

        reorder("languages", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.languages[0].id").value(ids.last()))

        remove("languages/${ids.first()}")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.languages.length()").value(1))
    }

    /**
     * `language_skill` is unique on `(profile_id, lower(language))` because
     * `CandidateProfile.languageLevel()` matches case-insensitively - otherwise 'English' and
     * 'english' could both exist and which one the analysis pipeline compared against would be
     * arbitrary.
     */
    @Test
    fun `a language already listed under different casing is a 409`() {
        seed()
        post("languages", """{"language":"English","level":"C1"}""").andExpect(status().isCreated)

        post("languages", """{"language":"english","level":"B2"}""")
            .andExpect(status().isConflict)

        assertEquals(1, profile().languages.size)
    }

    @Test
    fun `editing a language into another language's slot is a 409`() {
        seed()
        post("languages", """{"language":"English","level":"C1"}""").andExpect(status().isCreated)
        post("languages", """{"language":"Polish","level":"NATIVE"}""").andExpect(status().isCreated)
        val polish = profile().languages.single { it.language == "Polish" }

        put("languages/${polish.id}", """{"language":"english","level":"C2"}""")
            .andExpect(status().isConflict)
    }

    // ------------------------------------------------------- profile scoping

    @Test
    fun `two profiles can each hold a language under the same name`() {
        seed()
        post("languages", """{"language":"English","level":"C1"}""").andExpect(status().isCreated)

        val other = createProfile("Other")
        put("details", """{"fullName":"Someone Else"}""", id = other).andExpect(status().isOk)
        post("languages", """{"language":"English","level":"B1"}""", id = other)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.languages.length()").value(1))
    }

    @Test
    fun `an id belonging to another profile is a 404`() {
        seed()
        val kotlin = profile().skills.single { it.skillId == skillId("Kotlin") }

        val other = createProfile("Other")
        put("details", """{"fullName":"Someone Else"}""", id = other).andExpect(status().isOk)

        remove("skills/${kotlin.id}", id = other).andExpect(status().isNotFound)

        // Untouched on the profile it actually belongs to.
        assertTrue(skillId("Kotlin") in profile().heldSkillIds)
    }

    /**
     * `display_order` is counted within one profile, so a second persona's first entry must start
     * at the top of its own collection rather than after the first persona's.
     */
    @Test
    fun `each profile orders its own collection independently`() {
        seed()
        post("links", """{"label":"GitHub","url":"https://github.com/example"}""").andExpect(status().isCreated)
        post("links", """{"label":"LinkedIn","url":"https://linkedin.com/in/example"}""").andExpect(status().isCreated)

        val other = createProfile("Other")
        put("details", """{"fullName":"Someone Else"}""", id = other).andExpect(status().isOk)
        post("links", """{"label":"Blog","url":"https://blog.example.com"}""", id = other)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.links.length()").value(1))
            .andExpect(jsonPath("$.links[0].label").value("Blog"))

        assertEquals(listOf("GitHub", "LinkedIn"), profile().links.map { it.label })
    }

    // ---------------------------------------------------------------- ordering

    @Test
    fun `new entries land at the end of their collection`() {
        put("details", """{"fullName":"Rafal Jankowski"}""").andExpect(status().isOk)
        post("skills", """{"skillId":${skillId("Kotlin")},"proficiency":"EXPERT"}""").andExpect(status().isCreated)
        post("skills", """{"skillId":${skillId("Docker")},"proficiency":"WORKING"}""").andExpect(status().isCreated)
        post("skills", """{"skillId":${skillId("PostgreSQL")},"proficiency":"WORKING"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.skills[0].skillId").value(skillId("Kotlin")))
            .andExpect(jsonPath("$.skills[1].skillId").value(skillId("Docker")))
            .andExpect(jsonPath("$.skills[2].skillId").value(skillId("PostgreSQL")))
    }

    @Test
    fun `reordering answers with the collection in its new order`() {
        seed()
        post("skills", """{"skillId":${skillId("Docker")},"proficiency":"WORKING"}""")
            .andExpect(status().isCreated)

        val ids = profile().skills.map { it.id }
        reorder("skills", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.skills[0].id").value(ids.last()))

        assertEquals(ids.reversed(), profile().skills.map { it.id })
    }

    /** `/order` is a literal path that must win over the `{id}` template next to it. */
    @Test
    fun `a partial reorder is a 409 rather than a silent partial move`() {
        seed()
        val ids = profile().skills.map { it.id }

        reorder("skills", listOf(ids.first())).andExpect(status().isConflict)
        reorder("skills", ids + ids.first()).andExpect(status().isConflict)

        assertEquals(ids, profile().skills.map { it.id })
    }

    @Test
    fun `a reorder naming no ids at all is a 400`() {
        seed()
        reorder("skills", emptyList())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.ids").exists())
    }

    @Test
    fun `reordering bullets round-trips within their role`() {
        val experience = seed().experiences.single()
        val ids = experience.bullets.map { it.id }

        reorder("experiences/${experience.id}/bullets", ids.reversed())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.experiences[0].bullets[0].id").value(ids.last()))
    }

    // ---------------------------------------------------------------- revision

    @Test
    fun `every write path bumps the revision`() {
        var expected = 0L
        fun next() = ++expected

        put("details", """{"fullName":"Rafal Jankowski"}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("skills", """{"skillId":${skillId("Kotlin")},"proficiency":"EXPERT"}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("experiences", """{"company":"Acme","roleTitle":"Engineer","startedOn":"2021-01-01"}""")
            .andExpect(jsonPath("$.revision").value(next()))

        val experience = profile().experiences.single()
        post("experiences/${experience.id}/bullets", """{"text":"Shipped things.","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(jsonPath("$.revision").value(next()))

        val bullet = profile().bullets.single()
        put("bullets/${bullet.id}", """{"text":"Shipped better things.","skillIds":[${skillId("Kotlin")}]}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("links", """{"label":"GitHub","url":"https://github.com/example"}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("education", """{"institution":"AGH","degree":"BSc"}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("credentials", """{"title":"CKAD","issuer":"CNCF","kind":"CERTIFICATION"}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("projects", """{"name":"Side project"}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("consent-clauses", """{"language":"Polish","text":"Zgoda."}""")
            .andExpect(jsonPath("$.revision").value(next()))
        post("languages", """{"language":"Polish","level":"NATIVE"}""")
            .andExpect(jsonPath("$.revision").value(next()))

        reorder("skills", profile().skills.map { it.id })
            .andExpect(jsonPath("$.revision").value(next()))
        remove("bullets/${bullet.id}")
            .andExpect(jsonPath("$.revision").value(next()))
    }

    /** A refused write must leave the revision alone, or output would read as stale for nothing. */
    @Test
    fun `a refused write does not bump the revision`() {
        seed()
        val before = profile().revision

        post("skills", """{"skillId":${skillId("Kotlin")},"proficiency":"WORKING"}""")
            .andExpect(status().isConflict)

        assertEquals(before, profile().revision)
    }

    /**
     * Import is a full replace, so it strands every bullet id a stored CV cites. It has to bump the
     * revision for the same reason a single edit does - arguably more so.
     */
    @Test
    fun `importing a document bumps the revision too`() {
        val before = profiles.revision(profileId)

        post(
            "import",
            """{"details":{"fullName":"Rafal Jankowski"},"skills":[{"skill":"Kotlin","proficiency":"EXPERT"}]}""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(before + 1))
    }

    /** Unchanged from before CRUD existed, and the SPA still reads both extension names. */
    @Test
    fun `an import naming an unknown skill is still a 400 listing it`() {
        post("import", """{"details":{"fullName":"Rafal"},"skills":[{"skill":"Iceberg","proficiency":"EXPERT"}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Profile import rejected"))
            .andExpect(jsonPath("$.unresolvedSkills[0]").value("Iceberg"))
    }
}
