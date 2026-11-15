package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProfileTargetRole
import com.jankowski.rafal.jobassistant.profile.TargetRole
import com.jankowski.rafal.jobassistant.profile.TargetRoleSkill
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

/**
 * Persistence and read seam for [TargetRole].
 *
 * Lives outside [ProfileCollections] for the same reason `putDetails` does: one row per profile,
 * upserted directly, with no ordering and no entity id.
 */
@Service
internal class ProfileTargetRoles(
    private val jdbc: JdbcClient,
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
    private val jsonMapper: JsonMapper,
) : ProfileTargetRole {

    override fun of(profileId: Long?): TargetRole? {
        val id = profileId ?: profiles.findDefaultProfileId() ?: return null
        return find(id)
    }

    fun get(profileId: Long): TargetRole? {
        requireProfileExists(profileId)
        return find(profileId)
    }

    @Transactional
    fun put(profileId: Long, request: TargetRoleRequest): TargetRole {
        requireProfileExists(profileId)

        val uniqueSkillIds = request.skillIds.distinct()
        if (uniqueSkillIds.isNotEmpty()) {
            val foundIds = catalog.findAllById(uniqueSkillIds).map { it.id }.toSet()
            val missing = uniqueSkillIds.filterNot { it in foundIds }
            if (missing.isNotEmpty()) {
                throw UnknownProfileEntityException("Unknown catalog skill id(s): ${missing.joinToString()}.")
            }
        }

        val keywords = request.titleKeywords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        jdbc.sql(
            """
            insert into profile_target_role (profile_id, target_level, title_keywords, updated_at)
            values (:profileId, :targetLevel, array(select jsonb_array_elements_text(cast(:keywords as jsonb))), now())
            on conflict (profile_id) do update set
                target_level   = excluded.target_level,
                title_keywords = excluded.title_keywords,
                updated_at     = now()
            """
        )
            .param("profileId", profileId)
            .param("targetLevel", request.targetLevel?.name)
            .param("keywords", jsonMapper.writeValueAsString(keywords))
            .update()

        jdbc.sql("delete from profile_target_role_skill where profile_id = :profileId")
            .param("profileId", profileId)
            .update()

        uniqueSkillIds.forEach { skillId ->
            jdbc.sql(
                "insert into profile_target_role_skill (profile_id, canonical_skill_id) " +
                    "values (:profileId, :skillId)"
            )
                .param("profileId", profileId)
                .param("skillId", skillId)
                .update()
        }

        jdbc.sql("update profile set revision = revision + 1 where id = :profileId")
            .param("profileId", profileId)
            .update()

        return find(profileId)!!
    }

    @Transactional
    fun delete(profileId: Long) {
        requireProfileExists(profileId)
        val deleted = jdbc.sql("delete from profile_target_role where profile_id = :profileId")
            .param("profileId", profileId)
            .update()
        if (deleted > 0) {
            jdbc.sql("update profile set revision = revision + 1 where id = :profileId")
                .param("profileId", profileId)
                .update()
        }
    }

    private fun find(profileId: Long): TargetRole? {
        val row = jdbc.sql(
            "select target_level, title_keywords from profile_target_role where profile_id = :profileId"
        )
            .param("profileId", profileId)
            .query { rs, _ ->
                val rawLevel = rs.getString("target_level")
                val level = rawLevel?.let { SeniorityLevel.valueOf(it) }
                val keywords = (rs.getArray("title_keywords")?.array as? Array<*>)
                    ?.map { it.toString() }
                    ?: emptyList()
                level to keywords
            }
            .optional()
            .orElse(null)
            ?: return null

        val skills = jdbc.sql(
            """
            select cs.id, cs.name
            from profile_target_role_skill ptrs
            join canonical_skill cs on cs.id = ptrs.canonical_skill_id
            where ptrs.profile_id = :profileId
            order by cs.name
            """
        )
            .param("profileId", profileId)
            .query { rs, _ ->
                TargetRoleSkill(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                )
            }
            .list()

        return TargetRole(
            targetLevel = row.first,
            skills = skills,
            titleKeywords = row.second,
        )
    }

    private fun requireProfileExists(profileId: Long) {
        val exists = jdbc.sql("select 1 from profile where id = :id")
            .param("id", profileId)
            .query(Int::class.java)
            .optional()
            .isPresent
        if (!exists) throw UnknownProfileEntityException("No profile $profileId. POST /api/profiles first.")
    }
}
