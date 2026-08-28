package com.jankowski.rafal.jobassistant.analysis.internal

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

/**
 * Turns pasted offer text into structured requirements.
 *
 * The model's only job here is reading comprehension: it identifies what the offer asks for and
 * names the closest catalog entry. It does not compare anything against the profile - that
 * happens afterwards, in Kotlin, where it can be tested.
 */
internal interface OfferExtractor {

    @SystemMessage(fromResource = "/prompts/offer-extraction-system.md")
    @UserMessage(fromResource = "/prompts/offer-extraction-user.md")
    fun extract(
        @V("offerText") offerText: String,
        @V("catalog") catalog: String,
    ): ExtractedOffer
}

// Every property needs a default: LangChain4j instantiates these reflectively without the
// Jackson Kotlin module, so required constructor parameters fail at runtime.
internal data class ExtractedOffer(
    val title: String = "",
    val company: String = "",
    val seniority: String = "",
    val detectedLanguage: String = "",
    val requirements: List<ExtractedRequirement> = emptyList(),
    val languageRequirements: List<ExtractedLanguageRequirement> = emptyList(),
    val redFlags: List<String> = emptyList(),
)

internal data class ExtractedRequirement(
    val rawText: String = "",
    /** Exact catalog name, or empty when nothing fit. Resolved to an id in Kotlin, not here. */
    val catalogSkill: String = "",
    val importance: String = "NICE_TO_HAVE",
    val rationale: String = "",
)

internal data class ExtractedLanguageRequirement(
    val language: String = "",
    val level: String = "",
)

/**
 * Reads a model-supplied collection as the claim it is rather than the guarantee its type implies.
 *
 * LangChain4j builds a service return type reflectively, without the Jackson Kotlin module and
 * without calling the constructor, so the intrinsic null checks never run: a model emitting
 * `"requirements": null` produces a null here despite the non-null type. The default value covers
 * a *missing* key only. See CLAUDE.md, "Writing an AI service".
 */
@Suppress("USELESS_ELVIS")
internal fun ExtractedOffer.requirementsOrEmpty(): List<ExtractedRequirement> =
    requirements ?: emptyList()

@Suppress("USELESS_ELVIS")
internal fun ExtractedOffer.languageRequirementsOrEmpty(): List<ExtractedLanguageRequirement> =
    languageRequirements ?: emptyList()
