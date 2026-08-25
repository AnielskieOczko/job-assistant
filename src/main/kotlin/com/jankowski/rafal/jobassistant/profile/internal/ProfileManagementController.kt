package com.jankowski.rafal.jobassistant.profile.internal

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Creating, listing, deleting and defaulting profiles themselves - not their contents. */
@RestController
@RequestMapping("/api/profiles")
internal class ProfileManagementController(private val management: ProfileManagementService) {

    @GetMapping
    fun list(): List<ProfileSummary> = management.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateProfileRequest): ProfileSummary = management.create(request.name)

    @PutMapping("/{profileId}/default")
    fun setDefault(@PathVariable profileId: Long): ProfileSummary = management.setDefault(profileId)

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable profileId: Long) = management.delete(profileId)

    @ExceptionHandler(ProfileConflictException::class)
    fun handleConflict(exception: ProfileConflictException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.message!!).apply {
            title = "Profile deletion rejected"
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    @ExceptionHandler(UnknownProfileEntityException::class)
    fun handleUnknown(exception: UnknownProfileEntityException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message!!).apply {
            title = "No such profile"
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalid(exception: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val fieldErrors = exception.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "is invalid") }
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            fieldErrors.entries.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "Invalid request." },
        ).apply {
            title = "Invalid profile"
            setProperty("fieldErrors", fieldErrors)
        }
        return ResponseEntity.badRequest().body(problem)
    }
}
