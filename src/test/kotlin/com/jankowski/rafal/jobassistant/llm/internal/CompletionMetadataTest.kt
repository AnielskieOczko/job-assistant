package com.jankowski.rafal.jobassistant.llm.internal

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure logic, so it runs in the fast tier - and it has to be tested here rather than through the
 * listener, because `ScriptedChatModel` builds a base `ChatResponse` with no raw HTTP response at
 * all. No integration test can reach this code path.
 *
 * What it reads matters for two separate reasons. The serving provider makes a bad result
 * attributable: a router serves one model slug from providers whose capabilities genuinely differ -
 * for `minimax/minimax-m3`, three of eleven implemented structured outputs - so without it two rows
 * with identical `model_name` cannot be told apart when one honoured the JSON schema and the other
 * discarded it. The cost is the only figure anywhere in the application that says what any of this
 * is being paid for.
 */
class CompletionMetadataTest {

    private val json = JsonMapper.builder().build()

    private fun read(body: String?) = completionMetadataIn(body, json)

    @Test
    fun `reads the provider a router reports`() {
        assertEquals(
            "CoreWeave",
            read("""{"id":"gen-1","provider":"CoreWeave","choices":[{"message":{"content":"{}"}}]}""")
                .servingProvider,
        )
    }

    @Test
    fun `a body without a provider field is not an error`() {
        assertNull(read("""{"id":"chatcmpl-1","choices":[{"message":{"content":"{}"}}]}""").servingProvider)
    }

    /** Every non-router provider takes this path on every single call. */
    @Test
    fun `an absent body is not an error`() {
        assertEquals(CompletionMetadata.NONE, read(null))
        assertEquals(CompletionMetadata.NONE, read(""))
        assertEquals(CompletionMetadata.NONE, read("   "))
    }

    /**
     * Losing an audit row to a malformed response would discard the evidence the row exists to
     * keep, which is the opposite of what these columns are for.
     */
    @Test
    fun `a malformed body is swallowed rather than thrown`() {
        assertEquals(CompletionMetadata.NONE, read("not json at all"))
        assertEquals(CompletionMetadata.NONE, read("""{"provider":"""))
    }

    @Test
    fun `a non-string or empty provider is treated as absent`() {
        assertNull(read("""{"provider":123}""").servingProvider)
        assertNull(read("""{"provider":null}""").servingProvider)
        assertNull(read("""{"provider":""}""").servingProvider)
    }

    @Test
    fun `reads the generation id, cost and token breakdown`() {
        val metadata = read(
            """
            {
              "id": "gen-1758",
              "provider": "Novita",
              "model": "minimax/minimax-m3",
              "usage": {
                "prompt_tokens": 2048,
                "completion_tokens": 512,
                "total_tokens": 2560,
                "prompt_tokens_details": {"cached_tokens": 1024},
                "completion_tokens_details": {"reasoning_tokens": 300},
                "cost": 0.00123456
              }
            }
            """
        )

        assertEquals("gen-1758", metadata.generationId)
        assertEquals("Novita", metadata.servingProvider)
        assertEquals(0, BigDecimal("0.00123456").compareTo(metadata.costUsd))
        assertEquals(1024, metadata.cachedInputTokens)
        assertEquals(300, metadata.reasoningOutputTokens)
    }

    /**
     * The exact shape a local model returns. Null is not zero: a total built from these has to be
     * able to say how many of its calls were priced at all, and a zero here would make an unpriced
     * call indistinguishable from a free one.
     */
    @Test
    fun `a usage block with no cost yields a null cost, not zero`() {
        val metadata = read(
            """{"id":"chatcmpl-9","usage":{"prompt_tokens":10,"completion_tokens":20}}"""
        )

        assertNull(metadata.costUsd)
        assertNull(metadata.cachedInputTokens)
        assertNull(metadata.reasoningOutputTokens)
        assertEquals("chatcmpl-9", metadata.generationId)
    }

    @Test
    fun `an explicitly null or non-numeric cost is treated as absent`() {
        assertNull(read("""{"usage":{"cost":null}}""").costUsd)
        assertNull(read("""{"usage":{"cost":"0.01"}}""").costUsd)
        assertNull(read("""{"usage":null}""").costUsd)
    }

    /** A free model reports a real zero, which is a different claim from reporting nothing. */
    @Test
    fun `a reported zero cost is kept as zero`() {
        assertEquals(0, BigDecimal.ZERO.compareTo(read("""{"usage":{"cost":0}}""").costUsd))
    }
}
