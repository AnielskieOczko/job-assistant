package com.jankowski.rafal.jobassistant.llm.internal

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.guardrail.GuardrailResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure logic, no model and no database. This is the repair step that keeps a fenced or chatty
 * response from failing an otherwise good analysis.
 */
class JsonOutputGuardrailTest {

    private val guardrail = JsonOutputGuardrail()

    private fun validate(text: String) = guardrail.validate(AiMessage.from(text))

    @Test
    fun `clean JSON passes through untouched`() {
        val result = validate("""{"skill":"Kotlin"}""")

        assertEquals(GuardrailResult.Result.SUCCESS, result.result())
        assertTrue(result.getReprompt().isEmpty)
    }

    @Test
    fun `markdown fenced JSON is repaired without another model call`() {
        val result = validate("```json\n{\"skill\":\"Kotlin\"}\n```")

        assertEquals(GuardrailResult.Result.SUCCESS_WITH_RESULT, result.result())
        assertEquals("""{"skill":"Kotlin"}""", result.successfulText())
    }

    @Test
    fun `commentary around the JSON is stripped`() {
        val result = validate("Sure! Here is the result:\n{\"skill\":\"Kotlin\"}\nHope that helps.")

        assertEquals("""{"skill":"Kotlin"}""", result.successfulText())
    }

    @Test
    fun `a JSON array is extracted just like an object`() {
        val result = validate("```\n[{\"a\":1},{\"b\":2}]\n```")

        assertEquals("""[{"a":1},{"b":2}]""", result.successfulText())
    }

    @Test
    fun `a brace inside a string value does not end the scan early`() {
        val payload = """{"text":"a } brace and a \" quote","n":1}"""

        val result = validate("noise $payload trailing")

        assertEquals(payload, result.successfulText())
    }

    @Test
    fun `nested objects are captured whole`() {
        val payload = """{"outer":{"inner":{"deep":true}},"after":1}"""

        assertEquals(payload, validate("```json\n$payload\n```").successfulText())
    }

    @Test
    fun `prose with no JSON at all triggers a reprompt`() {
        val result = validate("I am unable to help with that request.")

        assertTrue(result.isReprompt, "expected a reprompt, got ${result.result()}")
        assertTrue(result.getReprompt().isPresent)
    }

    @Test
    fun `an unterminated object triggers a reprompt rather than returning junk`() {
        val result = validate("""{"skill":"Kotlin", "unfinished": """)

        assertTrue(result.isReprompt)
    }
}
