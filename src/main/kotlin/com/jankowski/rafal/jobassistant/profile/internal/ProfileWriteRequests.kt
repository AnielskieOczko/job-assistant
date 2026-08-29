package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.profile.CredentialKind
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.Proficiency
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request bodies for per-entity editing.
 *
 * Every update is a full-entity PUT rather than a patch. Kotlin data classes cannot distinguish an
 * absent field from an explicit null without wrapping every property, and here that distinction
 * carries meaning: `endedOn = null` is what makes a role current, and `location = null` is a role
 * with no location. A patch would have to guess which of those a missing key meant.
 *
 * Skills are identified by catalog id, not by name. These requests come from a picker that resolved
 * the name already, so re-resolving it would only add a failure mode. Import keeps names because a
 * hand-written document is exactly where aliases earn their keep.
 */
internal data class CreateProfileRequest(
    @field:NotBlank val name: String = "",
)

internal data class DetailsRequest(
    @field:NotBlank val fullName: String = "",
    val headline: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val summary: String? = null,
)

internal data class LinkRequest(
    @field:NotBlank val label: String = "",
    @field:NotBlank val url: String = "",
)

internal data class SkillRequest(
    val skillId: Long = 0,
    val proficiency: Proficiency = Proficiency.WORKING,
    @field:PositiveOrZero val yearsOfExperience: BigDecimal? = null,
    val lastUsedYear: Int? = null,
)

/**
 * Updating a skill deliberately cannot change which canonical skill it is. Swapping the identity
 * under a row would strand every bullet tagged with the old skill, and the delete path already
 * refuses exactly that. Change your mind about a skill by removing it and adding the other one.
 */
internal data class SkillUpdateRequest(
    val proficiency: Proficiency = Proficiency.WORKING,
    @field:PositiveOrZero val yearsOfExperience: BigDecimal? = null,
    val lastUsedYear: Int? = null,
)

internal data class ExperienceRequest(
    @field:NotBlank val company: String = "",
    @field:NotBlank val roleTitle: String = "",
    val location: String? = null,
    val startedOn: LocalDate = LocalDate.EPOCH,
    val endedOn: LocalDate? = null,
    val summary: String? = null,
)

internal data class BulletRequest(
    @field:NotBlank val text: String = "",
    val skillIds: Set<Long> = emptySet(),
)

internal data class EducationRequest(
    @field:NotBlank val institution: String = "",
    @field:NotBlank val degree: String = "",
    val fieldOfStudy: String? = null,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
)

internal data class CredentialRequest(
    @field:NotBlank val title: String = "",
    @field:NotBlank val issuer: String = "",
    val kind: CredentialKind = CredentialKind.COURSE,
    val url: String? = null,
    val credentialId: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
)

internal data class LanguageRequest(
    @field:NotBlank val language: String = "",
    val level: LanguageLevel = LanguageLevel.B1,
)

/**
 * A reorder names the collection's ids in their new order. It must name all of them: a partial list
 * would leave the omitted rows at whatever position they happened to hold, which reads as silent
 * corruption rather than a reorder.
 */
internal data class ReorderRequest(
    @field:NotEmpty val ids: List<Long> = emptyList(),
)
