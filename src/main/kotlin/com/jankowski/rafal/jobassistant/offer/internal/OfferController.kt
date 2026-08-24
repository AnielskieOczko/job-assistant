package com.jankowski.rafal.jobassistant.offer.internal

import com.jankowski.rafal.jobassistant.offer.Application
import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.JobOffer
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.offer.OfferSummary
import com.jankowski.rafal.jobassistant.offer.PastedOffer
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/offers")
internal class OfferController(private val offers: OfferService) {

    /**
     * Returns 200 with `deduplicated: true` when the same text was already stored, and 201 for a
     * genuinely new offer, so a client can tell the difference without a second request.
     */
    @PostMapping
    fun paste(@RequestBody request: PasteOfferRequest): ResponseEntity<PastedOffer> {
        val result = offers.paste(request.text, request.sourceUrl)
        val status = if (result.deduplicated) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(result)
    }

    @GetMapping
    fun list(): List<OfferSummary> = offers.list()

    @GetMapping("/{id}")
    fun byId(@PathVariable id: Long): ResponseEntity<JobOffer> =
        offers.findById(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{id}/status")
    fun updateStatus(@PathVariable id: Long, @RequestBody request: UpdateStatusRequest): Application =
        offers.updateStatus(id, request.status, request.appliedOn, request.notes)

    @ExceptionHandler(UnknownOfferException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleUnknownOffer(exception: UnknownOfferException): Map<String, String?> =
        mapOf("error" to exception.message)
}

internal data class PasteOfferRequest(
    @field:NotBlank val text: String,
    val sourceUrl: String? = null,
)

internal data class UpdateStatusRequest(
    val status: ApplicationStatus,
    val appliedOn: LocalDate? = null,
    val notes: String? = null,
)
