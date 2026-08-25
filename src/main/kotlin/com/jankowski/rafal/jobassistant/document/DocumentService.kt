package com.jankowski.rafal.jobassistant.document

interface DocumentService {

    /**
     * Generates a CV or cover letter tailored to an offer's analysis of the given profile.
     *
     * @throws FabricatedClaimException if the result would mention a skill the profile lacks.
     * @throws IllegalStateException if the offer has no completed analysis of this profile to
     *   tailor against.
     */
    fun generate(offerId: Long, profileId: Long, type: DocumentType, language: String = "English"): GeneratedDocument

    fun findById(documentId: Long): GeneratedDocument?

    /** Falls back to the default profile when [profileId] is not given. */
    fun latest(offerId: Long, type: DocumentType, profileId: Long? = null): GeneratedDocument?

    /** Renders a stored document's HTML to PDF. Requires Chromium. */
    fun renderPdf(documentId: Long): ByteArray
}
