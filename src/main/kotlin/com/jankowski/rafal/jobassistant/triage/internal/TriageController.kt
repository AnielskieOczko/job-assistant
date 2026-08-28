package com.jankowski.rafal.jobassistant.triage.internal

import com.jankowski.rafal.jobassistant.triage.TriageQueue
import com.jankowski.rafal.jobassistant.triage.TriageRanking
import com.jankowski.rafal.jobassistant.triage.TriageService
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The ranked, filtered review queue.
 *
 * Read-only, deliberately. Approve and reject stay on `/api/catalog/unmatched/{id}`, because the
 * rule that only a human decision may grow the catalog is enforced in `catalog` and a second write
 * path would be a second place to forget it.
 */
@RestController
@RequestMapping("/api/triage")
@Validated
internal class TriageController(private val triage: TriageService) {

    @GetMapping("/queue")
    fun queue(
        @RequestParam(defaultValue = "${TriageService.DEFAULT_MIN_OCCURRENCES}")
        @PositiveOrZero minOccurrences: Int,
        @RequestParam(defaultValue = "SCOPE") ranking: TriageRanking,
        @RequestParam(defaultValue = "${TriageService.DEFAULT_LIMIT}")
        @Positive limit: Int,
    ): TriageQueue = triage.queue(minOccurrences, ranking, limit)
}
