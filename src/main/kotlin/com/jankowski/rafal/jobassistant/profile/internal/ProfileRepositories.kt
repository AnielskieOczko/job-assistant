package com.jankowski.rafal.jobassistant.profile.internal

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

internal interface ProfileLinkRepository : CrudRepository<ProfileLinkRow, Long> {
    @Query("select * from profile_link order by display_order, id")
    fun findAllOrdered(): List<ProfileLinkRow>
}

internal interface ProfileSkillRepository : CrudRepository<ProfileSkillRow, Long> {
    @Query("select * from profile_skill order by display_order, id")
    fun findAllOrdered(): List<ProfileSkillRow>

    @Query("select * from profile_skill where canonical_skill_id = :skillId")
    fun findByCanonicalSkillId(skillId: Long): ProfileSkillRow?
}

internal interface WorkExperienceRepository : CrudRepository<WorkExperienceRow, Long> {
    @Query("select * from work_experience order by display_order, id")
    fun findAllOrdered(): List<WorkExperienceRow>
}

/**
 * Bullets are read for the whole profile in one query and grouped in Kotlin rather than fetched per
 * experience, so promoting them to their own aggregate root costs one extra round trip in total
 * rather than one per role.
 */
internal interface ExperienceBulletRepository : CrudRepository<ExperienceBulletRow, Long> {
    @Query("select * from experience_bullet order by work_experience_id, display_order, id")
    fun findAllOrdered(): List<ExperienceBulletRow>

    @Query(
        "select * from experience_bullet where work_experience_id = :experienceId " +
            "order by display_order, id"
    )
    fun findByExperience(experienceId: Long): List<ExperienceBulletRow>

    @Query("select * from experience_bullet where id in (:ids)")
    fun findAllByIds(ids: Collection<Long>): List<ExperienceBulletRow>

    /** Bullets still tagged with a skill, used to refuse a delete that would strand the tag. */
    @Query(
        "select b.* from experience_bullet b " +
            "join experience_bullet_skill s on s.experience_bullet_id = b.id " +
            "where s.canonical_skill_id = :skillId order by b.id"
    )
    fun findTaggedWith(skillId: Long): List<ExperienceBulletRow>
}

internal interface EducationRepository : CrudRepository<EducationRow, Long> {
    @Query("select * from education order by display_order, id")
    fun findAllOrdered(): List<EducationRow>
}

internal interface LanguageSkillRepository : CrudRepository<LanguageSkillRow, Long> {
    @Query("select * from language_skill order by display_order, id")
    fun findAllOrdered(): List<LanguageSkillRow>

    @Query("select * from language_skill where lower(language) = lower(:language)")
    fun findByLanguageIgnoringCase(language: String): LanguageSkillRow?
}
