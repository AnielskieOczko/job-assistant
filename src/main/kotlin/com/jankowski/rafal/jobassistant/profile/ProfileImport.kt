package com.jankowski.rafal.jobassistant.profile

import java.math.BigDecimal
import java.time.LocalDate

/**
 * The hand-authored profile document. Skills are given by catalog name or alias rather than id,
 * so the file stays readable and diffable; import resolves them and fails loudly on anything the
 * catalog does not know, rather than quietly dropping it.
 */
data class ProfileImport(
    val details: ProfileDetails,
    val links: List<LinkImport> = emptyList(),
    val skills: List<SkillImport> = emptyList(),
    val experiences: List<ExperienceImport> = emptyList(),
    val education: List<EducationImport> = emptyList(),
    val credentials: List<CredentialImport> = emptyList(),
    val projects: List<ProjectImport> = emptyList(),
    val languages: List<LanguageImport> = emptyList(),
)

data class LinkImport(val label: String, val url: String)

data class SkillImport(
    val skill: String,
    val proficiency: Proficiency,
    val yearsOfExperience: BigDecimal? = null,
    val lastUsedYear: Int? = null,
)

data class ExperienceImport(
    val company: String,
    val roleTitle: String,
    val location: String? = null,
    val startedOn: LocalDate,
    val endedOn: LocalDate? = null,
    val summary: String? = null,
    val bullets: List<BulletImport> = emptyList(),
)

data class BulletImport(val text: String, val skills: List<String> = emptyList())

data class EducationImport(
    val institution: String,
    val degree: String,
    val fieldOfStudy: String? = null,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
)

data class CredentialImport(
    val title: String,
    val issuer: String,
    val kind: CredentialKind,
    val url: String? = null,
    val credentialId: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
)

data class ProjectImport(
    val name: String,
    val url: String? = null,
    val description: String? = null,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
    val skills: List<String> = emptyList(),
    val bullets: List<BulletImport> = emptyList(),
)

data class LanguageImport(val language: String, val level: LanguageLevel)

/**
 * Thrown when an import names skills the catalog cannot resolve, or tags a bullet with a skill
 * the profile does not declare.
 *
 * Both are refusals to guess: an unresolvable skill would silently vanish from every future gap
 * report, and a bullet evidencing an undeclared skill would let that skill onto a CV with nothing
 * backing it.
 */
class ProfileImportException(
    val unresolvedSkills: List<String> = emptyList(),
    val undeclaredBulletSkills: List<String> = emptyList(),
) : RuntimeException(
    buildString {
        append("Profile import rejected.")
        if (unresolvedSkills.isNotEmpty()) {
            append(" Not in the skill catalog: ${unresolvedSkills.joinToString()}.")
            append(" Add them via POST /api/catalog/skills, or correct the spelling.")
        }
        if (undeclaredBulletSkills.isNotEmpty()) {
            append(" Tagged on a bullet but not declared in the profile's skill list: ")
            append(undeclaredBulletSkills.joinToString())
            append(".")
        }
    }
)
