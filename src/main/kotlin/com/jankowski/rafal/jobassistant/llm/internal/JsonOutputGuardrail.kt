package com.jankowski.rafal.jobassistant.llm.internal

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.guardrail.OutputGuardrail
import dev.langchain4j.guardrail.OutputGuardrailResult

/**
 * The parse-repair step. Strict JSON-schema support varies across OpenRouter's upstream models and
 * is mostly absent locally, so responses arrive wrapped in markdown fences or trailed by prose
 * often enough to matter.
 *
 * Cheap damage is repaired silently by rewriting the message. **Anything worse fails the call**,
 * and used to reprompt.
 *
 * ### Why the reprompt was removed
 *
 * A guardrail reprompt cannot see the question. `OutputGuardrailExecutor` builds the retry from
 * `requestParams().chatMemory()`, and where there is no memory - which is every AI service in this
 * application, deliberately - it falls back to `new ArrayList<>()` and sends **the reprompt
 * instruction as the only message**: no system prompt, no original request. The model is asked to
 * "respond with a single JSON value" about nothing.
 *
 * The audit trail caught it. `llm_call` row 8 was a polish request whose answer came back empty;
 * row 9, the repair, carried one user message - the instruction - and the model answered
 * `{"polished": "I understand you'd like me to respond with a single JSON value. However, I need a
 * question or request to respond to."}`. Well-formed JSON, schema-valid, and shown to the user as a
 * suggested rewrite of their project description.
 *
 * That is the failure this codebase keeps naming from other directions: **a repair that cannot
 * repair, whose output is indistinguishable from an answer.** A hard failure carrying the reason is
 * worth more than a fluent non-answer, so the JSON-free case now fails and the caller decides what
 * to tell the user.
 *
 * The repair that *can* work lives in [InspectingChatModel], which still holds the original
 * request and re-asks with it.
 */
internal class JsonOutputGuardrail : OutputGuardrail {

    override fun validate(responseFromLLM: AiMessage): OutputGuardrailResult {
        val raw = responseFromLLM.text() ?: return failure(NO_TEXT)

        val cleaned = stripFencesAndProse(raw)

        return when {
            cleaned.isEmpty() -> failure(NO_JSON)
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
        /**
         * Both messages reach the caller: an analysis stores it on the row and a polish request
         * renders it. They therefore say what the model did, not what the code did - "no JSON" is
         * something a human can act on, and "guardrail failed" is not.
         */
        const val NO_TEXT = "The model returned no text, twice. Nothing was parsed."

        const val NO_JSON =
            "The model's answer contained no JSON at all. Nothing was parsed, and the answer is " +
                "not shown: prose where an object was required means the model did not do the task."
    }
}
