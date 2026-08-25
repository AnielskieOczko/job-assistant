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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
internal class AnalysisController(private val analyses: AnalysisService) {

    /**
     * Queues the work and returns 202 with a Location header. The client polls that URL until
     * `state` is DONE or FAILED. `profileId` is required: starting an analysis against "whichever
     * profile" is exactly the ambiguity multiple profiles removes, and the caller always has a
     * selected one by the time this is reachable.
     */
    @PostMapping("/api/offers/{offerId}/analyses")
    fun start(@PathVariable offerId: Long, @RequestParam profileId: Long): ResponseEntity<Map<String, Any>> {
        val analysisId = analyses.start(offerId, profileId)
        return ResponseEntity.accepted()
            .location(URI.create("/api/analyses/$analysisId"))
            .body(mapOf("analysisId" to analysisId, "state" to "PENDING"))
    }

    @GetMapping("/api/analyses/{analysisId}")
    fun report(@PathVariable analysisId: Long): ResponseEntity<AnalysisReport> =
        analyses.findReport(analysisId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** Defaults to the default profile when `profileId` is omitted - this endpoint is deep-linkable. */
    @GetMapping("/api/offers/{offerId}/analyses/latest")
    fun latest(
        @PathVariable offerId: Long,
        @RequestParam(required = false) profileId: Long?,
    ): ResponseEntity<AnalysisReport> =
        analyses.latestForOffer(offerId, profileId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** What to actually learn, across every offer analysed so far against the given profile. */
    @GetMapping("/api/analyses/aggregate")
    fun aggregate(@RequestParam(required = false) profileId: Long?): AggregateGapReport =
        analyses.aggregateGaps(profileId)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMissing(exception: NoSuchElementException): Map<String, String?> =
        mapOf("error" to exception.message)

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleNotReady(exception: IllegalStateException): Map<String, String?> =
        mapOf("error" to exception.message)
}
