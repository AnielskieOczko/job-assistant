package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileImportException
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/profile")
internal class ProfileController(private val profiles: ProfileService) {

    @GetMapping
    fun current(): ResponseEntity<CandidateProfile> =
        profiles.current()?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @PostMapping("/import")
    fun import(@RequestBody document: ProfileImport): CandidateProfile = profiles.replace(document)

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
}
