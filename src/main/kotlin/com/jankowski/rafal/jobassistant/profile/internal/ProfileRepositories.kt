package com.jankowski.rafal.jobassistant.profile.internal

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

internal interface ProfileLinkRepository : CrudRepository<ProfileLinkRow, Long> {
    @Query("select * from profile_link order by display_order")
    fun findAllOrdered(): List<ProfileLinkRow>
}

internal interface ProfileSkillRepository : CrudRepository<ProfileSkillRow, Long> {
    @Query("select * from profile_skill order by id")
    fun findAllOrdered(): List<ProfileSkillRow>
}

internal interface WorkExperienceRepository : CrudRepository<WorkExperienceRow, Long> {
    @Query("select * from work_experience order by display_order")
    fun findAllOrdered(): List<WorkExperienceRow>
}

internal interface EducationRepository : CrudRepository<EducationRow, Long> {
    @Query("select * from education order by display_order")
    fun findAllOrdered(): List<EducationRow>
}

internal interface LanguageSkillRepository : CrudRepository<LanguageSkillRow, Long> {
    @Query("select * from language_skill order by language")
    fun findAllOrdered(): List<LanguageSkillRow>
}
