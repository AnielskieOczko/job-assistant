package com.jankowski.rafal.jobassistant.linkedin.internal

import com.jankowski.rafal.jobassistant.linkedin.LinkedInDraft
import com.jankowski.rafal.jobassistant.linkedin.LinkedInSection
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@Repository
internal class LinkedInDraftRepository(
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
) {

    @Transactional
    fun save(
        profileId: Long,
        section: LinkedInSection,
        subjectId: Long?,
        profileRevision: Long,
        content: String,
        unheldSkills: List<String>,
        modelProfile: String?,
    ): LinkedInDraft {
        val id = jdbc.sql(
            """
            insert into linkedin_draft (
                profile_id, section, subject_id, profile_revision, content, unheld_skills, model_profile
            )
            values (
                :profileId, :section, :subjectId, :profileRevision, :content,
                array(select jsonb_array_elements_text(cast(:unheldSkills as jsonb))),
                :modelProfile
            )
            returning id
            """
        )
            .param("profileId", profileId)
            .param("section", section.name)
            .param("subjectId", subjectId)
            .param("profileRevision", profileRevision)
            .param("content", content)
            .param("unheldSkills", json.writeValueAsString(unheldSkills))
            .param("modelProfile", modelProfile)
            .query(Long::class.java)
            .single()

        return findById(id, currentRevision = profileRevision)!!
    }

    fun listForProfile(profileId: Long, currentRevision: Long): List<LinkedInDraft> =
        jdbc.sql(
            """
            select id, profile_id, section, subject_id, profile_revision,
                   content, unheld_skills, model_profile, created_at
            from linkedin_draft
            where profile_id = :profileId
            order by created_at desc, id desc
            """
        )
            .param("profileId", profileId)
            .query { rs, _ -> mapRow(rs, currentRevision) }
            .list()

    private fun findById(id: Long, currentRevision: Long): LinkedInDraft? =
        jdbc.sql(
            """
            select id, profile_id, section, subject_id, profile_revision,
                   content, unheld_skills, model_profile, created_at
            from linkedin_draft
            where id = :id
            """
        )
            .param("id", id)
            .query { rs, _ -> mapRow(rs, currentRevision) }
            .optional()
            .orElse(null)

    private fun mapRow(rs: java.sql.ResultSet, currentRevision: Long): LinkedInDraft {
        val profileRevision = rs.getLong("profile_revision")
        return LinkedInDraft(
            id = rs.getLong("id"),
            profileId = rs.getLong("profile_id"),
            section = LinkedInSection.valueOf(rs.getString("section")),
            subjectId = rs.getObject("subject_id")?.let { (it as Number).toLong() },
            profileRevision = profileRevision,
            stale = currentRevision > profileRevision,
            content = rs.getString("content"),
            unheldSkills = (rs.getArray("unheld_skills")?.array as? Array<*>)
                ?.map { it.toString() }
                ?: emptyList(),
            modelProfile = rs.getString("model_profile"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }
}
