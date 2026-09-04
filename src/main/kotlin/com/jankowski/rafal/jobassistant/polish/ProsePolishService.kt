package com.jankowski.rafal.jobassistant.polish

/**
 * The polish module's entire public surface: rewrite one field, return a suggestion, store nothing.
 *
 * This is [com.jankowski.rafal.jobassistant.llm.LlmTask.TRIAGE]'s discipline applied to the
 * candidate's own prose. The model never sees more than one field's text, never learns which
 * profile it belongs to, and cannot write anywhere - the accept is a separate, human-driven call to
 * the profile CRUD endpoints, which are the only way anything reaches the profile at all.
 */
interface ProsePolishService {

    /**
     * @param profileId whose held skills the suggestion is scanned against. The profile's *content*
     *   is not sent to the model; only [text] is, and only the skills the profile holds are read
     *   back out of it.
     * @param text the field as the candidate currently has it. Must not be blank - this polishes
     *   writing that exists and never generates a field from nothing.
     * @throws IllegalArgumentException if [text] is blank or longer than [MAX_TEXT_LENGTH].
     * @throws UnusablePolishException if the model answered with nothing usable.
     */
    fun polish(profileId: Long, field: PolishField, text: String): PolishSuggestion

    companion object {
        /**
         * A bound on one request's prose, in characters.
         *
         * Every field this touches is a heading, a sentence or a short paragraph, so anything past
         * this is not a field being polished. It is here because each call costs tokens: without a
         * ceiling, one paste of a whole CV into a description box would be a request priced like an
         * analysis.
         */
        const val MAX_TEXT_LENGTH = 2000
    }
}

/**
 * Thrown when the model answered with nothing this can show.
 *
 * An empty suggestion is a failure, not a result. Rendered as an empty pane next to the original it
 * would read as "your text is best left alone", which is a judgement no empty response supports -
 * the same shape as an empty extraction reading as "this offer asks for nothing".
 *
 * It also covers the *fluent* non-answer. When a model replies with prose instead of an object,
 * `JsonOutputGuardrail` fails the call rather than accepting it, and that arrives here: a sentence
 * addressed to whoever asked ("I need a question to respond to") deserialises into a text field
 * perfectly and would render as a suggested rewrite of the candidate's own description. Refusing to
 * show it is the whole point - this surface has no other way to tell a rewrite from a reply.
 */
class UnusablePolishException(field: PolishField, reason: String? = null) : RuntimeException(
    "The model returned nothing usable when asked to polish $field. Nothing has been changed" +
        (reason?.let { "; $it" } ?: ". Check the response on the most recent llm_call row.")
)
