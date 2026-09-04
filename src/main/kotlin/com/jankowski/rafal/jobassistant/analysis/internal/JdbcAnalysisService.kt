package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.AggregateGapEntry
import com.jankowski.rafal.jobassistant.analysis.AggregateGapReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.analysis.ScoringRule
import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.analysis.LearningPlanItem
import com.jankowski.rafal.jobassistant.analysis.OfferScore
import com.jankowski.rafal.jobassistant.analysis.OfferShortlist
import com.jankowski.rafal.jobassistant.analysis.ShortlistEntry
import com.jankowski.rafal.jobassistant.analysis.RequirementFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
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
    override fun start(offerId: Long, profileId: Long): Long {
        val offer = offers.findById(offerId)
            ?: throw NoSuchElementException("No job offer $offerId")
        // Fail fast rather than queueing work that cannot possibly succeed.
        profiles.require(profileId)

        val analysis = analyses.save(
            AnalysisRow(jobOfferId = offer.id, profileId = profileId, state = AnalysisState.PENDING.name)
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
    override fun latestForOffer(offerId: Long, profileId: Long?): AnalysisReport? =
        analyses.findLatestForOfferAndProfile(offerId, profileId ?: profiles.defaultProfileId())?.toReport()

    private fun AnalysisRow.toReport(): AnalysisReport {
        val analysisId = requireNotNull(id)
        // Memoised whole rather than by name: the category is needed too, and a second lookup keyed
        // the same way would double the queries to say something the first answer already knew.
        val skills = mutableMapOf<Long, CanonicalSkill?>()
        fun skillOf(skillId: Long?) = skillId?.let { skills.getOrPut(it) { catalog.findById(it) } }

        return AnalysisReport(
            id = analysisId,
            offerId = jobOfferId,
            profileId = profileId,
            state = AnalysisState.valueOf(state),
            error = error,
            matchScore = matchScore?.toDouble(),
            summaryMarkdown = summaryMd,
            requirements = requirements.findForAnalysis(analysisId).map {
                RequirementFinding(
                    id = requireNotNull(it.id),
                    rawText = it.rawText,
                    skillId = it.canonicalSkillId,
                    skillName = skillOf(it.canonicalSkillId)?.name,
                    importance = Importance.valueOf(it.importance),
                    status = RequirementStatus.valueOf(it.status),
                    evidence = it.evidence,
                    rationale = it.rationale,
                    category = skillOf(it.canonicalSkillId)?.category,
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
            // Unknown values fall back to V1 rather than the current rule: a row we cannot read
            // was certainly not scored by a rule added after it.
            scoringRule = runCatching { ScoringRule.valueOf(scoringRule) }
                .getOrDefault(ScoringRule.V1_ALL_CATEGORIES),
        )
    }

    /**
     * The offer list with each offer's current score attached.
     *
     * Two queries rather than one join, on purpose. `job_offer` and `application` belong to the
     * `offer` module and are read through [OfferService], exactly as the offer list already reads
     * them; only `analysis` is queried here, and it is queried once for every offer rather than
     * once per offer. What the issue ruled out was resolving an analysis per row from the browser,
     * and this is a single HTTP request either way.
     *
     * The fallback asks for the default profile without insisting there is one. An install with
     * no persona yet is a legitimate state, and the honest answer there is every offer listed and
     * none scored - not a 500 on the screen whose job is to list offers.
     */
    @Transactional(readOnly = true)
    override fun shortlist(profileId: Long?): OfferShortlist {
        val resolved = profileId ?: profiles.findDefaultProfileId()
        val scores = resolved?.let(::latestScoresByOffer).orEmpty()
        val entries = offers.list()
            .map { ShortlistEntry(it.offer, it.application, scores[it.offer.id]) }
            .sortedWith(ShortlistOrder)

        return OfferShortlist(
            entries = entries,
            scored = entries.count { it.score != null },
            total = entries.size,
            profileId = resolved,
        )
    }

    /**
     * The latest completed analysis of each offer for one profile, keyed by offer.
     *
     * Latest rather than best: a shortlist reports where the candidate stands now, and promoting an
     * older, higher score would show a number the current analysis no longer backs. `created_at`
     * has a microsecond floor in Postgres, so the id breaks a tie between two runs queued inside
     * the same tick rather than leaving `distinct on` to pick one.
     *
     * A DONE run whose `match_score` is null contributes no entry at all - it scored nothing, and
     * an offer with nothing to score is unscored rather than a zero.
     */
    private fun latestScoresByOffer(profileId: Long): Map<Long, OfferScore> = jdbc.sql(
        """
        select distinct on (job_offer_id)
               job_offer_id, id, match_score, scoring_rule, completed_at
        from analysis
        where profile_id = :profileId and state = 'DONE'
        order by job_offer_id, created_at desc, id desc
        """
    )
        .param("profileId", profileId)
        .query { rs, _ ->
            val score = rs.getBigDecimal("match_score")
            val completedAt = rs.getTimestamp("completed_at")?.toInstant()
            rs.getLong("job_offer_id") to score?.let {
                OfferScore(
                    analysisId = rs.getLong("id"),
                    matchScore = it.toDouble(),
                    // Same fallback as `toReport`: a rule name we cannot read was certainly not
                    // written by a rule added after the row.
                    scoringRule = runCatching { ScoringRule.valueOf(rs.getString("scoring_rule")) }
                        .getOrDefault(ScoringRule.V1_ALL_CATEGORIES),
                    completedAt = completedAt,
                )
            }
        }
        .list()
        .mapNotNull { (offerId, score) -> score?.let { offerId to it } }
        .toMap()

    /**
     * Counts demand and gaps across the latest completed analysis of each offer.
     *
     * Latest-per-offer rather than all analyses, so re-running an offer does not double its vote
     * and skew what the histogram says you should learn.
     */
    @Transactional(readOnly = true)
    override fun aggregateGaps(profileId: Long?): AggregateGapReport {
        val resolved = profileId ?: profiles.defaultProfileId()
        val entries = jdbc.sql(
            """
            with latest as (
                select distinct on (job_offer_id) id
                from analysis
                where state = 'DONE' and profile_id = :profileId
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
            .param("profileId", resolved)
            .query { rs, _ ->
                val skillId = rs.getLong("skill_id")
                val skill = catalog.findById(skillId)
                AggregateGapEntry(
                    skillId = skillId,
                    skillName = skill?.name ?: "Unknown skill $skillId",
                    demandCount = rs.getInt("demand_count"),
                    gapCount = rs.getInt("gap_count"),
                    mustHaveGapCount = rs.getInt("must_have_gap_count"),
                    category = skill?.category,
                )
            }
            .list()

        return AggregateGapReport(analysedOffers = analyses.countAnalysedOffers(resolved), entries = entries)
    }
}
