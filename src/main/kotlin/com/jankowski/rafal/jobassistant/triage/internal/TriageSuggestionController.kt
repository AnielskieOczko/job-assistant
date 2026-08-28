package com.jankowski.rafal.jobassistant.triage.internal

import com.jankowski.rafal.jobassistant.triage.SuggestionRun
import com.jankowski.rafal.jobassistant.triage.TriageRanking
import com.jankowski.rafal.jobassistant.triage.TriageService
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Asks a model to read the terms string similarity could not place.
 *
 * A POST with no body that changes stored state, deliberately: it spends tokens, so it must be a
 * thing someone chose to do rather than something a page load can cause. `GET /api/triage/queue`
 * never calls a model and shows whatever this has already stored.
 */
@RestController
@RequestMapping("/api/triage")
@Validated
internal class TriageSuggestionController(private val suggestions: ModelTriageSuggestionService) {

    @PostMapping("/suggest")
    fun suggest(
        @RequestParam(defaultValue = "${TriageService.DEFAULT_MIN_OCCURRENCES}")
        @PositiveOrZero minOccurrences: Int,
        @RequestParam(defaultValue = "SCOPE") ranking: TriageRanking,
        @RequestParam(defaultValue = "25") @Positive limit: Int,
    ): SuggestionRun = suggestions.suggestFor(minOccurrences, ranking, limit)
}
