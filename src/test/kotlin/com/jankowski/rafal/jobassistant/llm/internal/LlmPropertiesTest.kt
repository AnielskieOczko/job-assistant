package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmPropertiesTest {

    private val openrouter = ModelProfile(
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "key",
        model = "some/model",
    )

    @Test
    fun `routes a task to its configured profile`() {
        val properties = LlmProperties(
            profiles = mapOf("openrouter" to openrouter),
            tasks = mapOf(LlmTask.EXTRACTION to "openrouter"),
        )

        assertEquals("openrouter", properties.profileNameFor(LlmTask.EXTRACTION))
        assertEquals(openrouter, properties.profileFor(LlmTask.EXTRACTION))
    }

    @Test
    fun `an unrouted task fails with the property to set`() {
        val properties = LlmProperties(profiles = mapOf("openrouter" to openrouter))

        val failure = assertThrows<IllegalStateException> { properties.profileFor(LlmTask.NARRATIVE) }

        assertTrue(failure.message!!.contains("job-assistant.llm.tasks.narrative"))
    }

    @Test
    fun `a task pointing at an undefined profile names the known profiles`() {
        val properties = LlmProperties(
            profiles = mapOf("openrouter" to openrouter),
            tasks = mapOf(LlmTask.DOCUMENT to "typo-profile"),
        )

        val failure = assertThrows<IllegalStateException> { properties.profileFor(LlmTask.DOCUMENT) }

        assertTrue(failure.message!!.contains("typo-profile"))
        assertTrue(failure.message!!.contains("openrouter"))
    }

    @Test
    fun `body logging is off by default`() {
        assertFalse(openrouter.logRequests)
        assertFalse(openrouter.logResponses)
    }

    /**
     * Body logging writes whole prompts to the console, and no logback config filters anything, so
     * enabling it would put profile data in the logs and defeat the outbound guard entirely.
     * Asserted against the shipped file rather than a bound object because the risk being guarded
     * against is precisely someone editing that file.
     */
    @Test
    fun `no shipped model profile enables body logging`() {
        val yaml = checkNotNull(javaClass.getResource("/application.yaml")) {
            "application.yaml is not on the test classpath"
        }.readText()

        val enabled = Regex("""log-(?:requests|responses)\s*:\s*(\S+)""")
            .findAll(yaml)
            .map { it.groupValues[1] }
            .filter { it != "false" }
            .toList()

        assertTrue(enabled.isEmpty(), "body logging is enabled in application.yaml: $enabled")
    }
}
