package com.jankowski.rafal.jobassistant.profile.internal

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Table("profile")
internal data class ProfileRow(
    @Id val id: Long? = null,
    val name: String,
    val isDefault: Boolean = false,
    val revision: Long = 0,
    val createdAt: Instant = Instant.now(),
)

@Table("profile_link")
internal data class ProfileLinkRow(
    @Id val id: Long? = null,
    val profileId: Long,
    val label: String,
    val url: String,
    val displayOrder: Int,
)

@Table("profile_skill")
internal data class ProfileSkillRow(
    @Id val id: Long? = null,
    val profileId: Long,
    val canonicalSkillId: Long,
    val proficiency: String,
    val yearsOfExperience: BigDecimal?,
    val lastUsedYear: Int?,
    val displayOrder: Int,
)

@Table("work_experience")
internal data class WorkExperienceRow(
    @Id val id: Long? = null,
    val profileId: Long,
    val company: String,
    val roleTitle: String,
    val location: String?,
    val startedOn: LocalDate,
    val endedOn: LocalDate?,
    val summary: String?,
    val displayOrder: Int,
)

/**
 * An aggregate root in its own right, rather than a collection owned by [WorkExperienceRow].
 *
 * The tailoring model selects bullets by id, and [com.jankowski.rafal.jobassistant.profile.CandidateProfile]
 * is the allowlist that turns an unknown id into nothing at all -- so a bullet id is the thread
 * between a generated CV and the verified experience behind it. Spring Data JDBC deletes and
 * reinserts an entire `@MappedCollection` whenever its owner is saved, so while bullets hung off
 * the experience, correcting a company name silently renumbered every bullet under that role.
 * Owning themselves, their ids survive every edit that is not their own.
 *
 * Skill tags stay an owned collection: `experience_bullet_skill` has a composite primary key and no
 * surrogate id, so rewriting the set churns nothing.
 *
 * Scoped to a profile transitively through `work_experience.profile_id` or `project.profile_id` --
 * it needs no `profileId` column of its own. A bullet belongs to exactly one owner: `workExperienceId`
 * and `projectId` are mutually exclusive, enforced by a database check constraint rather than trusted
 * to application code.
 */
@Table("experience_bullet")
internal data class ExperienceBulletRow(
    @Id val id: Long? = null,
    val workExperienceId: Long?,
    val projectId: Long?,
    val text: String,
    val displayOrder: Int,
    @MappedCollection(idColumn = "experience_bullet_id")
    val skills: Set<ExperienceBulletSkillRow> = emptySet(),
)

@Table("experience_bullet_skill")
internal data class ExperienceBulletSkillRow(val canonicalSkillId: Long)

@Table("education")
internal data class EducationRow(
    @Id val id: Long? = null,
    val profileId: Long,
    val institution: String,
    val degree: String,
    val fieldOfStudy: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val displayOrder: Int,
)

@Table("credential")
internal data class CredentialRow(
    @Id val id: Long? = null,
    val profileId: Long,
    val title: String,
    val issuer: String,
    val kind: String,
    val url: String?,
    val credentialId: String?,
    val issuedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val displayOrder: Int,
)

@Table("project")
internal data class ProjectRow(
    @Id val id: Long? = null,
    val profileId: Long,
    val name: String,
    val url: String?,
    val description: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val displayOrder: Int,
    @MappedCollection(idColumn = "project_id")
    val skills: Set<ProjectSkillRow> = emptySet(),
)

@Table("project_skill")
internal data class ProjectSkillRow(val canonicalSkillId: Long)

@Table("language_skill")
internal data class LanguageSkillRow(
    @Id val id: Long? = null,
    val profileId: Long,
    val language: String,
    val level: String,
    val displayOrder: Int,
)
