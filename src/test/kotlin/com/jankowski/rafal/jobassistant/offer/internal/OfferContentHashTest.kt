package com.jankowski.rafal.jobassistant.offer.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OfferContentHashTest {

    @Test
    fun `identical text hashes identically`() {
        assertEquals(OfferContentHash.of("Senior Kotlin Dev"), OfferContentHash.of("Senior Kotlin Dev"))
    }

    @Test
    fun `incidental whitespace from copy-pasting is ignored`() {
        val once = OfferContentHash.of("Senior Kotlin Developer\nWe need Spring Boot.")
        val again = OfferContentHash.of("  Senior Kotlin Developer\r\n\r\n   We need Spring Boot.  ")

        assertEquals(once, again)
    }

    @Test
    fun `non-breaking spaces pasted from a browser do not defeat dedup`() {
        assertEquals(
            OfferContentHash.of("Kotlin Developer wanted"),
            OfferContentHash.of("Kotlin Developer wanted"),
        )
    }

    @Test
    fun `different postings hash differently`() {
        assertNotEquals(OfferContentHash.of("Kotlin role"), OfferContentHash.of("Java role"))
    }

    @Test
    fun `case is preserved so genuinely different offers never merge`() {
        assertNotEquals(OfferContentHash.of("kotlin role"), OfferContentHash.of("Kotlin Role"))
    }

    @Test
    fun `produces a fixed length hex digest`() {
        assertEquals(64, OfferContentHash.of("anything").length)
    }
}
