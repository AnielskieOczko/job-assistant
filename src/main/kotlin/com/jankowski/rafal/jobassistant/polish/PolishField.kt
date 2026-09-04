package com.jankowski.rafal.jobassistant.polish

/**
 * The profile fields a model may be asked to rewrite.
 *
 * A closed set, and deliberately a short one: **free prose only**. Skill names are catalog-resolved
 * ids rather than text and there is nothing to phrase about them; employers, institutions, dates
 * and URLs are records rather than writing, and a model rewriting one would be changing a fact
 * rather than a sentence. Everything here is text the candidate wrote in their own words, where the
 * only thing a rewrite can improve is how it reads.
 *
 * The enum is the request's whole vocabulary, so an unknown field is a 400 rather than a prompt
 * built from a string the client chose.
 */
enum class PolishField {

    /**
     * `ProfileDetails.careerGoal` - where the candidate is trying to get to. Distinct from the
     * headline and the summary, which describe what they have already done, and the polish must
     * keep it that way: an aspiration rewritten into a capability is a fabricated claim.
     */
    CAREER_GOAL,

    /** `Project.name`, which renders as a heading. A few words, not a sentence. */
    PROJECT_NAME,

    /** `Project.description` - what the side project is, read directly under its name on a CV. */
    PROJECT_DESCRIPTION,

    /**
     * One `ExperienceBullet.text`, from a role or from a project - they are the same kind of
     * sentence and there is nothing for the model to do differently between them.
     */
    EXPERIENCE_BULLET,
}
