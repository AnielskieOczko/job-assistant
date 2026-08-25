package com.jankowski.rafal.jobassistant.profile.internal

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

internal interface ProfileRepository : CrudRepository<ProfileRow, Long> {
    @Query("select * from profile order by id")
    fun findAllOrdered(): List<ProfileRow>

    @Query("select count(*) from profile")
    fun countAll(): Long

    @Query("select id from profile where is_default")
    fun findDefaultId(): Long?
}

internal interface ProfileLinkRepository : CrudRepository<ProfileLinkRow, Long> {
    @Query("select * from profile_link where profile_id = :profileId order by display_order, id")
    fun findAllOrdered(profileId: Long): List<ProfileLinkRow>

    @Query("select * from profile_link where id = :id and profile_id = :profileId")
    fun findByIdAndProfileId(id: Long, profileId: Long): ProfileLinkRow?

    @Modifying
    @Query("delete from profile_link where profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)
}

internal interface ProfileSkillRepository : CrudRepository<ProfileSkillRow, Long> {
    @Query("select * from profile_skill where profile_id = :profileId order by display_order, id")
    fun findAllOrdered(profileId: Long): List<ProfileSkillRow>

    @Query("select * from profile_skill where id = :id and profile_id = :profileId")
    fun findByIdAndProfileId(id: Long, profileId: Long): ProfileSkillRow?

    @Query("select * from profile_skill where profile_id = :profileId and canonical_skill_id = :skillId")
    fun findByCanonicalSkillId(profileId: Long, skillId: Long): ProfileSkillRow?

    @Modifying
    @Query("delete from profile_skill where profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)
}

internal interface WorkExperienceRepository : CrudRepository<WorkExperienceRow, Long> {
    @Query("select * from work_experience where profile_id = :profileId order by display_order, id")
    fun findAllOrdered(profileId: Long): List<WorkExperienceRow>

    @Query("select * from work_experience where id = :id and profile_id = :profileId")
    fun findByIdAndProfileId(id: Long, profileId: Long): WorkExperienceRow?

    /** Bullets and their skill tags cascade from the FK on `experience_bullet.work_experience_id`. */
    @Modifying
    @Query("delete from work_experience where profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)
}

/**
 * Bullets are read for the whole profile in one query and grouped in Kotlin rather than fetched per
 * experience, so promoting them to their own aggregate root costs one extra round trip in total
 * rather than one per role.
 */
internal interface ExperienceBulletRepository : CrudRepository<ExperienceBulletRow, Long> {
    @Query(
        "select b.* from experience_bullet b join work_experience w on w.id = b.work_experience_id " +
            "where w.profile_id = :profileId order by b.work_experience_id, b.display_order, b.id"
    )
    fun findAllOrderedForProfile(profileId: Long): List<ExperienceBulletRow>

    @Query(
        "select * from experience_bullet where work_experience_id = :experienceId " +
            "order by display_order, id"
    )
    fun findByExperience(experienceId: Long): List<ExperienceBulletRow>

    @Query(
        "select b.* from experience_bullet b join work_experience w on w.id = b.work_experience_id " +
            "where b.id = :id and w.profile_id = :profileId"
    )
    fun findByIdForProfile(id: Long, profileId: Long): ExperienceBulletRow?

    /**
     * Bullets still tagged with a skill *within this profile*, used to refuse a delete that would
     * strand the tag. Scoped through `work_experience.profile_id` so a profile B bullet citing the
     * same catalog skill never blocks a delete on profile A.
     */
    @Query(
        "select b.* from experience_bullet b " +
            "join experience_bullet_skill s on s.experience_bullet_id = b.id " +
            "join work_experience w on w.id = b.work_experience_id " +
            "where s.canonical_skill_id = :skillId and w.profile_id = :profileId order by b.id"
    )
    fun findTaggedWithForProfile(skillId: Long, profileId: Long): List<ExperienceBulletRow>
}

internal interface EducationRepository : CrudRepository<EducationRow, Long> {
    @Query("select * from education where profile_id = :profileId order by display_order, id")
    fun findAllOrdered(profileId: Long): List<EducationRow>

    @Query("select * from education where id = :id and profile_id = :profileId")
    fun findByIdAndProfileId(id: Long, profileId: Long): EducationRow?

    @Modifying
    @Query("delete from education where profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)
}

internal interface LanguageSkillRepository : CrudRepository<LanguageSkillRow, Long> {
    @Query("select * from language_skill where profile_id = :profileId order by display_order, id")
    fun findAllOrdered(profileId: Long): List<LanguageSkillRow>

    @Query("select * from language_skill where id = :id and profile_id = :profileId")
    fun findByIdAndProfileId(id: Long, profileId: Long): LanguageSkillRow?

    @Query("select * from language_skill where profile_id = :profileId and lower(language) = lower(:language)")
    fun findByLanguageIgnoringCase(profileId: Long, language: String): LanguageSkillRow?

    @Modifying
    @Query("delete from language_skill where profile_id = :profileId")
    fun deleteByProfileId(profileId: Long)
}
