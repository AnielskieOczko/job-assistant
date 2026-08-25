package com.jankowski.rafal.jobassistant.profile

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDate

/**
 * The status codes and `ProblemDetail` extension names the SPA reads.
 *
 * The rest of the suite exercises services directly, which cannot catch a handler that never fires
 * or an extension property the frontend spells differently. These shapes are a wire contract, so
 * they are asserted over HTTP.
 */
@IntegrationTest
class ProfileCrudHttpTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val profiles: ProfileService,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val jdbc: JdbcClient,
) {

    /**
     * Built from the context rather than injected: Boot 4 moved the MockMvc autoconfiguration out
     * of `spring-boot-test-autoconfigure`, and `spring-test` alone is enough for this.
     */
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun clearProfile() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        listOf("work_experience", "profile_skill", "profile_link", "education", "language_skill")
            .forEach { jdbc.sql("delete from $it").update() }
        jdbc.sql("delete from profile_details").update()
    }

    private fun skillId(name: String) = catalog.resolve(name)!!.id

    private fun seed() = profiles.replace(
        ProfileImport(
            details = ProfileDetails(fullName = "Rafal Jankowski"),
            skills = listOf(SkillImport("Kotlin", Proficiency.EXPERT)),
            experiences = listOf(
                ExperienceImport(
                    company = "Acme",
                    roleTitle = "Engineer",
                    startedOn = LocalDate.of(2021, 1, 1),
                    bullets = listOf(BulletImport("Built services in Kotlin.", listOf("Kotlin"))),
                ),
            ),
        )
    )

    @Test
    fun `an empty profile is 204 rather than 404`() {
        mvc.perform(get("/api/profile")).andExpect(status().isNoContent)
    }

    @Test
    fun `putting details creates the profile and returns it`() {
        mvc.perform(
            put("/api/profile/details")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Rafal Jankowski","headline":"Consultant"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.details.fullName").value("Rafal Jankowski"))
            .andExpect(jsonPath("$.revision").value(1))

        mvc.perform(get("/api/profile")).andExpect(status().isOk)
    }

    @Test
    fun `adding a skill answers 201 with the whole profile`() {
        seed()
        mvc.perform(
            post("/api/profile/skills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"skillId":${skillId("Docker")},"proficiency":"WORKING"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.skills.length()").value(2))
            .andExpect(jsonPath("$.heldSkillIds").isArray)
    }

    @Test
    fun `a blank required field is a 400 naming the field`() {
        seed()
        mvc.perform(
            put("/api/profile/details")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"  "}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid profile edit"))
            .andExpect(jsonPath("$.fieldErrors.fullName").exists())
    }

    @Test
    fun `holding a skill twice is a 409`() {
        seed()
        mvc.perform(
            post("/api/profile/skills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"skillId":${skillId("Kotlin")},"proficiency":"WORKING"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Profile edit rejected"))
    }

    /** The frontend renders `blockingBullets` directly, so both the status and the name matter. */
    @Test
    fun `deleting a cited skill is a 409 naming the bullets in the way`() {
        val profile = seed()
        val kotlin = profile.skills.single()

        mvc.perform(delete("/api/profile/skills/${kotlin.id}"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.blockingBullets.length()").value(1))
            .andExpect(jsonPath("$.blockingBullets[0].text").value("Built services in Kotlin."))
            .andExpect(jsonPath("$.blockingBullets[0].id").exists())
    }

    @Test
    fun `an unknown id is a 404`() {
        seed()
        mvc.perform(delete("/api/profile/skills/99999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Not on this profile"))
    }

    @Test
    fun `a bullet citing an undeclared skill is a 409`() {
        val experience = seed().experiences.single()
        mvc.perform(
            post("/api/profile/experiences/${experience.id}/bullets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Deployed with Kubernetes.","skillIds":[${skillId("Kubernetes")}]}""")
        )
            .andExpect(status().isConflict)
    }

    /** Unchanged from before CRUD existed, and the SPA still reads both extension names. */
    @Test
    fun `an import naming an unknown skill is still a 400 listing it`() {
        mvc.perform(
            post("/api/profile/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"details":{"fullName":"Rafal"},"skills":[{"skill":"Iceberg","proficiency":"EXPERT"}]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Profile import rejected"))
            .andExpect(jsonPath("$.unresolvedSkills[0]").value("Iceberg"))
    }

    @Test
    fun `reordering answers with the collection in its new order`() {
        seed()
        mvc.perform(
            post("/api/profile/skills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"skillId":${skillId("Docker")},"proficiency":"WORKING"}""")
        ).andExpect(status().isCreated)

        val ids = profiles.require().skills.map { it.id }
        mvc.perform(
            put("/api/profile/skills/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${ids.reversed().joinToString(",")}]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.skills[0].id").value(ids.last()))
    }

    /** `/order` is a literal path that must win over the `{id}` template next to it. */
    @Test
    fun `a partial reorder is a 409 rather than a silent partial move`() {
        seed()
        val ids = profiles.require().skills.map { it.id }
        mvc.perform(
            put("/api/profile/skills/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${ids.first()},${ids.first()}]}""")
        )
            .andExpect(status().isConflict)
    }
}
