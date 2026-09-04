package com.jankowski.rafal.jobassistant.offer

import java.time.LocalDate

interface OfferService {

    /**
     * Stores pasted offer text, or returns the existing offer when the same text has been pasted
     * before. Matching is on normalised content, so incidental whitespace differences from
     * copy-pasting the same posting twice still deduplicate.
     */
    fun paste(rawText: String, sourceUrl: String? = null): PastedOffer

    fun findById(id: Long): JobOffer?

    fun list(): List<OfferSummary>

    fun applicationFor(offerId: Long): Application?

    /**
     * Records the metadata extraction discovered. Called by the analysis module - the offer module
     * never runs a model itself.
     */
    fun describe(
        offerId: Long,
        title: String?,
        company: String?,
        seniority: String?,
        detectedLanguage: String?,
    ): JobOffer

    fun updateStatus(
        offerId: Long,
        status: ApplicationStatus,
        appliedOn: LocalDate? = null,
        notes: String? = null,
    ): Application

    /**
     * Records which generated CV was sent for this offer, or clears the record when [documentId] is
     * null. Re-marking replaces the previous link: an application sent one CV, so the field is a
     * correction rather than a history.
     *
     * The offer module does not check that [documentId] names a real document - it cannot, because
     * `document` depends on `offer` and not the other way round. The caller in `document` resolves
     * the id, confirms it belongs to this offer and dispatches on its type before calling.
     *
     * Deliberately does not touch [ApplicationStatus] or `statusChangedAt`: what you sent and where
     * the application has got to are separate facts.
     */
    fun markCvSent(offerId: Long, documentId: Long?): Application

    /** The cover letter half of [markCvSent], with the same rules. */
    fun markCoverLetterSent(offerId: Long, documentId: Long?): Application
}
