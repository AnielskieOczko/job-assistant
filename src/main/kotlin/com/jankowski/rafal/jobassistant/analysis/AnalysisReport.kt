package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
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
    val state: AnalysisState,
    val error: String?,
    val matchScore: Double?,
    val summaryMarkdown: String?,
    val requirements: List<RequirementFinding>,
    val languageRequirements: List<LanguageFinding>,
    val learningPlan: List<LearningPlanItem>,
    val createdAt: Instant,
    val completedAt: Instant?,
) {
    val mustHaves: List<RequirementFinding> get() = requirements.filter { it.importance == Importance.MUST_HAVE }
    val niceToHaves: List<RequirementFinding> get() = requirements.filter { it.importance == Importance.NICE_TO_HAVE }
    val missingMustHaves: List<RequirementFinding>
        get() = mustHaves.filter { it.status == RequirementStatus.MISSING }

    /** How the score was arrived at, so the number is never a black box. */
    val scoreExplanation: String
        get() {
            val scored = mustHaves.filter { it.status != RequirementStatus.UNRESOLVED }
            if (scored.isEmpty()) return "No resolvable must-have requirements were found."
            val met = scored.count { it.status == RequirementStatus.MET }
            val partial = scored.count { it.status == RequirementStatus.PARTIAL }
            return "($met met + 0.5 x $partial partial) / ${scored.size} must-have requirements"
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
) {
    val gapRatio: Double get() = if (demandCount == 0) 0.0 else gapCount.toDouble() / demandCount
}
