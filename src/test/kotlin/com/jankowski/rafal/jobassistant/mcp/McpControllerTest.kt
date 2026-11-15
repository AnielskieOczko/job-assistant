package com.jankowski.rafal.jobassistant.mcp

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@IntegrationTest
internal class McpControllerTest {

    @Autowired lateinit var wac: WebApplicationContext
    @Autowired lateinit var jdbc: JdbcClient

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build()
        jdbc.sql("delete from profile").update()
        jdbc.sql("insert into profile (name, is_default) values ('Default', true)").update()
    }

    @Test
    fun `handles MCP initialize, tools list, and tool calls`() {
        mvc.perform(
            post("/api/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.serverInfo.name").value("job-assistant"))

        mvc.perform(
            post("/api/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.tools.length()").value(5))

        mvc.perform(
            post("/api/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_target_role","arguments":{}}}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.isError").value(false))
    }
}
