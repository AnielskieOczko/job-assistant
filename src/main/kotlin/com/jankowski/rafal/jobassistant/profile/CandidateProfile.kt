package com.jankowski.rafal.jobassistant.profile

import java.math.BigDecimal
import java.time.LocalDate

/**
 * The complete verified profile. Every claim a generated CV or cover letter can make must trace
 * back to something in here - that is the invariant the document module enforces.
 */
data class CandidateProfile(
    val details: ProfileDetails,
    val links: List<ProfileLink>,
    val skills: List<ProfileSkill>,
    val experiences: List<WorkExperience>,
    val education: List<Education>,
    val languages: List<LanguageSkill>,
) {
    /** Canonical skill ids the candidate actually holds - the allowlist for the CV invariant. */
    val heldSkillIds: Set<Long> = skills.mapTo(mutableSetOf()) { it.skillId }

    val bullets: List<ExperienceBullet> = experiences.flatMap { it.bullets }

    fun languageLevel(language: String): LanguageLevel? =
        languages.firstOrNull { it.language.equals(language, ignoreCase = true) }?.level

    fun bulletsEvidencing(skillId: Long): List<ExperienceBullet> =
        bullets.filter { skillId in it.skillIds }
}

data class ProfileDetails(
    val fullName: String,
    val headline: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val summary: String? = null,
)

data class ProfileLink(val id: Long, val label: String, val url: String)

data class ProfileSkill(
    val id: Long,
    val skillId: Long,
    val proficiency: Proficiency,
    val yearsOfExperience: BigDecimal? = null,
    val lastUsedYear: Int? = null,
)

data class WorkExperience(
    val id: Long,
    val company: String,
    val roleTitle: String,
    val location: String? = null,
    val startedOn: LocalDate,
    val endedOn: LocalDate? = null,
    val summary: String? = null,
    val bullets: List<ExperienceBullet> = emptyList(),
) {
    val isCurrent: Boolean get() = endedOn == null
}

/** A single achievement line, tagged with the canonical skills it genuinely evidences. */
data class ExperienceBullet(
    val id: Long,
    val text: String,
    val skillIds: Set<Long> = emptySet(),
)

data class Education(
    val id: Long,
    val institution: String,
    val degree: String,
    val fieldOfStudy: String? = null,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
)

data class LanguageSkill(val id: Long, val language: String, val level: LanguageLevel)
