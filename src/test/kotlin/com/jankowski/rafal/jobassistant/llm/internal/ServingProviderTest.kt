package com.jankowski.rafal.jobassistant.llm.internal

import tools.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure logic, so it runs in the fast tier.
 *
 * The value matters because a router serves one model slug from providers whose capabilities
 * genuinely differ - for `minimax/minimax-m3`, three of eleven implemented structured outputs -
 * so without it two rows with identical `model_name` cannot be told apart when one honoured the
 * JSON schema and the other discarded it.
 */
class ServingProviderTest {

    private val json = JsonMapper.builder().build()

    private fun read(body: String?) = servingProviderIn(body, json)

    @Test
    fun `reads the provider a router reports`() {
        assertEquals(
            "CoreWeave",
            read("""{"id":"gen-1","provider":"CoreWeave","choices":[{"message":{"content":"{}"}}]}"""),
        )
    }

    @Test
    fun `a body without a provider field is not an error`() {
        assertNull(read("""{"id":"chatcmpl-1","choices":[{"message":{"content":"{}"}}]}"""))
    }

    /** Every non-router provider takes this path on every single call. */
    @Test
    fun `an absent body is not an error`() {
        assertNull(read(null))
        assertNull(read(""))
        assertNull(read("   "))
    }

    /**
     * Losing an audit row to a malformed response would discard the evidence the row exists to
     * keep, which is the opposite of what this column is for.
     */
    @Test
    fun `a malformed body is swallowed rather than thrown`() {
        assertNull(read("not json at all"))
        assertNull(read("""{"provider":"""))
    }

    @Test
    fun `a non-string or empty provider is treated as absent`() {
        assertNull(read("""{"provider":123}"""))
        assertNull(read("""{"provider":null}"""))
        assertNull(read("""{"provider":""}"""))
    }
}
