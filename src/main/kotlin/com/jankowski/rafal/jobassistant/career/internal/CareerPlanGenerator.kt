package com.jankowski.rafal.jobassistant.career.internal

import com.jankowski.rafal.jobassistant.career.CareerPlan
import com.jankowski.rafal.jobassistant.career.CareerPlanSummary
import com.jankowski.rafal.jobassistant.career.CareerService
import com.jankowski.rafal.jobassistant.career.SkillProgress
import com.jankowski.rafal.jobassistant.career.SkillProgressState
import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmCallScope
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.market.DemandRanking
import com.jankowski.rafal.jobassistant.market.MarketInsights
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProfileTargetRole
import org.springframework.stereotype.Service

@Service
internal class CareerPlanGenerator(
    private val profiles: ProfileService,
    private val targetRoles: ProfileTargetRole,
    private val market: MarketInsights,
    private val catalog: SkillCatalog,
    private val aiFactory: AiServiceFactory,
    private val modelRegistry: ChatModelRegistry,
    private val repository: CareerPlanRepository,
) : CareerService {

    private val author: CareerPlanAuthor by lazy {
        aiFactory.create(CareerPlanAuthor::class.java, LlmTask.CAREER_PLAN)
    }

    override fun generate(requestedProfileId: Long?): CareerPlan {
        val profileId = requestedProfileId ?: profiles.defaultProfileId()
        val profile = profiles.require(profileId)
        val target = targetRoles.of(profileId)

        val demand = market.demand(profileId, DemandRanking.UNMET, EVIDENCE_LIMIT)
        val unmet = demand.entries.filter { it.status != CoverageStatus.MET }
        val evidence = unmet.ifEmpty { demand.entries }
        check(evidence.isNotEmpty()) {
            "No market demand entries in scope to build a career plan."
        }

        val targetRoleSummary = buildString {
            append("Level: ").append(target?.targetLevel?.name ?: "UNSPECIFIED").append('\n')
            append("Scope skills: ").append(demand.scope.scopeSkills.joinToString(", ")).append('\n')
            if (!target?.titleKeywords.isNullOrEmpty()) {
                append("Title keywords: ").append(target.titleKeywords.joinToString(", "))
            }
        }.trim()

        val evidenceTable = evidence.joinToString("\n") { entry ->
            val mix = if (entry.seniorityMix.isEmpty()) {
                "none"
            } else {
                entry.seniorityMix.entries.joinToString(", ") { "${it.key.name}:${it.value}" }
            }
            "- ${entry.skillName} (${entry.status}): ${entry.offers} offers (${entry.requiredOffers} required), seniority=[$mix]"
        }

        val careerGoal = profile.details.careerGoal?.trim().orEmpty().ifEmpty { "None stated" }

        // Model call stays outside any database transaction.
        val authored = LlmCallScope.forProfile(profileId) {
            author.author(targetRoleSummary, careerGoal, evidenceTable)
        }

        val selection = CareerPlanSelection.from(
            authored = authored,
            evidence = evidence,
            catalog = catalog,
            fallbackLevel = target?.targetLevel ?: SeniorityLevel.REGULAR,
        )

        return repository.savePlan(
            profileId = profileId,
            profileRevision = profile.revision,
            targetLevel = target?.targetLevel,
            targetSkills = demand.scope.scopeSkills,
            matchedOfferCount = demand.scope.offersInScope,
            corpusOfferCount = demand.scope.corpusOffers,
            selection = selection,
            modelProfile = modelRegistry.profileNameFor(LlmTask.CAREER_PLAN),
        )
    }

    override fun current(requestedProfileId: Long?): CareerPlan? {
        val profileId = requestedProfileId ?: profiles.findDefaultProfileId() ?: return null
        return repository.findCurrent(profileId)
    }

    override fun history(requestedProfileId: Long?): List<CareerPlanSummary> {
        val profileId = requestedProfileId ?: profiles.findDefaultProfileId() ?: return emptyList()
        return repository.findHistory(profileId)
    }

    override fun get(planId: Long): CareerPlan? = repository.findById(planId)

    override fun updateProgress(requestedProfileId: Long?, skillId: Long, state: SkillProgressState): SkillProgress {
        val profileId = requestedProfileId ?: profiles.defaultProfileId()
        profiles.require(profileId)
        requireNotNull(catalog.findById(skillId)) { "Unknown catalog skill id: $skillId" }
        return repository.upsertProgress(profileId, skillId, state)
    }

    private companion object {
        const val EVIDENCE_LIMIT = 30
    }
}
