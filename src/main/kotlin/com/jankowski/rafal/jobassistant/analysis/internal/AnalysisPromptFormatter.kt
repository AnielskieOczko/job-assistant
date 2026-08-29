package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill

/**
 * Renders the computed diff into the plain text the narrator sees.
 *
 * Kept separate and pure so the exact wording handed to the model is assertable in a test - the
 * narrator can only stay consistent with the statuses if it is actually shown them.
 */
internal object AnalysisPromptFormatter {

    fun catalogListing(skills: List<CanonicalSkill>): String =
        skills.joinToString("\n") { "- ${it.name} [${it.category}]" }

    fun requirements(matched: List<MatchedRequirement>, importance: Importance): String {
        val relevant = matched.filter { it.importance == importance && it.status != RequirementStatus.UNRESOLVED }
        if (relevant.isEmpty()) return "(none)"

        return relevant.joinToString("\n") { requirement ->
            buildString {
                append("- ").append(requirement.skillName ?: requirement.rawText)
                append(" | status: ").append(requirement.status)
                append(" | offer wording: \"").append(requirement.rawText.trim()).append('"')
                requirement.evidence?.let { append(" | evidence: ").append(it) }
            }
        }
    }

    fun unresolved(matched: List<MatchedRequirement>): String {
        val unresolved = matched.filter { it.status == RequirementStatus.UNRESOLVED }
        if (unresolved.isEmpty()) return "(none)"
        return unresolved.joinToString("\n") { "- \"${it.rawText.trim()}\"" }
    }

    fun languages(findings: List<LanguageFinding>): String {
        if (findings.isEmpty()) return "(none)"
        return findings.joinToString("\n") { finding ->
            val held = finding.heldLevel?.name ?: "not spoken"
            "- ${finding.language}: offer wants ${finding.requiredLevel}, candidate has $held | status: ${finding.status}"
        }
    }

    fun score(value: Double?): String =
        value?.let { "%.0f%%".format(it * 100) } ?: "not scoreable"

    /** An aspiration, not evidence - the narrator must not treat it as a claim of experience. */
    fun careerGoal(value: String?): String = value?.trim()?.ifBlank { null } ?: "(not stated)"
}
