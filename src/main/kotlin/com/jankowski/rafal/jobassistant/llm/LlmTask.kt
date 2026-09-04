package com.jankowski.rafal.jobassistant.llm

/**
 * The distinct jobs the application asks a model to do. Each maps to a named model profile in
 * configuration, so extraction can run on a different provider from narrative writing without
 * any code change.
 */
enum class LlmTask {
    /** Pulling structured requirements out of pasted offer text. Needs strict JSON schema. */
    EXTRACTION,

    /** Turning an already-computed diff into readable prose and a learning plan. */
    NARRATIVE,

    /** Selecting and rephrasing profile records for a CV or cover letter. */
    DOCUMENT,

    /**
     * Proposing which catalog entry a queued term might mean.
     *
     * Never authoritative: the model selects from a list it is given, everything it returns is
     * re-resolved against the catalog and dropped if it does not exist, and a human still clicks
     * approve. It carries no profile data at all - only public job-board vocabulary.
     */
    TRIAGE,

    /**
     * Rewriting one free-prose field of the profile so the candidate's own facts read better.
     *
     * [TRIAGE]'s discipline applied to the profile: never authoritative. The model is handed one
     * field's text and returns a suggestion, the suggestion is scanned for skills the profile does
     * not hold, and nothing is stored until the candidate accepts it through the ordinary CRUD
     * path. "No model writes to the profile" stays literally true.
     *
     * Its own task so it can point at a cheap model - rewriting a sentence is not extraction - and
     * so a habit of polishing every field shows up as its own line in `llm_spend_daily` rather
     * than inflating [DOCUMENT].
     */
    POLISH,
}
