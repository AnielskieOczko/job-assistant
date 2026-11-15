package com.jankowski.rafal.jobassistant.career

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@IntegrationTest
internal class CareerPlanIntegrationTest {

    @Autowired lateinit var wac: WebApplicationContext
    @Autowired lateinit var models: ScriptedModels
    @Autowired lateinit var catalog: SkillCatalog
    @Autowired lateinit var profiles: ProfileService
    @Autowired lateinit var jdbc: JdbcClient

    private lateinit var mvc: MockMvc
    private var profileId: Long = 0

    @BeforeEach
    fun setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build()
        models.resetAll()
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from profile").update()

        profileId = jdbc.sql("insert into profile (name, is_default) values ('Default', true) returning id")
            .query(Long::class.java)
            .single()
        jdbc.sql("insert into profile_details (profile_id, full_name) values (:id, 'Jane Doe')")
            .param("id", profileId)
            .update()

        insertOffer("offer-1", "Senior", listOf("Java", "Kafka"))
        insertOffer("offer-2", "Senior", listOf("Java", "Kafka", "Docker"))
    }

    @Test
    fun `generates immutable evidence-bounded career plan and persists skill progress across regenerations`() {
        val kafka = catalog.resolve("Kafka")!!

        models[LlmTask.CAREER_PLAN].enqueue(
            """
            {
              "topics": [
                {
                  "skill": "Kafka",
                  "priority": 1,
                  "why": "Asked for by both in-scope Senior offers.",
                  "practiceProject": "Build a transactional outbox consumer",
                  "effortEstimate": "2 weeks"
                },
                {
                  "skill": "InventedMagic",
                  "priority": 2,
                  "why": "Unbacked",
                  "practiceProject": null,
                  "effortEstimate": null
                }
              ]
            }
            """.trimIndent()
        )

        mvc.perform(post("/api/career/plans").param("profileId", profileId.toString()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.matchedOfferCount").value(2))
            .andExpect(jsonPath("$.corpusOfferCount").value(2))
            .andExpect(jsonPath("$.droppedTopicCount").value(1))
            .andExpect(jsonPath("$.topics.length()").value(1))
            .andExpect(jsonPath("$.topics[0].skillName").value(kafka.name))
            .andExpect(jsonPath("$.topics[0].targetLevel").value("SENIOR"))
            .andExpect(jsonPath("$.topics[0].progressState").value("NOT_STARTED"))

        mvc.perform(
            put("/api/career/progress/${kafka.id}")
                .param("profileId", profileId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"state":"READY_TO_DECLARE"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("READY_TO_DECLARE"))

        // Progress never writes to profile_skill automatically.
        assertThat(profiles.require(profileId).heldSkillIds).doesNotContain(kafka.id)

        // Regenerate and verify older plan remains in history and progress survives.
        models[LlmTask.CAREER_PLAN].enqueue(
            """
            {
              "topics": [
                {
                  "skill": "Kafka",
                  "priority": 1,
                  "why": "Still top gap.",
                  "practiceProject": null,
                  "effortEstimate": "1 week"
                }
              ]
            }
            """.trimIndent()
        )

        mvc.perform(post("/api/career/plans").param("profileId", profileId.toString()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.droppedTopicCount").value(0))
            .andExpect(jsonPath("$.topics[0].progressState").value("READY_TO_DECLARE"))

        mvc.perform(get("/api/career/plans").param("profileId", profileId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))

        mvc.perform(get("/api/career/plans/current").param("profileId", profileId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topics[0].effortEstimate").value("1 week"))
    }

    private fun insertOffer(key: String, seniority: String, skillNames: List<String>) {
        val id = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, experience_level, payload, valid_to)
            values ('solid.jobs', :key, 'Backend Engineer', :seniority, cast('{}' as jsonb), now() + interval '1 day')
            returning id
            """
        )
            .param("key", key)
            .param("seniority", seniority)
            .query { rs, _ -> rs.getLong("id") }
            .single()

        skillNames.forEach { name ->
            val skill = catalog.resolve(name)!!
            jdbc.sql(
                """
                insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
                values (:offerId, :name, 'ADVANCED', :skillId)
                """
            )
                .param("offerId", id)
                .param("name", name)
                .param("skillId", skill.id)
                .update()
        }
    }
}
