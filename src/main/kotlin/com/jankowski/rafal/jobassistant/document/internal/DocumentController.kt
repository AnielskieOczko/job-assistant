package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.document.DocumentLibraryEntry
import com.jankowski.rafal.jobassistant.document.DocumentService
import com.jankowski.rafal.jobassistant.document.DocumentType
import com.jankowski.rafal.jobassistant.document.FabricatedClaimException
import com.jankowski.rafal.jobassistant.document.GeneratedDocument
import com.jankowski.rafal.jobassistant.offer.Application
import com.jankowski.rafal.jobassistant.privacy.SensitiveDataInPromptException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
internal class DocumentController(private val documents: DocumentService) {

    /** `profileId` is required - see the equivalent note on `AnalysisController.start`. */
    @PostMapping("/api/offers/{offerId}/documents")
    fun generate(
        @PathVariable offerId: Long,
        @RequestParam profileId: Long,
        @RequestParam type: DocumentType,
        @RequestParam(defaultValue = "English") language: String,
    ): GeneratedDocument = documents.generate(offerId, profileId, type, language)

    /** Defaults to the default profile when `profileId` is omitted - this endpoint is deep-linkable. */
    @GetMapping("/api/offers/{offerId}/documents/latest")
    fun latest(
        @PathVariable offerId: Long,
        @RequestParam type: DocumentType,
        @RequestParam(required = false) profileId: Long?,
    ): ResponseEntity<GeneratedDocument> =
        documents.latest(offerId, type, profileId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** The cross-offer library: every document generated for this profile, newest first. */
    @GetMapping("/api/documents")
    fun library(@RequestParam profileId: Long): List<DocumentLibraryEntry> = documents.library(profileId)

    /**
     * Attaches an existing CV to a second offer as a copy, with no model call. `sourceDocumentId`
     * is the CV being reused; `profileId` is whose skills the fabrication guard re-checks it
     * against, since the HTML itself is not regenerated.
     */
    @PostMapping("/api/offers/{offerId}/documents/reuse")
    fun reuse(
        @PathVariable offerId: Long,
        @RequestParam profileId: Long,
        @RequestParam sourceDocumentId: Long,
    ): GeneratedDocument = documents.reuse(offerId, profileId, sourceDocumentId)

    /** The browser preview. Byte-for-byte the same markup Chromium turns into the PDF. */
    @GetMapping("/api/documents/{documentId}/html", produces = [MediaType.TEXT_HTML_VALUE])
    fun html(@PathVariable documentId: Long): ResponseEntity<String> =
        documents.findById(documentId)?.let { ResponseEntity.ok(it.html) } ?: ResponseEntity.notFound().build()

    @GetMapping("/api/documents/{documentId}/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun pdf(@PathVariable documentId: Long): ResponseEntity<ByteArray> {
        val document = documents.findById(documentId) ?: return ResponseEntity.notFound().build()
        val filename = "${document.type.name.lowercase()}-${document.id}.pdf"

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(filename).build().toString(),
            )
            .body(documents.renderPdf(documentId))
    }

    /**
     * Records this document as the one sent for the offer, and answers with the *application* -
     * the link is a fact about the application, not about the document, so that is the row a client
     * has to re-read. `PUT` because marking the same document twice is the same state, not a second
     * send.
     */
    @PutMapping("/api/offers/{offerId}/documents/{documentId}/sent")
    fun markSent(@PathVariable offerId: Long, @PathVariable documentId: Long): Application =
        documents.markSent(offerId, documentId)

    /**
     * Undoes a mis-click. Keyed by type rather than by document id: there is one CV slot and one
     * cover letter slot, and clearing the slot should not require knowing what is currently in it.
     */
    @DeleteMapping("/api/offers/{offerId}/documents/sent")
    fun unmarkSent(@PathVariable offerId: Long, @RequestParam type: DocumentType): Application =
        documents.unmarkSent(offerId, type)

    /**
     * A fabricated claim is a server-side refusal, not a client mistake, but 422 is the honest
     * code: the request was fine and the result was rejected on its content.
     */
    @ExceptionHandler(FabricatedClaimException::class)
    fun handleFabrication(exception: FabricatedClaimException): ResponseEntity<ProblemDetail> =
        ResponseEntity.unprocessableEntity().body(
            ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.message!!).apply {
                title = "Generated document rejected"
                setProperty("fabricatedClaims", exception.claims)
            }
        )

    /**
     * Same shape as the fabrication handler and for the same reason: the request was well formed and
     * the work was refused on its content. `sensitiveFields` names fields, never values - see
     * [SensitiveDataInPromptException].
     */
    @ExceptionHandler(SensitiveDataInPromptException::class)
    fun handleSensitiveData(exception: SensitiveDataInPromptException): ResponseEntity<ProblemDetail> =
        ResponseEntity.unprocessableEntity().body(
            ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.message!!).apply {
                title = "Generation refused to protect personal data"
                setProperty("sensitiveFields", exception.fields)
            }
        )

    /** `reuse` refusing a non-CV source names a request the client sent wrong, not a server refusal. */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalid(exception: IllegalArgumentException): Map<String, String?> =
        mapOf("error" to exception.message)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMissing(exception: NoSuchElementException): Map<String, String?> =
        mapOf("error" to exception.message)

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleNotReady(exception: IllegalStateException): Map<String, String?> =
        mapOf("error" to exception.message)
}
