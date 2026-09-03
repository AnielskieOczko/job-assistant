package com.jankowski.rafal.jobassistant.document

import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The record of which document was actually sent for an application.
 *
 * Documents are inserted directly rather than generated, deliberately: generation is covered by
 * [DocumentGenerationIntegrationTest] and needs a scripted model and a completed analysis, while
 * every rule here is about the link. What matters is that the row exists, carries a type and names
 * an offer.
 */
@IntegrationTest
internal class SentDocumentIntegrationTest(
    @Autowired private val documents: DocumentService,
    @Autowired private val offers: OfferService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val jdbc: JdbcClient,
) {

    private var offerId = 0L
    private var otherOfferId = 0L
    private var profileId = 0L

    @BeforeEach
    fun setUp() {
        jdbc.sql("delete from job_offer").update()
        jdbc.sql("delete from profile").update()

        profileId = management.create("Test").id
        offerId = offers.paste("Senior Kotlin Engineer at Acme.").offer.id
        otherOfferId = offers.paste("Staff Java Engineer at Globex.").offer.id
    }

    private fun storeDocument(type: DocumentType, offer: Long = offerId): Long =
        jdbc.sql(
            """
            insert into generated_document (job_offer_id, profile_id, type, language, html, selection_json)
            values (:offerId, :profileId, :type, 'English', '<html></html>', '{}')
            returning id
            """.trimIndent()
        )
            .param("offerId", offer)
            .param("profileId", profileId)
            .param("type", type.name)
            .query(Long::class.java)
            .single()

    private fun application() = assertNotNull(offers.applicationFor(offerId))

    @Test
    fun `marking a document as sent records it against the application`() {
        val cv = storeDocument(DocumentType.CV)

        val application = documents.markSent(offerId, cv)

        assertEquals(cv, application.sentCvDocumentId)
        assertEquals(cv, application().sentCvDocumentId)
        assertNull(application.sentCoverLetterDocumentId)
    }

    @Test
    fun `a CV and a cover letter occupy separate slots`() {
        val cv = storeDocument(DocumentType.CV)
        val letter = storeDocument(DocumentType.COVER_LETTER)

        documents.markSent(offerId, cv)
        val application = documents.markSent(offerId, letter)

        assertEquals(cv, application.sentCvDocumentId)
        assertEquals(letter, application.sentCoverLetterDocumentId)
    }

    /** An application sent one CV, so the field is a correction rather than a history. */
    @Test
    fun `re-marking replaces the CV that was recorded before`() {
        val first = storeDocument(DocumentType.CV)
        val second = storeDocument(DocumentType.CV)

        documents.markSent(offerId, first)
        val application = documents.markSent(offerId, second)

        assertEquals(second, application.sentCvDocumentId)
    }

    @Test
    fun `unmarking clears the record so a mis-click is not permanent`() {
        val cv = storeDocument(DocumentType.CV)
        documents.markSent(offerId, cv)

        val application = documents.unmarkSent(offerId, DocumentType.CV)

        assertNull(application.sentCvDocumentId)
        assertNull(application().sentCvDocumentId)
    }

    @Test
    fun `unmarking something that was never marked is not an error`() {
        assertNull(documents.unmarkSent(offerId, DocumentType.COVER_LETTER).sentCoverLetterDocumentId)
    }

    /**
     * The two facts are independent in both directions: sending a document is not applying, and
     * applying does not claim a document was sent. Deriving either from the other would fabricate
     * a record in the table outcome calibration will eventually read.
     */
    @Test
    fun `marking a document sent leaves the status alone, and a status change leaves the link alone`() {
        val cv = storeDocument(DocumentType.CV)
        val saved = application()

        val marked = documents.markSent(offerId, cv)

        assertEquals(ApplicationStatus.SAVED, marked.status)
        assertEquals(saved.statusChangedAt, marked.statusChangedAt)

        val applied = offers.updateStatus(offerId, ApplicationStatus.APPLIED)

        assertEquals(cv, applied.sentCvDocumentId)
    }

    @Test
    fun `a document belonging to another offer cannot be recorded against this one`() {
        val theirs = storeDocument(DocumentType.CV, offer = otherOfferId)

        assertThrows<NoSuchElementException> { documents.markSent(offerId, theirs) }

        assertNull(application().sentCvDocumentId)
    }

    @Test
    fun `an unknown document cannot be marked as sent`() {
        assertThrows<NoSuchElementException> { documents.markSent(offerId, 999_999) }
    }

    /**
     * The foreign key is the point: an id naming a document that is gone can no longer be opened,
     * so the link drops rather than dangling. This is the opposite choice from `llm_call`'s
     * subject_id, which carries no key precisely so cost history outlives what it paid for.
     */
    @Test
    fun `deleting the document clears the link instead of leaving it dangling`() {
        val cv = storeDocument(DocumentType.CV)
        documents.markSent(offerId, cv)

        jdbc.sql("delete from generated_document where id = :id").param("id", cv).update()

        assertNull(application().sentCvDocumentId)
    }
}
