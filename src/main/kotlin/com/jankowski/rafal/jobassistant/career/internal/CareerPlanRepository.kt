package com.jankowski.rafal.jobassistant.career.internal

import com.jankowski.rafal.jobassistant.career.CareerPlan
import com.jankowski.rafal.jobassistant.career.CareerPlanSummary
import com.jankowski.rafal.jobassistant.career.PlanTopic
import com.jankowski.rafal.jobassistant.career.SkillProgress
import com.jankowski.rafal.jobassistant.career.SkillProgressState
import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

@Repository
internal class CareerPlanRepository(
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
) {

    @Transactional
    fun savePlan(
        profileId: Long,
        profileRevision: Long,
        targetLevel: SeniorityLevel?,
        targetSkills: List<String>,
        matchedOfferCount: Int,
        corpusOfferCount: Int,
        selection: CareerPlanSelection,
        modelProfile: String?,
    ): CareerPlan {
        val planId = jdbc.sql(
            """
            insert into career_plan (
                profile_id, profile_revision, target_level, target_skills,
                matched_offer_count, corpus_offer_count, dropped_topic_count, model_profile
            )
            values (
                :profileId, :profileRevision, :targetLevel,
                array(select jsonb_array_elements_text(cast(:targetSkills as jsonb))),
                :matchedOfferCount, :corpusOfferCount, :droppedTopicCount, :modelProfile
            )
            returning id
            """
        )
            .param("profileId", profileId)
            .param("profileRevision", profileRevision)
            .param("targetLevel", targetLevel?.name)
            .param("targetSkills", json.writeValueAsString(targetSkills))
            .param("matchedOfferCount", matchedOfferCount)
            .param("corpusOfferCount", corpusOfferCount)
            .param("droppedTopicCount", selection.droppedTopicCount)
            .param("modelProfile", modelProfile)
            .query(Long::class.java)
            .single()

        selection.topics.forEach { topic ->
            jdbc.sql(
                """
                insert into career_plan_topic (
                    career_plan_id, canonical_skill_id, priority, target_level,
                    why, practice_project, effort_estimate,
                    demand_offers, demand_required_offers, coverage_status
                )
                values (
                    :planId, :skillId, :priority, :targetLevel,
                    :why, :practiceProject, :effortEstimate,
                    :demandOffers, :demandRequiredOffers, :coverageStatus
                )
                """
            )
                .param("planId", planId)
                .param("skillId", topic.skillId)
                .param("priority", topic.priority)
                .param("targetLevel", topic.targetLevel.name)
                .param("why", topic.why)
                .param("practiceProject", topic.practiceProject)
                .param("effortEstimate", topic.effortEstimate)
                .param("demandOffers", topic.demandOffers)
                .param("demandRequiredOffers", topic.demandRequiredOffers)
                .param("coverageStatus", topic.coverageStatus.name)
                .update()
        }

        return findById(planId)!!
    }

    fun findCurrent(profileId: Long): CareerPlan? {
        val id = jdbc.sql(
            "select id from career_plan where profile_id = :profileId order by created_at desc, id desc limit 1"
        )
            .param("profileId", profileId)
            .query(Long::class.java)
            .optional()
            .orElse(null)
            ?: return null
        return findById(id)
    }

    fun findHistory(profileId: Long): List<CareerPlanSummary> =
        jdbc.sql(
            """
            select id, profile_id, profile_revision, target_level, target_skills,
                   matched_offer_count, corpus_offer_count, dropped_topic_count,
                   model_profile, created_at
            from career_plan
            where profile_id = :profileId
            order by created_at desc, id desc
            """
        )
            .param("profileId", profileId)
            .query { rs, _ ->
                CareerPlanSummary(
                    id = rs.getLong("id"),
                    profileId = rs.getLong("profile_id"),
                    profileRevision = rs.getLong("profile_revision"),
                    targetLevel = rs.getString("target_level")?.let { SeniorityLevel.valueOf(it) },
                    targetSkills = (rs.getArray("target_skills")?.array as? Array<*>)
                        ?.map { it.toString() }
                        ?: emptyList(),
                    matchedOfferCount = rs.getInt("matched_offer_count"),
                    corpusOfferCount = rs.getInt("corpus_offer_count"),
                    droppedTopicCount = rs.getInt("dropped_topic_count"),
                    modelProfile = rs.getString("model_profile"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .list()

    fun findById(planId: Long): CareerPlan? {
        val header = jdbc.sql(
            """
            select id, profile_id, profile_revision, target_level, target_skills,
                   matched_offer_count, corpus_offer_count, dropped_topic_count,
                   model_profile, created_at
            from career_plan
            where id = :planId
            """
        )
            .param("planId", planId)
            .query { rs, _ ->
                CareerPlanSummary(
                    id = rs.getLong("id"),
                    profileId = rs.getLong("profile_id"),
                    profileRevision = rs.getLong("profile_revision"),
                    targetLevel = rs.getString("target_level")?.let { SeniorityLevel.valueOf(it) },
                    targetSkills = (rs.getArray("target_skills")?.array as? Array<*>)
                        ?.map { it.toString() }
                        ?: emptyList(),
                    matchedOfferCount = rs.getInt("matched_offer_count"),
                    corpusOfferCount = rs.getInt("corpus_offer_count"),
                    droppedTopicCount = rs.getInt("dropped_topic_count"),
                    modelProfile = rs.getString("model_profile"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .optional()
            .orElse(null)
            ?: return null

        val topics = jdbc.sql(
            """
            select t.id, t.canonical_skill_id, cs.name as skill_name, t.priority, t.target_level,
                   t.why, t.practice_project, t.effort_estimate,
                   t.demand_offers, t.demand_required_offers, t.coverage_status,
                   coalesce(sp.state, 'NOT_STARTED') as progress_state
            from career_plan_topic t
            join canonical_skill cs on cs.id = t.canonical_skill_id
            left join skill_progress sp
              on sp.profile_id = :profileId and sp.canonical_skill_id = t.canonical_skill_id
            where t.career_plan_id = :planId
            order by t.priority asc, t.id asc
            """
        )
            .param("profileId", header.profileId)
            .param("planId", planId)
            .query { rs, _ ->
                PlanTopic(
                    id = rs.getLong("id"),
                    skillId = rs.getLong("canonical_skill_id"),
                    skillName = rs.getString("skill_name"),
                    priority = rs.getInt("priority"),
                    targetLevel = SeniorityLevel.valueOf(rs.getString("target_level")),
                    why = rs.getString("why"),
                    practiceProject = rs.getString("practice_project"),
                    effortEstimate = rs.getString("effort_estimate"),
                    demandOffers = rs.getInt("demand_offers"),
                    demandRequiredOffers = rs.getInt("demand_required_offers"),
                    coverageStatus = CoverageStatus.valueOf(rs.getString("coverage_status")),
                    progressState = SkillProgressState.valueOf(rs.getString("progress_state")),
                )
            }
            .list()

        return CareerPlan(
            id = header.id,
            profileId = header.profileId,
            profileRevision = header.profileRevision,
            targetLevel = header.targetLevel,
            targetSkills = header.targetSkills,
            matchedOfferCount = header.matchedOfferCount,
            corpusOfferCount = header.corpusOfferCount,
            droppedTopicCount = header.droppedTopicCount,
            modelProfile = header.modelProfile,
            createdAt = header.createdAt,
            topics = topics,
        )
    }

    @Transactional
    fun upsertProgress(profileId: Long, skillId: Long, state: SkillProgressState): SkillProgress {
        val now = Instant.now()
        jdbc.sql(
            """
            insert into skill_progress (profile_id, canonical_skill_id, state, updated_at)
            values (:profileId, :skillId, :state, :updatedAt)
            on conflict (profile_id, canonical_skill_id) do update set
                state      = excluded.state,
                updated_at = excluded.updated_at
            """
        )
            .param("profileId", profileId)
            .param("skillId", skillId)
            .param("state", state.name)
            .param("updatedAt", java.sql.Timestamp.from(now))
            .update()

        return SkillProgress(
            profileId = profileId,
            skillId = skillId,
            state = state,
            updatedAt = now,
        )
    }
}
