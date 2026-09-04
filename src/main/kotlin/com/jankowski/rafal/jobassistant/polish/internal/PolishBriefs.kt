package com.jankowski.rafal.jobassistant.polish.internal

import com.jankowski.rafal.jobassistant.polish.PolishField

/**
 * What each field *is*, in the words the model is given.
 *
 * A rewrite with no idea what it is rewriting turns a project name into a paragraph and a bullet
 * into a cover letter, so the shape of the field is the one piece of context worth sending. It
 * describes the field, never the candidate: nothing here varies by profile, which is what keeps
 * this prompt free of anything to leak.
 *
 * Kept apart from [PolishField] because that enum crosses the wire and is the client's vocabulary.
 * Prompt text is this module's private business, and a wire type is a poor place to keep it.
 */
internal object PolishBriefs {

    fun of(field: PolishField): String = when (field) {
        PolishField.CAREER_GOAL ->
            "Where the candidate is trying to get to next, shown on their profile rather than on a CV. " +
                "One or two sentences. It is an aspiration, not a record of experience: keep it " +
                "forward-looking, and never let it read as a claim about what has already been done."

        PolishField.PROJECT_NAME ->
            "The name of a side project, rendered as a heading on a CV. A few words at most - never a " +
                "sentence, and never the name with a description appended to it."

        PolishField.PROJECT_DESCRIPTION ->
            "One or two sentences saying what a side project is and what it does, read directly under " +
                "its name on a CV by someone who has never heard of it."

        PolishField.EXPERIENCE_BULLET ->
            "A single achievement line under a role or a project on a CV. One sentence, past tense, " +
                "leading with what the candidate did. Keep every number it already states and add none."
    }
}
