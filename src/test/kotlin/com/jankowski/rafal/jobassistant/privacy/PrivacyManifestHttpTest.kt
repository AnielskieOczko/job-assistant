package com.jankowski.rafal.jobassistant.privacy

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * The manifest is profile-independent, so the case worth pinning is the one every other
 * profile-scoped endpoint has to special-case: no persona at all.
 */
@IntegrationTest
class PrivacyManifestHttpTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val jdbc: JdbcClient,
) {

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun clearProfiles() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbc.sql("delete from profile").update()
    }

    @Test
    fun `the manifest is served with no profile present`() {
        mvc.perform(get("/api/privacy/manifest"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fields").isArray)
            .andExpect(jsonPath("$.fields[?(@.name == 'fullName')].state").value("ENFORCED"))
            .andExpect(jsonPath("$.fields[?(@.name == 'location')].state").value("OMITTED"))
            .andExpect(jsonPath("$.fields[?(@.name == 'skills')].state").value("SENT"))
            .andExpect(jsonPath("$.offerScrubbing").isNotEmpty)
    }
}
