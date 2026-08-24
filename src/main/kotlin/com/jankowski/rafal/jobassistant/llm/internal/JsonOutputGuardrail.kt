package com.jankowski.rafal.jobassistant.llm.internal

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.guardrail.OutputGuardrail
import dev.langchain4j.guardrail.OutputGuardrailResult

/**
 * The parse-repair step. Strict JSON-schema support varies across OpenRouter's upstream models and
 * is mostly absent locally, so responses arrive wrapped in markdown fences or trailed by prose
 * often enough to matter.
 *
 * Cheap damage is repaired silently by rewriting the message; anything worse gets one reprompt
 * carrying the reason, which is far more likely to succeed than a blind retry.
 */
internal class JsonOutputGuardrail : OutputGuardrail {

    override fun validate(responseFromLLM: AiMessage): OutputGuardrailResult {
        val raw = responseFromLLM.text()
            ?: return reprompt(
                "The model returned no text.",
                REPROMPT_INSTRUCTION,
            )

        val cleaned = stripFencesAndProse(raw)

        return when {
            cleaned.isEmpty() -> reprompt("The response contained no JSON.", REPROMPT_INSTRUCTION)
            cleaned == raw.trim() -> success()
            // Recoverable without another round trip: it was valid JSON in a fence.
            else -> successWith(cleaned)
        }
    }

    /**
     * Extracts the outermost JSON object or array, dropping markdown fences and any commentary
     * around it. Brace counting is string-aware so a `}` inside a value cannot end the scan early.
     */
    private fun stripFencesAndProse(raw: String): String {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val start = text.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return ""

        val open = text[start]
        val close = if (open == '{') '}' else ']'

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return ""
    }

    private companion object {
        const val REPROMPT_INSTRUCTION =
            "Respond with a single JSON value and nothing else. No markdown fences, no commentary, " +
                "no explanation before or after the JSON."
    }
}
