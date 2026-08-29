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
 * Reading and writing the contents of one profile.
 *
 * Every mutation answers with the whole [CandidateProfile] rather than the entity it touched. The
 * profile is small, single-user-per-persona and always rendered as a whole, so this saves the client
 * reassembling one from a patch response and leaves it with a single query key to invalidate.
 */
@RestController
@RequestMapping("/api/profiles/{profileId}")
internal class ProfileController(
    private val profiles: ProfileService,
    private val writes: ProfileWriteService,
) {

    @GetMapping
    fun current(@PathVariable profileId: Long): ResponseEntity<CandidateProfile> =
        profiles.current(profileId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @PostMapping("/import")
    fun import(@PathVariable profileId: Long, @RequestBody document: ProfileImport): CandidateProfile =
        profiles.replace(profileId, document)

    /** Creates this profile's details - the profile itself (POST /api/profiles) must already exist. */
    @PutMapping("/details")
    fun putDetails(@PathVariable profileId: Long, @Valid @RequestBody request: DetailsRequest): CandidateProfile =
        writes.putDetails(profileId, request)

    // ------------------------------------------------------------------ links

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    fun addLink(@PathVariable profileId: Long, @Valid @RequestBody request: LinkRequest) =
        writes.addLink(profileId, request)

    @PutMapping("/links/order")
    fun reorderLinks(@PathVariable profileId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderLinks(profileId, request.ids)

    @PutMapping("/links/{id}")
    fun updateLink(@PathVariable profileId: Long, @PathVariable id: Long, @Valid @RequestBody request: LinkRequest) =
        writes.updateLink(profileId, id, request)

    @DeleteMapping("/links/{id}")
    fun deleteLink(@PathVariable profileId: Long, @PathVariable id: Long) = writes.deleteLink(profileId, id)

    // ----------------------------------------------------------------- skills

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSkill(@PathVariable profileId: Long, @Valid @RequestBody request: SkillRequest) =
        writes.addSkill(profileId, request)

    @PutMapping("/skills/order")
    fun reorderSkills(@PathVariable profileId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderSkills(profileId, request.ids)

    @PutMapping("/skills/{id}")
    fun updateSkill(@PathVariable profileId: Long, @PathVariable id: Long, @Valid @RequestBody request: SkillUpdateRequest) =
        writes.updateSkill(profileId, id, request)

    @DeleteMapping("/skills/{id}")
    fun deleteSkill(@PathVariable profileId: Long, @PathVariable id: Long) = writes.deleteSkill(profileId, id)

    // ------------------------------------------------------------ experiences

    @PostMapping("/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    fun addExperience(@PathVariable profileId: Long, @Valid @RequestBody request: ExperienceRequest) =
        writes.addExperience(profileId, request)

    @PutMapping("/experiences/order")
    fun reorderExperiences(@PathVariable profileId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderExperiences(profileId, request.ids)

    @PutMapping("/experiences/{id}")
    fun updateExperience(
        @PathVariable profileId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ExperienceRequest,
    ) = writes.updateExperience(profileId, id, request)

    @DeleteMapping("/experiences/{id}")
    fun deleteExperience(@PathVariable profileId: Long, @PathVariable id: Long) =
        writes.deleteExperience(profileId, id)

    // ---------------------------------------------------------------- bullets

    @PostMapping("/experiences/{experienceId}/bullets")
    @ResponseStatus(HttpStatus.CREATED)
    fun addBullet(
        @PathVariable profileId: Long,
        @PathVariable experienceId: Long,
        @Valid @RequestBody request: BulletRequest,
    ) = writes.addBullet(profileId, experienceId, request)

    @PutMapping("/experiences/{experienceId}/bullets/order")
    fun reorderBullets(
        @PathVariable profileId: Long,
        @PathVariable experienceId: Long,
        @Valid @RequestBody request: ReorderRequest,
    ) = writes.reorderBullets(profileId, experienceId, request.ids)

    @PutMapping("/bullets/{id}")
    fun updateBullet(@PathVariable profileId: Long, @PathVariable id: Long, @Valid @RequestBody request: BulletRequest) =
        writes.updateBullet(profileId, id, request)

    @DeleteMapping("/bullets/{id}")
    fun deleteBullet(@PathVariable profileId: Long, @PathVariable id: Long) = writes.deleteBullet(profileId, id)

    // -------------------------------------------------------------- education

    @PostMapping("/education")
    @ResponseStatus(HttpStatus.CREATED)
    fun addEducation(@PathVariable profileId: Long, @Valid @RequestBody request: EducationRequest) =
        writes.addEducation(profileId, request)

    @PutMapping("/education/order")
    fun reorderEducation(@PathVariable profileId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderEducation(profileId, request.ids)

    @PutMapping("/education/{id}")
    fun updateEducation(
        @PathVariable profileId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: EducationRequest,
    ) = writes.updateEducation(profileId, id, request)

    @DeleteMapping("/education/{id}")
    fun deleteEducation(@PathVariable profileId: Long, @PathVariable id: Long) =
        writes.deleteEducation(profileId, id)

    // ------------------------------------------------------------ credentials

    @PostMapping("/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCredential(@PathVariable profileId: Long, @Valid @RequestBody request: CredentialRequest) =
        writes.addCredential(profileId, request)

    @PutMapping("/credentials/order")
    fun reorderCredentials(@PathVariable profileId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderCredentials(profileId, request.ids)

    @PutMapping("/credentials/{id}")
    fun updateCredential(
        @PathVariable profileId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CredentialRequest,
    ) = writes.updateCredential(profileId, id, request)

    @DeleteMapping("/credentials/{id}")
    fun deleteCredential(@PathVariable profileId: Long, @PathVariable id: Long) =
        writes.deleteCredential(profileId, id)

    // --------------------------------------------------------------- projects

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    fun addProject(@PathVariable profileId: Long, @Valid @RequestBody request: ProjectRequest) =
        writes.addProject(profileId, request)

    @PutMapping("/projects/order")
    fun reorderProjects(@PathVariable profileId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderProjects(profileId, request.ids)

    @PutMapping("/projects/{id}")
    fun updateProject(
        @PathVariable profileId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ProjectRequest,
    ) = writes.updateProject(profileId, id, request)

    @DeleteMapping("/projects/{id}")
    fun deleteProject(@PathVariable profileId: Long, @PathVariable id: Long) =
        writes.deleteProject(profileId, id)

    @PostMapping("/projects/{projectId}/bullets")
    @ResponseStatus(HttpStatus.CREATED)
    fun addProjectBullet(
        @PathVariable profileId: Long,
        @PathVariable projectId: Long,
        @Valid @RequestBody request: BulletRequest,
    ) = writes.addProjectBullet(profileId, projectId, request)

    @PutMapping("/projects/{projectId}/bullets/order")
    fun reorderProjectBullets(
        @PathVariable profileId: Long,
        @PathVariable projectId: Long,
        @Valid @RequestBody request: ReorderRequest,
    ) = writes.reorderProjectBullets(profileId, projectId, request.ids)

    // -------------------------------------------------------------- languages

    @PostMapping("/languages")
    @ResponseStatus(HttpStatus.CREATED)
    fun addLanguage(@PathVariable profileId: Long, @Valid @RequestBody request: LanguageRequest) =
        writes.addLanguage(profileId, request)

    @PutMapping("/languages/order")
    fun reorderLanguages(@PathVariable profileId: Long, @Valid @RequestBody request: ReorderRequest) =
        writes.reorderLanguages(profileId, request.ids)

    @PutMapping("/languages/{id}")
    fun updateLanguage(
        @PathVariable profileId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: LanguageRequest,
    ) = writes.updateLanguage(profileId, id, request)

    @DeleteMapping("/languages/{id}")
    fun deleteLanguage(@PathVariable profileId: Long, @PathVariable id: Long) =
        writes.deleteLanguage(profileId, id)

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
            setProperty("blockingProjects", exception.blockingProjects)
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
