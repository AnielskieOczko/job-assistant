package com.jankowski.rafal.jobassistant.triage

/**
 * The triage module's entire public surface.
 *
 * It exists because ranking the review queue needs two modules that must not know about each other:
 * `unmatched_term` belongs to `catalog`, which has no dependencies and is depended on by everything,
 * while in-scope demand is computed from the corpus in `market`. A `catalog -> market` edge would
 * put an HTTP client and a scheduler into every module's transitive closure, so the join lives here
 * instead, in a module both can be depended on by.
 *
 * Triage **reads**. Approving or rejecting a term still goes through `catalog`, because
 * `unmatched_term` exists precisely so that nothing but a human decision can grow the catalog, and
 * a second write path would be a second place for that rule to be forgotten.
 *
 * See `docs/adr/0003-model-assisted-triage-outside-the-catalog.md`.
 */
interface TriageService {

    /**
     * The review queue, filtered and ranked.
     *
     * [minOccurrences] applies to the **sum** of the candidate's own count and the market's, never
     * to either alone - see the implementation for why filtering on `occurrences` would hide the
     * entire corpus.
     */
    fun queue(
        minOccurrences: Int = DEFAULT_MIN_OCCURRENCES,
        ranking: TriageRanking = TriageRanking.SCOPE,
        limit: Int = DEFAULT_LIMIT,
    ): TriageQueue

    companion object {
        /**
         * Enough to cut the singleton tail without hiding a real signal.
         *
         * Measured: 56% of distinct corpus names appear exactly once, and those are what make the
         * queue unopenable. A term two employers asked for is still worth a decision, so the
         * threshold sits just above the noise rather than wherever the list becomes short.
         */
        const val DEFAULT_MIN_OCCURRENCES = 3
        const val DEFAULT_LIMIT = 100
    }
}
