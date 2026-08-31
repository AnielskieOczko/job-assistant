package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Everything an analysis run writes down, and the only place it is written.
 *
 * A separate bean rather than methods on [AnalysisRunner], and that is the entire point: transaction
 * management here is proxy-based, so a `@Transactional` method the runner called on itself was never
 * intercepted and never opened a transaction. The annotations read as guarantees and provided none -
 * [saveFindings] in particular looked atomic while writing its two collections separately, so a
 * failure between them left a report with requirements and no language findings, a state nothing
 * downstream expects. Crossing a bean boundary is what makes the annotations mean what they say.
 *
 * Default propagation, deliberately, unlike
 * [LlmCallAuditor][com.jankowski.rafal.jobassistant.llm.internal.LlmCallAuditor]: an audit row has to
 * survive the rollback of the work that produced it, because what the model returned is exactly what
 * you need when a run fails. These writes are the opposite - they *describe* the work, so they should
 * roll back with it.
 *
 * Writes only. No model call, no prompt, no orchestration: everything handed to it is already
 * computed.
 */
@Component
internal class AnalysisJournal(
    private val analyses: AnalysisRepository,
    private val requirements: OfferRequirementRepository,
    private val languageRequirements: LanguageRequirementRepository,
    private val planItems: LearningPlanItemRepository,
    private val catalog: SkillCatalog,
    private val models: ChatModelRegistry,
) {

    @Transactional
    fun transition(analysisId: Long, state: AnalysisState, startedAt: Instant? = null) {
        val row = analyses.findById(analysisId).orElseThrow()
        analyses.save(
            row.copy(
                state = state.name,
                startedAt = startedAt ?: row.startedAt,
                modelProfile = row.modelProfile ?: models.profileNameFor(LlmTask.EXTRACTION),
            )
        )
    }

    /** Records the profile revision the run actually read, before any model work. */
    @Transactional
    fun recordProfileRevision(analysisId: Long, revision: Long) {
        val row = analyses.findById(analysisId).orElseThrow()
        analyses.save(row.copy(profileRevision = revision))
    }

    /**
     * The requirements and the language findings of one run, in **one** transaction.
     *
     * That is the behaviour this class exists to provide. Written separately they could disagree:
     * a failure between the two left an analysis carrying requirements whose language findings had
     * never been saved, and the gap report has no way to represent "half read".
     */
    @Transactional
    fun saveFindings(
        analysisId: Long,
        matched: List<MatchedRequirement>,
        languages: List<LanguageFinding>,
    ) {
        requirements.saveAll(
            matched.mapIndexed { index, requirement ->
                OfferRequirementRow(
                    analysisId = analysisId,
                    rawText = requirement.rawText,
                    canonicalSkillId = requirement.skillId,
                    importance = requirement.importance.name,
                    status = requirement.status.name,
                    evidence = requirement.evidence,
                    rationale = requirement.rationale,
                    displayOrder = index,
                )
            }
        )
        languageRequirements.saveAll(
            languages.map {
                LanguageRequirementRow(
                    analysisId = analysisId,
                    language = it.language,
                    requiredLevel = it.requiredLevel.name,
                    heldLevel = it.heldLevel?.name,
                    status = it.status.name,
                )
            }
        )
    }

    /**
     * The learning plan and the completed analysis together, so a run can never reach DONE with its
     * plan missing.
     *
     * A plan item naming a skill the catalog cannot place keeps its name and stores a null link,
     * rather than being dropped: the advice is still worth reading, it just has nothing to link to.
     */
    @Transactional
    fun saveNarrative(analysisId: Long, narrative: ReportNarrative, score: Double?) {
        planItems.saveAll(
            narrative.learningPlan.mapIndexed { index, item ->
                LearningPlanItemRow(
                    analysisId = analysisId,
                    canonicalSkillId = catalog.resolve(item.skill)?.id,
                    skillName = item.skill,
                    why = item.why,
                    practiceProject = item.practiceProject.ifBlank { null },
                    effortEstimate = item.effortEstimate.ifBlank { null },
                    priority = index,
                )
            }
        )

        val row = analyses.findById(analysisId).orElseThrow()
        analyses.save(
            row.copy(
                state = AnalysisState.DONE.name,
                summaryMd = narrative.summaryMarkdown,
                matchScore = score?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
                completedAt = Instant.now(),
            )
        )
    }

    @Transactional
    fun markFailed(analysisId: Long, failure: Throwable) {
        analyses.findById(analysisId).ifPresent { row ->
            analyses.save(
                row.copy(
                    state = AnalysisState.FAILED.name,
                    error = "${failure::class.simpleName}: ${failure.message}".take(2000),
                    completedAt = Instant.now(),
                )
            )
        }
    }
}
