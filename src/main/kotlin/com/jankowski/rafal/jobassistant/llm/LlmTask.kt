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
}
