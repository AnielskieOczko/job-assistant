package com.jankowski.rafal.jobassistant.privacy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Offer text is the input to requirement extraction, so the scrubber has to remove contact details
 * without disturbing anything the extractor reads. The negative cases carry the weight here.
 */
class OfferTextScrubberTest {

    @Test
    fun `a recruiter email is removed`() {
        val scrubbed = OfferTextScrubber.scrub("Send your CV to anna.kowalska@recruiter.example.com today.")
        assertFalse(scrubbed.contains("anna.kowalska"))
        assertTrue(scrubbed.contains("[email removed]"))
    }

    @Test
    fun `an international phone number is removed`() {
        val scrubbed = OfferTextScrubber.scrub("Questions? Call +48 555 123 456.")
        assertFalse(scrubbed.any(Char::isDigit))
        assertTrue(scrubbed.contains("[phone removed]"))
    }

    @Test
    fun `a grouped nine digit phone number is removed`() {
        assertTrue(OfferTextScrubber.scrub("tel. 555-123-456").contains("[phone removed]"))
    }

    @Test
    fun `requirements are left untouched`() {
        val offer = "We need Kotlin 2.x, Spring Boot 4, Java 21 and 5+ years of backend experience."
        assertEquals(offer, OfferTextScrubber.scrub(offer))
    }

    @Test
    fun `a salary range survives`() {
        // An earlier, looser pattern matched any three punctuated digit groups and ate this.
        val offer = "Budget is 8 000 - 12 000 PLN net per month."
        assertEquals(offer, OfferTextScrubber.scrub(offer))
    }

    @Test
    fun `a date survives`() {
        val offer = "Start date 2021.01.15, contract until 2023."
        assertEquals(offer, OfferTextScrubber.scrub(offer))
    }

    @Test
    fun `a version number survives`() {
        val offer = "Requires PostgreSQL 16.2 and Node 22.11.0 on the team."
        assertEquals(offer, OfferTextScrubber.scrub(offer))
    }

    @Test
    fun `empty text is handled`() {
        assertEquals("", OfferTextScrubber.scrub(""))
    }
}
