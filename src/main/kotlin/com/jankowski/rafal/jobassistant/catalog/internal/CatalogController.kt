package com.jankowski.rafal.jobassistant.catalog.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTerm
import jakarta.validation.constraints.Positive
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Review queue for terms the extractor could not place, plus a read-only view of the catalog.
 *
 * This is the human-in-the-loop that keeps the taxonomy honest: nothing enters the catalog
 * because a model suggested it.
 */
@RestController
@RequestMapping("/api/catalog")
@Validated
internal class CatalogController(private val catalog: SkillCatalog) {

    @GetMapping("/skills")
    fun skills(): List<CanonicalSkill> = catalog.findAll()

    @GetMapping("/skills/resolve")
    fun resolve(@RequestParam term: String): ResponseEntity<CanonicalSkill> =
        catalog.resolve(term)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/unmatched")
    fun unmatched(@RequestParam(defaultValue = "100") @Positive limit: Int): List<UnmatchedTerm> =
        catalog.pendingUnmatchedTerms(limit)

    /** Grows the catalog by hand when the seed does not cover a skill the profile needs. */
    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSkill(@RequestBody request: CreateSkillRequest): CanonicalSkill =
        catalog.createSkill(request.name, request.category, request.aliases)

    @PutMapping("/skills/{id}")
    fun updateSkill(@PathVariable id: Long, @RequestBody request: UpdateSkillRequest): CanonicalSkill =
        catalog.updateSkill(id, request.name, request.category)

    @DeleteMapping("/skills/{id}")
    fun deleteSkill(@PathVariable id: Long) = catalog.deleteSkill(id)

    @PostMapping("/unmatched/{termId}/approve")
    fun approve(@PathVariable termId: Long, @RequestParam skillId: Long): CanonicalSkill =
        catalog.approveUnmatchedTerm(termId, skillId)

    @PostMapping("/unmatched/{termId}/reject")
    fun reject(@PathVariable termId: Long) = catalog.rejectUnmatchedTerm(termId)

    // ----------------------------------------------------------------- errors

    /** A rename colliding with another skill's name, or a delete blocked by an in-use skill. */
    @ExceptionHandler(CatalogConflictException::class)
    fun handleConflict(exception: CatalogConflictException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.message!!).apply {
            title = "Catalog edit rejected"
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    @ExceptionHandler(UnknownSkillException::class)
    fun handleUnknown(exception: UnknownSkillException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message!!).apply {
            title = "Skill not found"
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }
}

internal data class CreateSkillRequest(
    val name: String,
    val category: SkillCategory,
    val aliases: List<String> = emptyList(),
)

internal data class UpdateSkillRequest(
    val name: String,
    val category: SkillCategory,
)
