package com.jankowski.rafal.jobassistant.catalog

/**
 * The catalog module's entire public surface.
 *
 * Callers hand in free text and get back canonical identity, or nothing. There is deliberately no
 * way to ask "what does the model think this is" - resolution is a lookup, not a judgement.
 */
interface SkillCatalog {

    fun findById(id: Long): CanonicalSkill?

    fun findAllById(ids: Collection<Long>): List<CanonicalSkill>

    /** Every skill, name-ordered. Used to build the extraction prompt's catalog listing. */
    fun findAll(): List<CanonicalSkill>

    /** Resolves one term via its normalised alias. Returns null rather than guessing. */
    fun resolve(term: String): CanonicalSkill?

    /** Batch form of [resolve]; keys are the original terms, values null where unresolved. */
    fun resolveAll(terms: Collection<String>): Map<String, CanonicalSkill?>

    /**
     * Catalog entries [term] might mean, best first.
     *
     * **Candidates for a human, never consulted by [resolve].** This is the one method on this
     * interface that answers "what might this be" rather than "what is this", and the separation is
     * deliberate: the docstring above says resolution is a lookup rather than a judgement, and that
     * stays true because nothing downstream of a suggestion happens without someone clicking
     * approve. Deterministic string similarity, not a model - `triage` owns the model half, so the
     * module everything depends on keeps depending on nothing.
     *
     * Empty for terms shorter than four characters, where every candidate matches equally.
     */
    fun suggest(term: String, limit: Int = 3): List<SkillSuggestion>

    /**
     * Batch form of [suggest]; keys are the original terms.
     *
     * Preferred wherever more than one term is being suggested for. The candidate index is the
     * whole catalog, so calling [suggest] in a loop rebuilds it once per term and reloads it from
     * the database each time.
     */
    fun suggestAll(terms: Collection<String>, limit: Int = 3): Map<String, List<SkillSuggestion>>

    /** Expands held skills through IMPLIES/RELATED edges so the diff can be a pure lookup. */
    fun coverageFor(heldSkillIds: Set<Long>): SkillCoverage

    /**
     * Records a term the extractor could not place, incrementing its occurrence count if seen
     * before. Safe to call repeatedly; this is the review queue's only write path.
     */
    fun recordUnmatched(term: String)

    /**
     * Records how often the ingested market corpus asks for each term the catalog cannot place,
     * under a counter kept *separate* from [recordUnmatched].
     *
     * [mentions] is the count over the **whole corpus**, not one poll's, and the stored value is
     * **set** to it rather than incremented. Polls re-serve the same listings, so accumulating
     * would multiply a term's demand by the number of times we happened to look, and the queue
     * would rank by how long a term had been listed as much as by how many employers asked for it.
     * Recomputing instead makes a re-poll a no-op, and makes this write the same computation the
     * V16 backfill performs.
     *
     * Spellings collapse onto the normalised key and their counts are summed, so "Power Apps" and
     * "power apps" are one queue entry asked for twice rather than two entries asked for once.
     */
    fun recordUnmatchedFromMarket(mentions: Map<String, Int>)

    fun pendingUnmatchedTerms(limit: Int = 100): List<UnmatchedTerm>

    /**
     * Every pending term, unlimited.
     *
     * For callers that rank or count across the whole queue rather than showing its head. A limit
     * would make the count a floor rather than a total, and a queue that reports "showing 100 of
     * 100" while holding 1,500 rows reads as finished when it is not -- the same failure shape as
     * an empty denominator reading as success. The queue is human-scale by construction: it exists
     * to be emptied by a person, so if it were ever too large to hold in memory the problem would
     * be the queue, not the query.
     */
    fun allPendingUnmatchedTerms(): List<UnmatchedTerm>

    /**
     * Approving a term adds it as an alias of [skillId], so it resolves from then on.
     *
     * Refused when the term's normalised key is already an alias of a *different* skill: aliases
     * are unique on that key, so the approval could not change what the term resolves to, and
     * recording it anyway would drop the term out of the queue against a skill it never reaches.
     * Approving a term that already aliases [skillId] is a no-op, so retries stay safe.
     */
    fun approveUnmatchedTerm(termId: Long, skillId: Long): CanonicalSkill

    fun rejectUnmatchedTerm(termId: Long)

    /**
     * Adds a skill the seed catalog does not cover. Human-invoked only - the extractor can queue
     * an unmatched term but can never create a canonical entry on its own.
     *
     * Returns the existing skill if [name] is already present, so imports stay idempotent.
     */
    fun createSkill(name: String, category: SkillCategory, aliases: Collection<String> = emptyList()): CanonicalSkill

    /**
     * Renames a skill and/or changes its category. The new name is registered as an alias too, so
     * nothing that already resolved via the old name stops working.
     *
     * Throws if [id] is unknown, or if another skill already has [name].
     */
    fun updateSkill(id: Long, name: String, category: SkillCategory): CanonicalSkill

    /**
     * Removes a skill from the catalog. Refused while any profile still holds it or any bullet is
     * tagged with it - deleting it out from under a candidate's data would silently corrupt every
     * gap report and CV that cites it.
     */
    fun deleteSkill(id: Long)
}
