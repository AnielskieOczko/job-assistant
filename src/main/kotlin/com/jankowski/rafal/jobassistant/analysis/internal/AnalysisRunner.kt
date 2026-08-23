package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Runs one analysis end to end: extract, match, narrate.
 *
 * Only steps 1 and 3 involve a model. Step 2 - the part that decides what you are missing - is
 * plain Kotlin over the catalog, so the verdict is reproducible and the model never gets a vote
 * on whether you have a skill.
 */
@Component
internal class AnalysisRunner(
    private val analyses: AnalysisRepository,
    private val requirements: OfferRequirementRepository,
    private val languageRequirements: LanguageRequirementRepository,
    private val planItems: LearningPlanItemRepository,
    private val offers: OfferService,
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
    private val aiServices: AiServiceFactory,
    private val models: ChatModelRegistry,
) {

    private val log = LoggerFactory.getLogger(AnalysisRunner::class.java)

    @Async("analysisExecutor")
    fun run(analysisId: Long) {
        try {
            execute(analysisId)
        } catch (failure: Exception) {
            log.warn("Analysis {} failed", analysisId, failure)
            markFailed(analysisId, failure)
        }
    }

    private fun execute(analysisId: Long) {
        val analysis = analyses.findById(analysisId).orElseThrow {
            IllegalStateException("No analysis $analysisId")
        }
        val offer = offers.findById(analysis.jobOfferId)
            ?: throw IllegalStateException("Analysis $analysisId points at missing offer ${analysis.jobOfferId}")
        val profile = profiles.require()

        transition(analysisId, AnalysisState.EXTRACTING, startedAt = Instant.now())

        val skills = catalog.findAll()
        val extracted = aiServices
            .create(OfferExtractor::class.java, LlmTask.EXTRACTION)
            .extract(offer.rawText, AnalysisPromptFormatter.catalogListing(skills))

        offers.describe(
            offerId = offer.id,
            title = extracted.title.ifBlank { null },
            company = extracted.company.ifBlank { null },
            seniority = extracted.seniority.ifBlank { null },
            detectedLanguage = extracted.detectedLanguage.ifBlank { null },
        )

        transition(analysisId, AnalysisState.MATCHING)

        val resolved = resolveRequirements(extracted)
        val coverage = catalog.coverageFor(profile.heldSkillIds)
        val evidence = EvidenceDescriber(profile, coverage, catalog)
        val matched = RequirementMatcher.match(resolved, coverage, evidence::describe)
        val score = RequirementMatcher.score(matched)

        val languageFindings = RequirementMatcher.matchLanguages(
            required = extracted.languageRequirements.mapNotNull { it.toRequirement() },
            heldLevel = profile::languageLevel,
        )

        saveFindings(analysisId, matched, languageFindings)

        transition(analysisId, AnalysisState.NARRATING)

        val narrative = aiServices
            .create(ReportNarrator::class.java, LlmTask.NARRATIVE)
            .narrate(
                roleTitle = extracted.title.ifBlank { offer.displayTitle },
                company = extracted.company.ifBlank { "unknown" },
                matchScore = AnalysisPromptFormatter.score(score),
                scoreExplanation = explainScore(matched),
                language = "English",
                mustHaves = AnalysisPromptFormatter.requirements(matched, Importance.MUST_HAVE),
                niceToHaves = AnalysisPromptFormatter.requirements(matched, Importance.NICE_TO_HAVE),
                languageRequirements = AnalysisPromptFormatter.languages(languageFindings),
                unresolved = AnalysisPromptFormatter.unresolved(matched),
            )

        saveNarrative(analysisId, narrative, score)
    }

    /**
     * Maps each extracted requirement onto a catalog entry, preferring the name the model chose
     * but falling back to the offer's own wording. Anything still unmatched is queued for review
     * rather than dropped, so the catalog grows from real offers.
     */
    private fun resolveRequirements(extracted: ExtractedOffer): List<ResolvedRequirement> =
        extracted.requirements.map { requirement ->
            val skill = requirement.catalogSkill.ifBlank { null }?.let { catalog.resolve(it) }
                ?: catalog.resolve(requirement.rawText)

            if (skill == null) {
                catalog.recordUnmatched(requirement.catalogSkill.ifBlank { requirement.rawText })
            }

            ResolvedRequirement(
                rawText = requirement.rawText,
                skillId = skill?.id,
                skillName = skill?.name,
                importance = parseImportance(requirement.importance),
                rationale = requirement.rationale.ifBlank { null },
            )
        }

    private fun parseImportance(raw: String): Importance =
        runCatching { Importance.valueOf(raw.trim().uppercase()) }.getOrDefault(Importance.NICE_TO_HAVE)

    private fun ExtractedLanguageRequirement.toRequirement(): Pair<String, LanguageLevel>? {
        if (language.isBlank()) return null
        val level = runCatching { LanguageLevel.valueOf(level.trim().uppercase()) }
            .getOrDefault(LanguageLevel.B2)
        return language.trim() to level
    }

    private fun explainScore(matched: List<MatchedRequirement>): String {
        val scoreable = matched.filter {
            it.importance == Importance.MUST_HAVE && it.status != RequirementStatus.UNRESOLVED
        }
        if (scoreable.isEmpty()) return "no resolvable must-have requirements"
        val met = scoreable.count { it.status == RequirementStatus.MET }
        val partial = scoreable.count { it.status == RequirementStatus.PARTIAL }
        return "($met met + 0.5 x $partial partial) / ${scoreable.size} must-haves"
    }

    @Transactional
    internal fun saveFindings(
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

    @Transactional
    internal fun saveNarrative(analysisId: Long, narrative: ReportNarrative, score: Double?) {
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
    internal fun transition(analysisId: Long, state: AnalysisState, startedAt: Instant? = null) {
        val row = analyses.findById(analysisId).orElseThrow()
        analyses.save(
            row.copy(
                state = state.name,
                startedAt = startedAt ?: row.startedAt,
                modelProfile = row.modelProfile ?: models.profileNameFor(LlmTask.EXTRACTION),
            )
        )
    }

    @Transactional
    internal fun markFailed(analysisId: Long, failure: Throwable) {
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
