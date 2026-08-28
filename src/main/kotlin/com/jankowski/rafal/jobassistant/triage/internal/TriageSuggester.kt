package com.jankowski.rafal.jobassistant.triage.internal

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

/**
 * Proposes which catalog entry a queued term might mean.
 *
 * The model's job is matching, not authoring: it picks from a listing it is handed, and everything
 * it returns is re-resolved against the catalog and discarded if it does not exist. That is
 * `CvSelection.from` transplanted - the model may select from a list, never create.
 *
 * The prompt interpolates **no profile data**. Terms come from public job-board listings and the
 * catalog is a public taxonomy, so nothing here is the candidate's to leak.
 */
internal interface TriageSuggester {

    @SystemMessage(fromResource = "/prompts/triage-suggestion-system.md")
    @UserMessage(fromResource = "/prompts/triage-suggestion-user.md")
    fun suggest(
        @V("terms") terms: String,
        @V("catalog") catalog: String,
    ): SuggestedReadings
}

// Every property needs a default: LangChain4j instantiates these reflectively without the
// Jackson Kotlin module, so required constructor parameters fail at runtime.
internal data class SuggestedReadings(
    val suggestions: List<SuggestedReading> = emptyList(),
)

internal data class SuggestedReading(
    /** Echoed back so a suggestion can be matched to the term it is for. */
    val term: String = "",
    /** Exact catalog name. Resolved to an id in Kotlin, and dropped here if it resolves to nothing. */
    val catalogSkill: String = "",
    val rationale: String = "",
)

/**
 * Reads a model-supplied collection as the claim it is rather than the guarantee its type implies.
 *
 * LangChain4j builds a service return type reflectively, without calling the constructor, so
 * Kotlin's intrinsic null checks never run: a model emitting `"suggestions": null` produces a null
 * here despite the non-null type. The default value covers a *missing* key only. See CLAUDE.md,
 * "Writing an AI service".
 */
@Suppress("USELESS_ELVIS")
internal fun SuggestedReadings.suggestionsOrEmpty(): List<SuggestedReading> =
    suggestions ?: emptyList()
