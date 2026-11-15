package com.jankowski.rafal.jobassistant.career

import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel
import java.time.Instant

enum class SkillProgressState {
    NOT_STARTED,
    LEARNING,
    READY_TO_DECLARE,
}

data class SkillProgress(
    val profileId: Long,
    val skillId: Long,
    val state: SkillProgressState,
    val updatedAt: Instant,
)

data class PlanTopic(
    val id: Long,
    val skillId: Long,
    val skillName: String,
    val priority: Int,
    val targetLevel: SeniorityLevel,
    val why: String,
    val practiceProject: String? = null,
    val effortEstimate: String? = null,
    val demandOffers: Int,
    val demandRequiredOffers: Int,
    val coverageStatus: CoverageStatus,
    val progressState: SkillProgressState = SkillProgressState.NOT_STARTED,
)

data class CareerPlan(
    val id: Long,
    val profileId: Long,
    val profileRevision: Long,
    val targetLevel: SeniorityLevel? = null,
    val targetSkills: List<String> = emptyList(),
    val matchedOfferCount: Int,
    val corpusOfferCount: Int,
    val droppedTopicCount: Int,
    val modelProfile: String? = null,
    val createdAt: Instant,
    val topics: List<PlanTopic> = emptyList(),
)

data class CareerPlanSummary(
    val id: Long,
    val profileId: Long,
    val profileRevision: Long,
    val targetLevel: SeniorityLevel? = null,
    val targetSkills: List<String> = emptyList(),
    val matchedOfferCount: Int,
    val corpusOfferCount: Int,
    val droppedTopicCount: Int,
    val modelProfile: String? = null,
    val createdAt: Instant,
)

interface CareerService {
    fun generate(requestedProfileId: Long?): CareerPlan
    fun current(requestedProfileId: Long?): CareerPlan?
    fun history(requestedProfileId: Long?): List<CareerPlanSummary>
    fun get(planId: Long): CareerPlan?
    fun updateProgress(requestedProfileId: Long?, skillId: Long, state: SkillProgressState): SkillProgress
}
