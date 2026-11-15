package com.jankowski.rafal.jobassistant.career.internal

import com.jankowski.rafal.jobassistant.career.CareerPlan
import com.jankowski.rafal.jobassistant.career.CareerPlanSummary
import com.jankowski.rafal.jobassistant.career.SkillProgress
import com.jankowski.rafal.jobassistant.career.SkillProgressState
import com.jankowski.rafal.jobassistant.privacy.SensitiveDataInPromptException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/career")
internal class CareerController(
    private val generator: CareerPlanGenerator,
) {

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(@RequestParam(required = false) profileId: Long?): CareerPlan =
        generator.generate(profileId)

    @GetMapping("/plans/current")
    fun current(@RequestParam(required = false) profileId: Long?): ResponseEntity<CareerPlan> =
        generator.current(profileId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @GetMapping("/plans")
    fun history(@RequestParam(required = false) profileId: Long?): List<CareerPlanSummary> =
        generator.history(profileId)

    @GetMapping("/plans/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<CareerPlan> =
        generator.get(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PutMapping("/progress/{skillId}")
    fun updateProgress(
        @PathVariable skillId: Long,
        @RequestParam(required = false) profileId: Long?,
        @RequestBody request: UpdateSkillProgressRequest,
    ): SkillProgress = generator.updateProgress(profileId, skillId, request.state)

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
                title = "Career plan refused to protect personal data"
                setProperty("sensitiveFields", exception.fields)
            }
        )
}

internal data class UpdateSkillProgressRequest(
    val state: SkillProgressState = SkillProgressState.NOT_STARTED,
)
