package com.jankowski.rafal.jobassistant.polish.internal

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

/**
 * Rewrites one field of the candidate's prose.
 *
 * The prompt carries **one field's text and a description of what that field is**, and nothing
 * else. Not the employer it sits under, not the project's URL, not the profile it belongs to: a
 * project URL is a direct identifier that
 * [com.jankowski.rafal.jobassistant.privacy.internal.ProfileIdentityInspector] would refuse the
 * call over, and everything else would be context the rewrite has no use for. The field the
 * candidate is editing is the whole input by construction, not by trimming an entity down.
 */
internal interface ProsePolisher {

    @SystemMessage(fromResource = "/prompts/prose-polish-system.md")
    @UserMessage(fromResource = "/prompts/prose-polish-user.md")
    fun polish(
        @V("field") field: String,
        @V("guidance") guidance: String,
        @V("text") text: String,
    ): PolishedProse
}

// Every property needs a default: LangChain4j instantiates these reflectively without the
// Jackson Kotlin module, so required constructor parameters fail at runtime.
internal data class PolishedProse(
    val polished: String = "",
)

/**
 * Reads a model-supplied string as the claim it is rather than the guarantee its type implies.
 *
 * LangChain4j builds a service return type reflectively, without calling the constructor, so
 * Kotlin's intrinsic null checks never run: a model emitting `"polished": null` produces a null
 * here despite the non-null type. The default value covers a *missing* key only. See CLAUDE.md,
 * "Writing an AI service".
 */
@Suppress("USELESS_ELVIS")
internal fun PolishedProse.polishedOrBlank(): String = (polished ?: "").trim()
