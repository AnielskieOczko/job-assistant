package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.AggregateGapEntry
import com.jankowski.rafal.jobassistant.analysis.AggregateGapReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.analysis.LearningPlanItem
import com.jankowski.rafal.jobassistant.analysis.RequirementFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
internal class JdbcAnalysisService(
    private val analyses: AnalysisRepository,
    private val requirements: OfferRequirementRepository,
    private val languageRequirements: LanguageRequirementRepository,
    private val planItems: LearningPlanItemRepository,
    private val offers: OfferService,
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
    private val runner: AnalysisRunner,
    private val jdbc: JdbcClient,
) : AnalysisService {

    @Transactional
    override fun start(offerId: Long): Long {
        val offer = offers.findById(offerId)
            ?: throw NoSuchElementException("No job offer $offerId")
        // Fail fast rather than queueing work that cannot possibly succeed.
        profiles.require()

        val analysis = analyses.save(
            AnalysisRow(jobOfferId = offer.id, state = AnalysisState.PENDING.name)
        )
        val analysisId = requireNotNull(analysis.id)

        offers.updateStatus(offerId, ApplicationStatus.ANALYZED)

        // Only dispatch once the row is actually committed, otherwise the worker can start and
        // fail to find the analysis it was handed.
        afterCommit { runner.run(analysisId) }
        return analysisId
    }

    private fun afterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = action()
                }
            )
        } else {
            action()
        }
    }

    @Transactional(readOnly = true)
    override fun findReport(analysisId: Long): AnalysisReport? =
        analyses.findById(analysisId).orElse(null)?.toReport()

    @Transactional(readOnly = true)
    override fun latestForOffer(offerId: Long): AnalysisReport? =
        analyses.findLatestForOffer(offerId)?.toReport()

    private fun AnalysisRow.toReport(): AnalysisReport {
        val analysisId = requireNotNull(id)
        val skillNames = mutableMapOf<Long, String?>()
        fun nameOf(skillId: Long?) = skillId?.let {
            skillNames.getOrPut(it) { catalog.findById(it)?.name }
        }

        return AnalysisReport(
            id = analysisId,
            offerId = jobOfferId,
            state = AnalysisState.valueOf(state),
            error = error,
            matchScore = matchScore?.toDouble(),
            summaryMarkdown = summaryMd,
            requirements = requirements.findForAnalysis(analysisId).map {
                RequirementFinding(
                    id = requireNotNull(it.id),
                    rawText = it.rawText,
                    skillId = it.canonicalSkillId,
                    skillName = nameOf(it.canonicalSkillId),
                    importance = Importance.valueOf(it.importance),
                    status = RequirementStatus.valueOf(it.status),
                    evidence = it.evidence,
                    rationale = it.rationale,
                )
            },
            languageRequirements = languageRequirements.findForAnalysis(analysisId).map {
                LanguageFinding(
                    language = it.language,
                    requiredLevel = LanguageLevel.valueOf(it.requiredLevel),
                    heldLevel = it.heldLevel?.let(LanguageLevel::valueOf),
                    status = RequirementStatus.valueOf(it.status),
                )
            },
            learningPlan = planItems.findForAnalysis(analysisId).map {
                LearningPlanItem(
                    skillId = it.canonicalSkillId,
                    skillName = it.skillName,
                    why = it.why,
                    practiceProject = it.practiceProject,
                    effortEstimate = it.effortEstimate,
                    priority = it.priority,
                )
            },
            createdAt = createdAt,
            completedAt = completedAt,
            profileRevision = profileRevision,
        )
    }

    /**
     * Counts demand and gaps across the latest completed analysis of each offer.
     *
     * Latest-per-offer rather than all analyses, so re-running an offer does not double its vote
     * and skew what the histogram says you should learn.
     */
    @Transactional(readOnly = true)
    override fun aggregateGaps(): AggregateGapReport {
        val entries = jdbc.sql(
            """
            with latest as (
                select distinct on (job_offer_id) id
                from analysis
                where state = 'DONE'
                order by job_offer_id, created_at desc
            )
            select r.canonical_skill_id                                            as skill_id,
                   count(*)                                                        as demand_count,
                   count(*) filter (where r.status in ('MISSING', 'PARTIAL'))      as gap_count,
                   count(*) filter (where r.status in ('MISSING', 'PARTIAL')
                                      and r.importance = 'MUST_HAVE')              as must_have_gap_count
            from offer_requirement r
            join latest l on l.id = r.analysis_id
            where r.canonical_skill_id is not null
            group by r.canonical_skill_id
            order by must_have_gap_count desc, gap_count desc, demand_count desc
            """
        )
            .query { rs, _ ->
                val skillId = rs.getLong("skill_id")
                AggregateGapEntry(
                    skillId = skillId,
                    skillName = catalog.findById(skillId)?.name ?: "Unknown skill $skillId",
                    demandCount = rs.getInt("demand_count"),
                    gapCount = rs.getInt("gap_count"),
                    mustHaveGapCount = rs.getInt("must_have_gap_count"),
                )
            }
            .list()

        return AggregateGapReport(analysedOffers = analyses.countAnalysedOffers(), entries = entries)
    }
}
