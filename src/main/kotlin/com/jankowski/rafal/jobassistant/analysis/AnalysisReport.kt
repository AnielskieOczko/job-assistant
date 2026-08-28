package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import java.time.Instant

enum class AnalysisState {
    PENDING, EXTRACTING, MATCHING, NARRATING, DONE, FAILED;

    val isTerminal: Boolean get() = this == DONE || this == FAILED
}

enum class Importance { MUST_HAVE, NICE_TO_HAVE }

/**
 * Status of a single requirement. Mirrors [CoverageStatus] but adds [UNRESOLVED] for requirements
 * whose phrasing matched nothing in the catalog - those are a gap in the *catalog*, not
 * necessarily in the candidate, and conflating the two would quietly distort the score.
 */
enum class RequirementStatus { MET, PARTIAL, MISSING, UNRESOLVED }

data class RequirementFinding(
    val id: Long,
    val rawText: String,
    val skillId: Long?,
    val skillName: String?,
    val importance: Importance,
    val status: RequirementStatus,
    /** Which profile record backs a MET/PARTIAL verdict. Null when nothing does. */
    val evidence: String?,
    val rationale: String?,
    /**
     * The catalog category, or null when the phrasing resolved to nothing.
     *
     * Present so a reader can tell a soft requirement from a scored gap. Under
     * [ScoringRule.V2_SOFT_EXCLUDED] a `SOFT` finding is reported but sits outside the score.
     */
    val category: SkillCategory? = null,
)

data class LanguageFinding(
    val language: String,
    val requiredLevel: LanguageLevel,
    val heldLevel: LanguageLevel?,
    val status: RequirementStatus,
)

data class LearningPlanItem(
    val skillId: Long?,
    val skillName: String,
    val why: String,
    val practiceProject: String?,
    val effortEstimate: String?,
    val priority: Int,
)

/**
 * The report. The numbers and statuses here are computed in Kotlin from the catalog and profile;
 * only [summaryMarkdown] and [learningPlan] prose come from a model.
 */
data class AnalysisReport(
    val id: Long,
    val offerId: Long,
    /** Which profile this analysis was run against. */
    val profileId: Long = 0,
    val state: AnalysisState,
    val error: String?,
    val matchScore: Double?,
    val summaryMarkdown: String?,
    val requirements: List<RequirementFinding>,
    val languageRequirements: List<LanguageFinding>,
    val learningPlan: List<LearningPlanItem>,
    val createdAt: Instant,
    val completedAt: Instant?,
    /**
     * Profile revision this analysis was computed from. When it trails the profile's current
     * revision the findings have been overtaken by an edit - a gap report telling you to learn
     * something you have since added is worse than no report at all.
     */
    val profileRevision: Long? = null,
    /**
     * Which rule produced [matchScore].
     *
     * Defaults to V1 because that is what every row predating the change holds, and a report has to
     * explain itself in the terms it was actually scored by.
     */
    val scoringRule: ScoringRule = ScoringRule.V1_ALL_CATEGORIES,
) {
    val mustHaves: List<RequirementFinding> get() = requirements.filter { it.importance == Importance.MUST_HAVE }
    val niceToHaves: List<RequirementFinding> get() = requirements.filter { it.importance == Importance.NICE_TO_HAVE }
    val missingMustHaves: List<RequirementFinding>
        get() = mustHaves.filter { it.status == RequirementStatus.MISSING }

    /**
     * The must-haves [matchScore] was computed over.
     *
     * Private: it is how [scoreExplanation] is derived, not a second copy of the requirement list
     * for the wire. [reportedNotScored] is the part a reader actually needs.
     *
     * Branches on [scoringRule] rather than always applying the current rule. `matchScore` is read
     * from storage while this recomputes its denominator from the stored requirements, so applying
     * today's rule to yesterday's score would make a report contradict its own explanation.
     */
    private val scoredRequirements: List<RequirementFinding>
        get() = mustHaves
            .filter { it.status != RequirementStatus.UNRESOLVED }
            .filter {
                scoringRule != ScoringRule.V2_SOFT_EXCLUDED || it.category != SkillCategory.SOFT
            }

    /** Requirements shown in the report but deliberately left out of the score. */
    val reportedNotScored: List<RequirementFinding>
        get() = if (scoringRule != ScoringRule.V2_SOFT_EXCLUDED) emptyList()
        else requirements.filter { it.category == SkillCategory.SOFT }

    /** How the score was arrived at, so the number is never a black box. */
    val scoreExplanation: String
        get() {
            val scored = scoredRequirements
            if (scored.isEmpty()) return "No resolvable must-have requirements were found."
            val met = scored.count { it.status == RequirementStatus.MET }
            val partial = scored.count { it.status == RequirementStatus.PARTIAL }
            val noun = when (scoringRule) {
                ScoringRule.V2_SOFT_EXCLUDED -> "technical must-have requirements"
                ScoringRule.V1_ALL_CATEGORIES -> "must-have requirements"
            }
            return "($met met + 0.5 x $partial partial) / ${scored.size} $noun"
        }
}

/** Cross-offer view: what to actually learn, as opposed to what one offer happened to ask for. */
data class AggregateGapReport(
    val analysedOffers: Int,
    val entries: List<AggregateGapEntry>,
)

data class AggregateGapEntry(
    val skillId: Long,
    val skillName: String,
    /** How many analysed offers asked for this skill at all. */
    val demandCount: Int,
    /** Of those, how many you do not currently cover. */
    val gapCount: Int,
    val mustHaveGapCount: Int,
    /** Lets the cross-offer view separate a soft-skill row from a scored technical gap. */
    val category: SkillCategory? = null,
) {
    val gapRatio: Double get() = if (demandCount == 0) 0.0 else gapCount.toDouble() / demandCount
}
