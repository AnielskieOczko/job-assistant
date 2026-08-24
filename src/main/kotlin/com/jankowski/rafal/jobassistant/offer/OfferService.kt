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
}
