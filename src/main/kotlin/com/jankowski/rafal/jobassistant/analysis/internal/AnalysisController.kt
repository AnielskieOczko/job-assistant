package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.AggregateGapReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
internal class AnalysisController(private val analyses: AnalysisService) {

    /**
     * Queues the work and returns 202 with a Location header. The client polls that URL until
     * `state` is DONE or FAILED.
     */
    @PostMapping("/api/offers/{offerId}/analyses")
    fun start(@PathVariable offerId: Long): ResponseEntity<Map<String, Any>> {
        val analysisId = analyses.start(offerId)
        return ResponseEntity.accepted()
            .location(URI.create("/api/analyses/$analysisId"))
            .body(mapOf("analysisId" to analysisId, "state" to "PENDING"))
    }

    @GetMapping("/api/analyses/{analysisId}")
    fun report(@PathVariable analysisId: Long): ResponseEntity<AnalysisReport> =
        analyses.findReport(analysisId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/api/offers/{offerId}/analyses/latest")
    fun latest(@PathVariable offerId: Long): ResponseEntity<AnalysisReport> =
        analyses.latestForOffer(offerId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** What to actually learn, across every offer analysed so far. */
    @GetMapping("/api/analyses/aggregate")
    fun aggregate(): AggregateGapReport = analyses.aggregateGaps()

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMissing(exception: NoSuchElementException): Map<String, String?> =
        mapOf("error" to exception.message)

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleNotReady(exception: IllegalStateException): Map<String, String?> =
        mapOf("error" to exception.message)
}
