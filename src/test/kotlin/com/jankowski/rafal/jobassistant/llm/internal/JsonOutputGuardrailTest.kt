package com.jankowski.rafal.jobassistant.llm.internal

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.guardrail.GuardrailResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * It used to reprompt, and the reprompt could not work: `OutputGuardrailExecutor` builds the
     * retry from a chat memory these services deliberately do not have, so the correction
     * instruction went out as the only message and the model answered a question it had never been
     * asked. Failing carries the reason to the caller; repromptng carried a fluent non-answer to
     * the user.
     */
    @Test
    fun `prose with no JSON at all fails the call rather than asking again with no question`() {
        val result = validate("I am unable to help with that request.")

        assertFalse(result.isSuccess(), "prose is not a parseable answer")
        assertFalse(result.isReprompt, "a reprompt here cannot see the original request")
        assertFalse(result.isRetry, "and a retry from this layer would be the same call again")
    }

    @Test
    fun `an unterminated object fails rather than returning junk`() {
        val result = validate("""{"skill":"Kotlin", "unfinished": """)

        assertFalse(result.isSuccess())
        assertFalse(result.isReprompt)
    }

    @Test
    fun `a response with no text at all fails, and says so in words a caller can render`() {
        val result = JsonOutputGuardrail().validate(AiMessage.builder().text(null).build())

        assertFalse(result.isSuccess())
        assertTrue(
            result.toString().contains("no text", ignoreCase = true),
            "the reason reaches an analysis row and a polish response; it has to name what happened",
        )
    }
}
