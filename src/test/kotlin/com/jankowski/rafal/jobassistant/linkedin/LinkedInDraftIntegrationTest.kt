package com.jankowski.rafal.jobassistant.linkedin

import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@IntegrationTest
internal class LinkedInDraftIntegrationTest {

    @Autowired lateinit var wac: WebApplicationContext
    @Autowired lateinit var models: ScriptedModels
    @Autowired lateinit var jdbc: JdbcClient

    private lateinit var mvc: MockMvc
    private var profileId: Long = 0

    @BeforeEach
    fun setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build()
        models.resetAll()
        jdbc.sql("delete from profile").update()

        profileId = jdbc.sql("insert into profile (name, is_default, revision) values ('Default', true, 3) returning id")
            .query(Long::class.java)
            .single()
        jdbc.sql(
            """
            insert into profile_details (profile_id, full_name, headline, email, summary, career_goal)
            values (:id, 'Jane Sentinel', 'Backend Engineer', 'jane@sentinel.example',
                    'Building reliable JVM services.', 'Staff Backend Engineer')
            """
        )
            .param("id", profileId)
            .update()
    }

    @Test
    fun `drafts LinkedIn section, flags unheld skills, and marks draft stale after profile revision bumps`() {
        models[LlmTask.LINKEDIN].enqueue(
            """{"content": "Backend Engineer focused on Kotlin, Spring Boot, and Kafka systems."}"""
        )

        mvc.perform(
            post("/api/profiles/$profileId/linkedin/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"section":"ABOUT"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.section").value("ABOUT"))
            .andExpect(jsonPath("$.profileRevision").value(3))
            .andExpect(jsonPath("$.stale").value(false))
            .andExpect(jsonPath("$.unheldSkills").isArray)
            .andExpect(jsonPath("$.content").value("Backend Engineer focused on Kotlin, Spring Boot, and Kafka systems."))

        jdbc.sql("update profile set revision = 4 where id = :id").param("id", profileId).update()

        mvc.perform(get("/api/profiles/$profileId/linkedin/drafts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].stale").value(true))
    }

    @Test
    fun `rejects EXPERIENCE_ENTRY draft without subjectId`() {
        mvc.perform(
            post("/api/profiles/$profileId/linkedin/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"section":"EXPERIENCE_ENTRY"}"""),
        )
            .andExpect(status().isBadRequest)
    }
}
