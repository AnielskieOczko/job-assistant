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

    /** Expands held skills through IMPLIES/RELATED edges so the diff can be a pure lookup. */
    fun coverageFor(heldSkillIds: Set<Long>): SkillCoverage

    /**
     * Records a term the extractor could not place, incrementing its occurrence count if seen
     * before. Safe to call repeatedly; this is the review queue's only write path.
     */
    fun recordUnmatched(term: String)

    fun pendingUnmatchedTerms(limit: Int = 100): List<UnmatchedTerm>

    /** Approving a term adds it as an alias of [skillId], so it resolves from then on. */
    fun approveUnmatchedTerm(termId: Long, skillId: Long): CanonicalSkill

    fun rejectUnmatchedTerm(termId: Long)

    /**
     * Adds a skill the seed catalog does not cover. Human-invoked only - the extractor can queue
     * an unmatched term but can never create a canonical entry on its own.
     *
     * Returns the existing skill if [name] is already present, so imports stay idempotent.
     */
    fun createSkill(name: String, category: SkillCategory, aliases: Collection<String> = emptyList()): CanonicalSkill
}
