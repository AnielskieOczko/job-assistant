package com.jankowski.rafal.jobassistant.linkedin.internal

import com.jankowski.rafal.jobassistant.linkedin.LinkedInDraft
import com.jankowski.rafal.jobassistant.linkedin.LinkedInDraftService
import com.jankowski.rafal.jobassistant.linkedin.LinkedInSection
import com.jankowski.rafal.jobassistant.privacy.SensitiveDataInPromptException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/profiles/{profileId}/linkedin")
internal class LinkedInController(
    private val service: LinkedInDraftService,
) {

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    fun draft(
        @PathVariable profileId: Long,
        @RequestBody request: LinkedInDraftRequest,
    ): LinkedInDraft = service.draft(profileId, request.section, request.subjectId, request.notes)

    @GetMapping("/drafts")
    fun list(@PathVariable profileId: Long): List<LinkedInDraft> = service.list(profileId)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(exception: IllegalArgumentException): Map<String, String?> =
        mapOf("error" to exception.message)

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    fun handleIllegalState(exception: IllegalStateException): Map<String, String?> =
        mapOf("error" to exception.message)

    @ExceptionHandler(SensitiveDataInPromptException::class)
    fun handleSensitiveData(exception: SensitiveDataInPromptException): ResponseEntity<ProblemDetail> =
        ResponseEntity.unprocessableEntity().body(
            ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.message!!).apply {
                title = "LinkedIn draft refused to protect personal data"
                setProperty("sensitiveFields", exception.fields)
            }
        )
}

internal data class LinkedInDraftRequest(
    val section: LinkedInSection = LinkedInSection.ABOUT,
    val subjectId: Long? = null,
    val notes: String? = null,
)
