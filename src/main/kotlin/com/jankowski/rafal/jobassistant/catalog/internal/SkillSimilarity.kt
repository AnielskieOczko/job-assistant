package com.jankowski.rafal.jobassistant.catalog.internal

/**
 * Nearest catalog entries for a term, by character-trigram similarity.
 *
 * **Scored over normalised keys, not raw text.** `pg_trgm` was the obvious alternative and is
 * rejected for exactly that reason: it tokenises raw strings with its own padding and word-splitting
 * rules, which would give this application two different notions of "nearly the same string" - one
 * deciding what to suggest and another deciding what resolves. Scoring the same keys
 * `SkillNormalizer` produces means a suggestion, once approved, resolves for the reason the score
 * predicted rather than by coincidence.
 *
 * Pure and stateless. No index is cached: the catalog is 210 names and 362 aliases, and a cache
 * would be a second copy of the catalog that goes stale the moment a skill is renamed - a real bug
 * traded for an imperceptible saving.
 */
internal object SkillSimilarity {

    /**
     * Below this a key matches everything.
     *
     * "ai", "go" and "c" share a trigram with half the catalog, and a suggestion list that is
     * always wrong is worse than none: it trains the reviewer to ignore the chips. The cost is that
     * genuinely short terms get no help, which is the right side to err on.
     */
    const val MIN_QUERY_LENGTH = 4

    /** Tuned so a shared word earns a place and a shared prefix does not. */
    const val THRESHOLD = 0.55

    const val DEFAULT_LIMIT = 3

    private const val TRIGRAM = 3

    /** One catalog spelling to score against: a canonical name, or any alias of one. */
    data class Candidate(val skillId: Long, val spelling: String, val key: String)

    /** The best-scoring spelling of one skill, and what it scored. */
    data class Match(val skillId: Long, val spelling: String, val score: Double)

    /**
     * The [limit] closest skills to [queryKey], best first.
     *
     * Returns at most one [Match] per skill. A skill carrying six aliases would otherwise fill the
     * whole list with itself and hide every alternative, which is the opposite of what a reviewer
     * needs from a shortlist.
     */
    fun rank(
        queryKey: String,
        candidates: Collection<Candidate>,
        limit: Int = DEFAULT_LIMIT,
    ): List<Match> {
        if (queryKey.length < MIN_QUERY_LENGTH || limit <= 0) return emptyList()

        val queryGrams = trigrams(queryKey)
        val bestPerSkill = HashMap<Long, Match>()

        candidates.forEach { candidate ->
            val score = score(queryKey, queryGrams, candidate.key)
            if (score < THRESHOLD) return@forEach

            val match = Match(candidate.skillId, candidate.spelling, score)
            val incumbent = bestPerSkill[candidate.skillId]
            // The spelling breaks a tie so the surviving alias does not depend on row order.
            if (incumbent == null ||
                score > incumbent.score ||
                (score == incumbent.score && candidate.spelling < incumbent.spelling)
            ) {
                bestPerSkill[candidate.skillId] = match
            }
        }

        // Score, then spelling: equal scores must order the same way on every call, or the chips
        // shuffle between page loads for no reason a reviewer can see.
        return bestPerSkill.values
            .sortedWith(compareByDescending<Match> { it.score }.thenBy { it.spelling })
            .take(limit)
    }

    private fun score(queryKey: String, queryGrams: Set<String>, candidateKey: String): Double =
        maxOf(dice(queryGrams, trigrams(candidateKey)), containment(queryKey, candidateKey))

    /**
     * Sørensen-Dice over trigram *sets*.
     *
     * Sets rather than multisets because a repeated trigram inside one long name says nothing about
     * whether it means the same thing as a short one.
     */
    private fun dice(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val shared = a.count { it in b }
        return 2.0 * shared / (a.size + b.size)
    }

    /**
     * The signal Dice misses: one name literally containing the other.
     *
     * "spring" inside "springbootstarter" shares all of its trigrams but is swamped by the longer
     * string's, so the coefficient sinks well below threshold even though the relationship is
     * obvious to a person.
     *
     * Scored at exactly [THRESHOLD] - enough to earn a place on the list, never enough to outrank a
     * genuine similarity. "test" is inside "testautomation" without being what the reviewer meant,
     * so containment gets a hearing rather than a verdict.
     */
    private fun containment(a: String, b: String): Double {
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return if (shorter.length >= MIN_QUERY_LENGTH && longer.contains(shorter)) THRESHOLD else 0.0
    }

    private fun trigrams(key: String): Set<String> =
        if (key.length < TRIGRAM) emptySet()
        else (0..key.length - TRIGRAM).mapTo(HashSet()) { key.substring(it, it + TRIGRAM) }
}
