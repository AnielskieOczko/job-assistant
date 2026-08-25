package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileImportException
import com.jankowski.rafal.jobassistant.profile.ProfileService
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

/**
 * Reading and writing the profile.
 *
 * Every mutation answers with the whole [CandidateProfile] rather than the entity it touched. The
 * profile is small, single-user and always rendered as a whole, so this saves the client
 * reassembling one from a patch response and leaves it with a single query key to invalidate.
 */
@RestController
@RequestMapping("/api/profile")
internal class ProfileController(
    private val profiles: ProfileService,
    private val writes: ProfileWriteService,
) {

    @GetMapping
    fun current(): ResponseEntity<CandidateProfile> =
        profiles.current()?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @PostMapping("/import")
    fun import(@RequestBody document: ProfileImport): CandidateProfile = profiles.replace(document)

    /** Also the way a profile comes into existence - no document required. */
    @PutMapping("/details")
    fun putDetails(@Valid @RequestBody request: DetailsRequest): CandidateProfile = writes.putDetails(request)

    // ------------------------------------------------------------------ links

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    fun addLink(@Valid @RequestBody request: LinkRequest) = writes.addLink(request)

    @PutMapping("/links/order")
    fun reorderLinks(@Valid @RequestBody request: ReorderRequest) = writes.reorderLinks(request.ids)

    @PutMapping("/links/{id}")
    fun updateLink(@PathVariable id: Long, @Valid @RequestBody request: LinkRequest) = writes.updateLink(id, request)

    @DeleteMapping("/links/{id}")
    fun deleteLink(@PathVariable id: Long) = writes.deleteLink(id)

    // ----------------------------------------------------------------- skills

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSkill(@Valid @RequestBody request: SkillRequest) = writes.addSkill(request)

    @PutMapping("/skills/order")
    fun reorderSkills(@Valid @RequestBody request: ReorderRequest) = writes.reorderSkills(request.ids)

    @PutMapping("/skills/{id}")
    fun updateSkill(@PathVariable id: Long, @Valid @RequestBody request: SkillUpdateRequest) =
        writes.updateSkill(id, request)

    @DeleteMapping("/skills/{id}")
    fun deleteSkill(@PathVariable id: Long) = writes.deleteSkill(id)

    // ------------------------------------------------------------ experiences

    @PostMapping("/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    fun addExperience(@Valid @RequestBody request: ExperienceRequest) = writes.addExperience(request)

    @PutMapping("/experiences/order")
    fun reorderExperiences(@Valid @RequestBody request: ReorderRequest) = writes.reorderExperiences(request.ids)

    @PutMapping("/experiences/{id}")
    fun updateExperience(@PathVariable id: Long, @Valid @RequestBody request: ExperienceRequest) =
        writes.updateExperience(id, request)

    @DeleteMapping("/experiences/{id}")
    fun deleteExperience(@PathVariable id: Long) = writes.deleteExperience(id)

    // ---------------------------------------------------------------- bullets

    @PostMapping("/experiences/{experienceId}/bullets")
    @ResponseStatus(HttpStatus.CREATED)
    fun addBullet(@PathVariable experienceId: Long, @Valid @RequestBody request: BulletRequest) =
        writes.addBullet(experienceId, request)

    @PutMapping("/experiences/{experienceId}/bullets/order")
    fun reorderBullets(@PathVariable experienceId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderBullets(experienceId, request.ids)

    @PutMapping("/bullets/{id}")
    fun updateBullet(@PathVariable id: Long, @Valid @RequestBody request: BulletRequest) =
        writes.updateBullet(id, request)

    @DeleteMapping("/bullets/{id}")
    fun deleteBullet(@PathVariable id: Long) = writes.deleteBullet(id)

    // -------------------------------------------------------------- education

    @PostMapping("/education")
    @ResponseStatus(HttpStatus.CREATED)
    fun addEducation(@Valid @RequestBody request: EducationRequest) = writes.addEducation(request)

    @PutMapping("/education/order")
    fun reorderEducation(@Valid @RequestBody request: ReorderRequest) = writes.reorderEducation(request.ids)

    @PutMapping("/education/{id}")
    fun updateEducation(@PathVariable id: Long, @Valid @RequestBody request: EducationRequest) =
        writes.updateEducation(id, request)

    @DeleteMapping("/education/{id}")
    fun deleteEducation(@PathVariable id: Long) = writes.deleteEducation(id)

    // -------------------------------------------------------------- languages

    @PostMapping("/languages")
    @ResponseStatus(HttpStatus.CREATED)
    fun addLanguage(@Valid @RequestBody request: LanguageRequest) = writes.addLanguage(request)

    @PutMapping("/languages/order")
    fun reorderLanguages(@Valid @RequestBody request: ReorderRequest) = writes.reorderLanguages(request.ids)

    @PutMapping("/languages/{id}")
    fun updateLanguage(@PathVariable id: Long, @Valid @RequestBody request: LanguageRequest) =
        writes.updateLanguage(id, request)

    @DeleteMapping("/languages/{id}")
    fun deleteLanguage(@PathVariable id: Long) = writes.deleteLanguage(id)

    // ----------------------------------------------------------------- errors

    /**
     * Import failures are the user's data problem, not a server fault, and the detail lists
     * exactly which names to fix - so it is worth a structured 400 rather than a stack trace.
     */
    @ExceptionHandler(ProfileImportException::class)
    fun handleImportFailure(exception: ProfileImportException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message!!).apply {
            title = "Profile import rejected"
            setProperty("unresolvedSkills", exception.unresolvedSkills)
            setProperty("undeclaredBulletSkills", exception.undeclaredBulletSkills)
        }

    /**
     * The request was well-formed but cannot be reconciled with what is stored - a skill already
     * held, a language already listed, a skill still cited by bullets. `blockingBullets` names the
     * rows in the way so the UI can offer to go and fix them.
     */
    @ExceptionHandler(ProfileConflictException::class)
    fun handleConflict(exception: ProfileConflictException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.message!!).apply {
            title = "Profile edit rejected"
            setProperty("blockingBullets", exception.blockingBullets)
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    @ExceptionHandler(UnknownProfileEntityException::class)
    fun handleUnknown(exception: UnknownProfileEntityException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message!!).apply {
            title = "Not on this profile"
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
            title = "Invalid profile edit"
            setProperty("fieldErrors", fieldErrors)
        }
        return ResponseEntity.badRequest().body(problem)
    }
}
