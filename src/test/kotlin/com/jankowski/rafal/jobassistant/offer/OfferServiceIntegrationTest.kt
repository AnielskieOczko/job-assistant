package com.jankowski.rafal.jobassistant.offer

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class OfferServiceIntegrationTest(
    @Autowired private val offers: OfferService,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun clear() {
        jdbc.sql("delete from job_offer").update()
    }

    private val offerText = """
        Senior Kotlin Engineer
        We are looking for someone with Spring Boot and PostgreSQL experience.
    """.trimIndent()

    @Test
    fun `pasting an offer stores it with its raw text`() {
        val pasted = offers.paste(offerText, sourceUrl = "https://example.com/job/1")

        assertFalse(pasted.deduplicated)
        assertEquals(offerText, pasted.offer.rawText)
        assertEquals("https://example.com/job/1", pasted.offer.sourceUrl)
    }

    @Test
    fun `a new offer starts life in SAVED`() {
        val pasted = offers.paste(offerText)

        assertEquals(ApplicationStatus.SAVED, assertNotNull(offers.applicationFor(pasted.offer.id)).status)
    }

    @Test
    fun `re-pasting the same offer returns the original instead of duplicating`() {
        val first = offers.paste(offerText)
        val second = offers.paste(offerText)

        assertTrue(second.deduplicated)
        assertEquals(first.offer.id, second.offer.id)
        assertEquals(1, offers.list().size)
    }

    @Test
    fun `re-pasting with different whitespace still deduplicates`() {
        val first = offers.paste(offerText)
        val second = offers.paste("  $offerText\n\n ")

        assertTrue(second.deduplicated)
        assertEquals(first.offer.id, second.offer.id)
    }

    @Test
    fun `blank text is rejected`() {
        assertThrows<IllegalArgumentException> { offers.paste("   ") }
    }

    @Test
    fun `extraction metadata is recorded onto the offer`() {
        val pasted = offers.paste(offerText)

        val described = offers.describe(
            offerId = pasted.offer.id,
            title = "Senior Kotlin Engineer",
            company = "Acme",
            seniority = "SENIOR",
            detectedLanguage = "en",
        )

        assertEquals("Senior Kotlin Engineer", described.title)
        assertEquals("Acme", described.company)
        assertEquals("en", described.detectedLanguage)
    }

    @Test
    fun `describe leaves existing values alone when passed nulls`() {
        val pasted = offers.paste(offerText)
        offers.describe(pasted.offer.id, "Original Title", "Acme", null, "en")

        val second = offers.describe(pasted.offer.id, null, null, "SENIOR", null)

        assertEquals("Original Title", second.title)
        assertEquals("Acme", second.company)
        assertEquals("SENIOR", second.seniority)
    }

    @Test
    fun `display title falls back to the first line before extraction runs`() {
        val pasted = offers.paste(offerText)

        assertEquals("Senior Kotlin Engineer", pasted.offer.displayTitle)
    }

    @Test
    fun `status moves through the lifecycle and records when it changed`() {
        val pasted = offers.paste(offerText)

        val analyzed = offers.updateStatus(pasted.offer.id, ApplicationStatus.ANALYZED)
        val applied = offers.updateStatus(
            pasted.offer.id,
            ApplicationStatus.APPLIED,
            appliedOn = LocalDate.of(2026, 8, 23),
            notes = "Referred by a friend",
        )

        assertEquals(ApplicationStatus.APPLIED, applied.status)
        assertEquals(LocalDate.of(2026, 8, 23), applied.appliedOn)
        assertEquals("Referred by a friend", applied.notes)
        assertTrue(applied.statusChangedAt.isAfter(analyzed.statusChangedAt) || applied.statusChangedAt == analyzed.statusChangedAt)
    }

    @Test
    fun `editing notes without a status change does not restamp the change time`() {
        val pasted = offers.paste(offerText)
        val applied = offers.updateStatus(pasted.offer.id, ApplicationStatus.APPLIED)

        val renoted = offers.updateStatus(pasted.offer.id, ApplicationStatus.APPLIED, notes = "Follow up Monday")

        assertEquals(applied.statusChangedAt, renoted.statusChangedAt)
        assertEquals("Follow up Monday", renoted.notes)
    }

    @Test
    fun `updating an unknown offer fails loudly`() {
        assertThrows<NoSuchElementException> {
            offers.updateStatus(999_999, ApplicationStatus.APPLIED)
        }
    }

    @Test
    fun `listing pairs each offer with its application, newest first`() {
        offers.paste("First offer text")
        offers.paste("Second offer text")

        val listed = offers.list()

        assertEquals(2, listed.size)
        assertEquals("Second offer text", listed.first().offer.rawText)
        assertTrue(listed.all { it.application.status == ApplicationStatus.SAVED })
    }

    @Test
    fun `an unknown id is absent rather than an error`() {
        assertNull(offers.findById(999_999))
    }
}
