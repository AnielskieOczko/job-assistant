package com.jankowski.rafal.jobassistant.profile.internal

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDate

@Table("profile_link")
internal data class ProfileLinkRow(
    @Id val id: Long? = null,
    val label: String,
    val url: String,
    val displayOrder: Int,
)

@Table("profile_skill")
internal data class ProfileSkillRow(
    @Id val id: Long? = null,
    val canonicalSkillId: Long,
    val proficiency: String,
    val yearsOfExperience: BigDecimal?,
    val lastUsedYear: Int?,
)

/**
 * Aggregate root. Bullets and their skill tags are owned by the experience, so Spring Data JDBC
 * inserts and deletes the whole tree in one call and the ordering column is managed for us.
 */
@Table("work_experience")
internal data class WorkExperienceRow(
    @Id val id: Long? = null,
    val company: String,
    val roleTitle: String,
    val location: String?,
    val startedOn: LocalDate,
    val endedOn: LocalDate?,
    val summary: String?,
    val displayOrder: Int,
    @MappedCollection(idColumn = "work_experience_id", keyColumn = "display_order")
    val bullets: List<ExperienceBulletRow> = emptyList(),
)

@Table("experience_bullet")
internal data class ExperienceBulletRow(
    @Id val id: Long? = null,
    val text: String,
    @MappedCollection(idColumn = "experience_bullet_id")
    val skills: Set<ExperienceBulletSkillRow> = emptySet(),
)

@Table("experience_bullet_skill")
internal data class ExperienceBulletSkillRow(val canonicalSkillId: Long)

@Table("education")
internal data class EducationRow(
    @Id val id: Long? = null,
    val institution: String,
    val degree: String,
    val fieldOfStudy: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val displayOrder: Int,
)

@Table("language_skill")
internal data class LanguageSkillRow(
    @Id val id: Long? = null,
    val language: String,
    val level: String,
)
