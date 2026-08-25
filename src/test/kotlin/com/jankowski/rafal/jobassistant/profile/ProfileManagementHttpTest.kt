package com.jankowski.rafal.jobassistant.profile

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

/**
 * Creating, listing, deleting and defaulting profiles themselves - not their contents.
 *
 * `ProfileCrudHttpTest` covers editing what is inside one profile; this covers deciding which
 * profiles exist at all, including the exactly-one-default invariant the roadmap calls out.
 */
@IntegrationTest
class ProfileManagementHttpTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val jdbc: JdbcClient,
) {

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun clearProfiles() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbc.sql("delete from profile").update()
    }

    private fun create(name: String): Long {
        val body = mvc.perform(
            post("/api/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name"}""")
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return Regex(""""id":(\d+)""").find(body)!!.groupValues[1].toLong()
    }

    @Test
    fun `an empty list is 200 with no profiles`() {
        mvc.perform(get("/api/profiles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `the first profile created becomes the default`() {
        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Java developer"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Java developer"))
            .andExpect(jsonPath("$.isDefault").value(true))

        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Cloud consultant"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.isDefault").value(false))
    }

    @Test
    fun `a blank name is a 400`() {
        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON).content("""{"name":"  "}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid profile"))
    }

    @Test
    fun `setting a different profile as default swaps the flag`() {
        val first = create("Java developer")
        val second = create("Cloud consultant")

        mvc.perform(put("/api/profiles/$second/default"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isDefault").value(true))

        mvc.perform(get("/api/profiles"))
            .andExpect(jsonPath("$[?(@.id == $first)].isDefault").value(false))
            .andExpect(jsonPath("$[?(@.id == $second)].isDefault").value(true))
    }

    @Test
    fun `deleting a non-default profile succeeds`() {
        create("Java developer")
        val second = create("Cloud consultant")

        mvc.perform(delete("/api/profiles/$second")).andExpect(status().isNoContent)

        mvc.perform(get("/api/profiles")).andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `deleting the default profile while another exists is a 409`() {
        val first = create("Java developer")
        create("Cloud consultant")

        mvc.perform(delete("/api/profiles/$first"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Profile deletion rejected"))
    }

    @Test
    fun `deleting the last profile is allowed even though it is the default`() {
        val only = create("Java developer")

        mvc.perform(delete("/api/profiles/$only")).andExpect(status().isNoContent)
        mvc.perform(get("/api/profiles")).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `deleting an unknown profile is a 404`() {
        mvc.perform(delete("/api/profiles/99999")).andExpect(status().isNotFound)
    }
}
