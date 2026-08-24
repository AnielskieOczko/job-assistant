package com.jankowski.rafal.jobassistant.analysis.internal

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

/**
 * Writes prose over an already-computed diff. It receives statuses as facts and is forbidden from
 * contradicting them, so the narrative cannot disagree with the numbers above it.
 */
internal interface ReportNarrator {

    @SystemMessage(fromResource = "/prompts/report-narrative-system.md")
    @UserMessage(fromResource = "/prompts/report-narrative-user.md")
    fun narrate(
        @V("roleTitle") roleTitle: String,
        @V("company") company: String,
        @V("matchScore") matchScore: String,
        @V("scoreExplanation") scoreExplanation: String,
        @V("language") language: String,
        @V("mustHaves") mustHaves: String,
        @V("niceToHaves") niceToHaves: String,
        @V("languageRequirements") languageRequirements: String,
        @V("unresolved") unresolved: String,
    ): ReportNarrative
}

internal data class ReportNarrative(
    val summaryMarkdown: String = "",
    val learningPlan: List<NarratedPlanItem> = emptyList(),
)

internal data class NarratedPlanItem(
    val skill: String = "",
    val why: String = "",
    val practiceProject: String = "",
    val effortEstimate: String = "",
)
