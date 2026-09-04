package com.jankowski.rafal.jobassistant.document

import com.jankowski.rafal.jobassistant.offer.Application

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

    /** Every document generated for [profileId], across every offer, newest first. */
    fun library(profileId: Long): List<DocumentLibraryEntry>

    /**
     * Copies [sourceDocumentId]'s CV onto [targetOfferId] as [profileId]'s document for that offer.
     * No model call and no regeneration - the HTML is byte-identical to the source - which is the
     * whole point: two similar offers should not cost two generations and two reviews.
     *
     * The copy is still re-checked against the fabrication guard before it is stored: the text is
     * unchanged, but the profile might not be, and a skill deleted since the original generation
     * would otherwise carry a stale, now-fabricated claim onto a second offer.
     *
     * The new row carries the source's drop counts and profile revision rather than fresh ones -
     * nothing was regenerated, so there is nothing new to measure. `sourceDocumentId` on the result
     * is what attributes them back to the generation that actually produced them.
     *
     * @throws FabricatedClaimException if [profileId] no longer holds a skill the document mentions.
     * @throws IllegalArgumentException if [sourceDocumentId] does not name a CV.
     * @throws NoSuchElementException if [sourceDocumentId] or [targetOfferId] does not exist.
     */
    fun reuse(targetOfferId: Long, profileId: Long, sourceDocumentId: Long): GeneratedDocument

    /**
     * Records that this document is the one actually sent for its offer, replacing whatever was
     * marked for its type before. The returned [Application] is the offer's lifecycle row, which is
     * where the link lives - a document may exist and never be sent, so the fact belongs to the
     * application rather than to the document.
     *
     * Marking is optional and does not move the application's status: an application can be APPLIED
     * with documents sent by hand, and a document can be sent before the status is updated.
     *
     * @throws NoSuchElementException if no such document exists, or if it belongs to another offer.
     */
    fun markSent(offerId: Long, documentId: Long): Application

    /**
     * Clears the sent record for [type] on this offer, so a mis-click is not permanent. Unmarking
     * something that was never marked is a no-op rather than an error - the result is the state the
     * caller asked for either way.
     */
    fun unmarkSent(offerId: Long, type: DocumentType): Application
}
