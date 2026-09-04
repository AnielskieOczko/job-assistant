package com.jankowski.rafal.jobassistant.offer

import java.time.Instant
import java.time.LocalDate

data class JobOffer(
    val id: Long,
    val contentHash: String,
    val rawText: String,
    val sourceUrl: String? = null,
    val title: String? = null,
    val company: String? = null,
    val seniority: String? = null,
    val detectedLanguage: String? = null,
    val createdAt: Instant,
) {
    /** A short label for listings, before extraction has supplied a real title. */
    val displayTitle: String
        get() = title ?: rawText.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "Untitled offer"
}

enum class ApplicationStatus { SAVED, ANALYZED, APPLIED, INTERVIEWING, REJECTED, OFFER }

data class Application(
    val id: Long,
    val offerId: Long,
    val status: ApplicationStatus,
    val statusChangedAt: Instant,
    val appliedOn: LocalDate? = null,
    val notes: String? = null,
    /**
     * The generated CV that was actually sent, and the cover letter that went with it. Null is the
     * normal state: a document may be generated and never sent, and an application made outside the
     * tool has no document to name at all.
     *
     * These are ids rather than documents because `offer` knows nothing about the `document`
     * module - the dependency runs the other way, and it is `document` that checks an id names a
     * real document of the right type belonging to this offer before recording it here.
     *
     * Deliberately independent of [status]: sending a document does not move an application to
     * APPLIED, and marking it APPLIED does not claim a document was sent. Deriving either from the
     * other would fabricate a record.
     */
    val sentCvDocumentId: Long? = null,
    val sentCoverLetterDocumentId: Long? = null,
)

data class OfferSummary(val offer: JobOffer, val application: Application)

/**
 * Result of pasting offer text. [deduplicated] is true when the text matched an offer already
 * stored, in which case [offer] is the existing one and nothing new was created.
 */
data class PastedOffer(val offer: JobOffer, val deduplicated: Boolean)
