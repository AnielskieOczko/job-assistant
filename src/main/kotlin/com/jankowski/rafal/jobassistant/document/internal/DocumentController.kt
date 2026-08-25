package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.document.DocumentService
import com.jankowski.rafal.jobassistant.document.DocumentType
import com.jankowski.rafal.jobassistant.document.FabricatedClaimException
import com.jankowski.rafal.jobassistant.document.GeneratedDocument
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMissing(exception: NoSuchElementException): Map<String, String?> =
        mapOf("error" to exception.message)

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleNotReady(exception: IllegalStateException): Map<String, String?> =
        mapOf("error" to exception.message)
}
