package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.profile.LanguageLevel

/** A requirement after its catalog lookup, before it has been compared to the profile. */
internal data class ResolvedRequirement(
    val rawText: String,
    val skillId: Long?,
    val skillName: String?,
    val importance: Importance,
    val rationale: String?,
    /** Null when the term resolved to nothing. Trailing and defaulted so callers need not care. */
    val category: SkillCategory? = null,
)

internal data class MatchedRequirement(
    val rawText: String,
    val skillId: Long?,
    val skillName: String?,
    val importance: Importance,
    val status: RequirementStatus,
    val evidence: String?,
    val rationale: String?,
    val category: SkillCategory? = null,
)

/**
 * The deterministic core of the gap report.
 *
 * No model is involved: given the same requirements, coverage and profile, this produces byte
 * identical output every time. That is what makes "you match 7 of 10 must-haves" mean the same
 * thing on Tuesday as it did on Monday, and what makes the whole thing unit-testable.
 */
internal object RequirementMatcher {

    fun match(
        requirements: List<ResolvedRequirement>,
        coverage: SkillCoverage,
        describeEvidence: (skillId: Long, status: RequirementStatus) -> String?,
    ): List<MatchedRequirement> = requirements.map { requirement ->
        val skillId = requirement.skillId

        val status = when {
            skillId == null -> RequirementStatus.UNRESOLVED
            else -> when (coverage.statusFor(skillId)) {
                CoverageStatus.MET -> RequirementStatus.MET
                CoverageStatus.PARTIAL -> RequirementStatus.PARTIAL
                CoverageStatus.MISSING -> RequirementStatus.MISSING
            }
        }

        MatchedRequirement(
            rawText = requirement.rawText,
            skillId = skillId,
            skillName = requirement.skillName,
            importance = requirement.importance,
            status = status,
            evidence = if (skillId != null && (status == RequirementStatus.MET || status == RequirementStatus.PARTIAL)) {
                describeEvidence(skillId, status)
            } else {
                null
            },
            rationale = requirement.rationale,
            category = requirement.category,
        )
    }

    /**
     * Which requirements the score is computed over. The denominator, in one place.
     *
     * Nice-to-haves are excluded because including them lets a job with a long wish list score
     * better than a focused one you are equally qualified for. UNRESOLVED requirements are excluded
     * because they say nothing about the candidate - counting them as missing would punish gaps in
     * our own catalog.
     *
     * **SOFT skills are excluded, but still reported.** A "Communication" must-have the profile
     * does not declare is a real thing the offer asked for and belongs in the gap report; counting
     * it makes the number answer a question it cannot answer. The score says how *technically*
     * qualified someone is, and no catalog lookup can tell you whether they communicate well.
     *
     * Public so the narrator's explanation is computed from the same list the score is. These two
     * were separately implemented filters until the rule changed and they disagreed.
     */
    fun scoreable(matched: List<MatchedRequirement>): List<MatchedRequirement> = matched.filter {
        it.importance == Importance.MUST_HAVE &&
            it.status != RequirementStatus.UNRESOLVED &&
            it.category != SkillCategory.SOFT
    }

    /**
     * `(met + 0.5 * partial) / total` over [scoreable].
     *
     * Returns null when there is nothing scoreable, rather than a misleading 0.0 or 1.0.
     */
    fun score(matched: List<MatchedRequirement>): Double? {
        val scoreable = scoreable(matched)
        if (scoreable.isEmpty()) return null

        val met = scoreable.count { it.status == RequirementStatus.MET }
        val partial = scoreable.count { it.status == RequirementStatus.PARTIAL }
        return (met + 0.5 * partial) / scoreable.size
    }

    /**
     * CEFR comparison by ordinal. One level short counts as PARTIAL rather than MISSING: B1
     * against a B2 requirement is a conversation, not a disqualification.
     */
    fun matchLanguages(
        required: List<Pair<String, LanguageLevel>>,
        heldLevel: (language: String) -> LanguageLevel?,
    ): List<LanguageFinding> = required.map { (language, requiredLevel) ->
        val held = heldLevel(language)
        val status = when {
            held == null -> RequirementStatus.MISSING
            held.atLeast(requiredLevel) -> RequirementStatus.MET
            held.ordinal == requiredLevel.ordinal - 1 -> RequirementStatus.PARTIAL
            else -> RequirementStatus.MISSING
        }
        LanguageFinding(language, requiredLevel, held, status)
    }
}
