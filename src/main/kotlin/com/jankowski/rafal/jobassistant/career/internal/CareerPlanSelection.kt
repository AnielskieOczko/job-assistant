package com.jankowski.rafal.jobassistant.career.internal

import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.market.DemandEntry

internal data class SelectedPlanTopic(
    val skillId: Long,
    val skillName: String,
    val priority: Int,
    val targetLevel: SeniorityLevel,
    val why: String,
    val practiceProject: String?,
    val effortEstimate: String?,
    val demandOffers: Int,
    val demandRequiredOffers: Int,
    val coverageStatus: CoverageStatus,
)

internal data class CareerPlanSelection(
    val topics: List<SelectedPlanTopic>,
    val droppedTopicCount: Int,
) {
    companion object {
        /**
         * Re-resolves every model-authored topic against [catalog] and requires it to exist in
         * [evidence]. Anything unbacked or duplicated is dropped and counted.
         *
         * `targetLevel` is always derived from the counted modal seniority in [DemandEntry.seniorityMix]
         * (falling back to [fallbackLevel]), never from a model-supplied string.
         */
        fun from(
            authored: AuthoredCareerPlan,
            evidence: List<DemandEntry>,
            catalog: SkillCatalog,
            fallbackLevel: SeniorityLevel,
        ): CareerPlanSelection {
            val evidenceBySkillId = evidence.associateBy { it.skillId }
            val seen = mutableSetOf<Long>()
            val selected = mutableListOf<SelectedPlanTopic>()
            var dropped = 0

            authored.safeTopics()
                .sortedBy { it.priority }
                .forEach { topic ->
                    val rawSkill = topic.safeSkill()
                    val resolved = rawSkill.takeIf { it.isNotEmpty() }?.let { catalog.resolve(it) }
                    val demand = resolved?.let { evidenceBySkillId[it.id] }
                    if (resolved == null || demand == null || !seen.add(resolved.id)) {
                        dropped++
                        return@forEach
                    }

                    selected += SelectedPlanTopic(
                        skillId = resolved.id,
                        skillName = resolved.name,
                        priority = selected.size + 1,
                        targetLevel = modalSeniority(demand.seniorityMix, fallbackLevel),
                        why = topic.safeWhy().ifEmpty { "Requested by ${demand.offers} in-scope offers." },
                        practiceProject = topic.practiceProject?.trim()?.takeIf { it.isNotEmpty() },
                        effortEstimate = topic.effortEstimate?.trim()?.takeIf { it.isNotEmpty() },
                        demandOffers = demand.offers,
                        demandRequiredOffers = demand.requiredOffers,
                        coverageStatus = demand.status,
                    )
                }

            return CareerPlanSelection(topics = selected, droppedTopicCount = dropped)
        }

        private fun modalSeniority(
            mix: Map<SeniorityLevel, Int>,
            fallback: SeniorityLevel,
        ): SeniorityLevel {
            if (mix.isEmpty()) return fallback
            return mix.entries
                .maxWithOrNull(compareBy<Map.Entry<SeniorityLevel, Int>> { it.value }.thenBy { it.key.ordinal })
                ?.key
                ?: fallback
        }
    }
}
