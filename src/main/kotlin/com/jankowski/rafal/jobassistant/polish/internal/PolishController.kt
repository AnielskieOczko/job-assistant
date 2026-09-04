package com.jankowski.rafal.jobassistant.polish.internal

import com.jankowski.rafal.jobassistant.polish.UnusablePolishException
import com.jankowski.rafal.jobassistant.polish.PolishField
import com.jankowski.rafal.jobassistant.polish.PolishSuggestion
import com.jankowski.rafal.jobassistant.polish.ProsePolishService
import com.jankowski.rafal.jobassistant.privacy.SensitiveDataInPromptException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The one polish endpoint.
 *
 * A POST that spends tokens and changes nothing, which is the honest description: it is a POST
 * because it is not safe to repeat for free, and it stores nothing because the write is the
 * candidate's own subsequent `PUT` to the profile. There is deliberately no endpoint that polishes
 * a whole profile, a whole project or every bullet at once - each of those would be several calls
 * behind one click, and each suggestion has to be read by a person before it means anything.
 */
@RestController
internal class PolishController(private val polish: ProsePolishService) {

    @PostMapping("/api/profiles/{profileId}/polish")
    fun polish(
        @PathVariable profileId: Long,
        @RequestParam field: PolishField,
        @Valid @RequestBody request: PolishRequest,
    ): PolishSuggestion = polish.polish(profileId, field, request.text)

    /**
     * Blank text, or more of it than a field holds. Both are refused before a provider is reached,
     * which is the point of checking them at all: an empty box is not a request a model can answer,
     * and a pasted CV in a description field is a request priced like an analysis.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(exception: IllegalArgumentException): Map<String, String?> =
        mapOf("error" to exception.message)

    /** `ProfileService.require` throws this for a profile that does not exist or has no details. */
    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMissingProfile(exception: IllegalStateException): Map<String, String?> =
        mapOf("error" to exception.message)

    /**
     * A refusal to show what the model said, not an empty result. 422 for the same reason document
     * generation uses it: the request was fine and the answer was rejected on its content.
     */
    @ExceptionHandler(UnusablePolishException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleUnusable(exception: UnusablePolishException): Map<String, String?> =
        mapOf("error" to exception.message)

    /**
     * The same shape `DocumentController` uses, for the same reason: the request was well formed
     * and the work was refused on its content. It fires when the field's own text carries an
     * identifier - a description naming the project's URL, say - and `sensitiveFields` names
     * fields, never values.
     */
    @ExceptionHandler(SensitiveDataInPromptException::class)
    fun handleSensitiveData(exception: SensitiveDataInPromptException): ResponseEntity<ProblemDetail> =
        ResponseEntity.unprocessableEntity().body(
            ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.message!!).apply {
                title = "Polish refused to protect personal data"
                setProperty("sensitiveFields", exception.fields)
            }
        )
}

/**
 * Just the prose. Which field it is rides as a query parameter rather than a body property, so the
 * enum is what Spring binds against: an unknown kind is a 400 before any of this module runs, and
 * there is no default for a missing one to fall through to. The set of polishable fields is fixed
 * in Kotlin, and a client cannot name a fifth.
 */
internal data class PolishRequest(
    @field:NotBlank
    @field:Size(max = ProsePolishService.MAX_TEXT_LENGTH)
    val text: String = "",
)
