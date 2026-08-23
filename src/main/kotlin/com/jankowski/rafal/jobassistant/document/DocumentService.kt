package com.jankowski.rafal.jobassistant.document

interface DocumentService {

    /**
     * Generates a CV or cover letter tailored to an offer's analysis.
     *
     * @throws FabricatedClaimException if the result would mention a skill the profile lacks.
     * @throws IllegalStateException if the offer has no completed analysis to tailor against.
     */
    fun generate(offerId: Long, type: DocumentType, language: String = "English"): GeneratedDocument

    fun findById(documentId: Long): GeneratedDocument?

    fun latest(offerId: Long, type: DocumentType): GeneratedDocument?

    /** Renders a stored document's HTML to PDF. Requires Chromium. */
    fun renderPdf(documentId: Long): ByteArray
}
