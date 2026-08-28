package com.jankowski.rafal.jobassistant.triage.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import com.jankowski.rafal.jobassistant.market.MarketDemand
import com.jankowski.rafal.jobassistant.triage.TriageEntry
import com.jankowski.rafal.jobassistant.triage.TriageQueue
import com.jankowski.rafal.jobassistant.triage.TriageRanking
import com.jankowski.rafal.jobassistant.triage.TriageService
import org.springframework.stereotype.Service

/**
 * Joins the review queue to market demand in memory.
 *
 * In memory rather than in SQL because the two sides live in different modules and therefore in
 * different owners' tables; a join across them in one statement would be exactly the coupling this
 * module exists to avoid. The sizes make it a non-question - the queue is human-scale by
 * construction and the corpus side is a single grouped scan.
 */
@Service
internal class TriageQueueService(
    private val catalog: SkillCatalog,
    private val demand: MarketDemand,
) : TriageService {

    override fun queue(minOccurrences: Int, ranking: TriageRanking, limit: Int): TriageQueue {
        require(minOccurrences >= 0) { "minOccurrences must not be negative" }
        require(limit > 0) { "limit must be positive" }

        val inScope = demand.inScopeUnresolvedDemand()
        val entries = catalog.allPendingUnmatchedTerms().map { term ->
            TriageEntry(
                termId = term.id,
                term = term.term,
                occurrences = term.occurrences,
                marketOccurrences = term.marketOccurrences,
                // Recomputing the key rather than carrying it: unmatched_term is unique on it and
                // MarketDemand is keyed the same way, so this is a join on a shared identity rather
                // than a guess at one. V15's drift test is what keeps the two spellings of that
                // identity from parting company.
                inScopeDemand = inScope[SkillNormalizer.normalize(term.term)] ?: 0,
                firstSeenAt = term.firstSeenAt,
                lastSeenAt = term.lastSeenAt,
            )
        }

        // Filtered on the *sum* of both counters, never on `occurrences` alone. Every term the
        // corpus contributed has `occurrences = 0`, so an `occurrences >= 3` threshold would hide
        // the entire market -- including Komunikacja at 61 -- behind a filter whose only job is to
        // cut the singleton tail.
        val matching = entries.filter { it.occurrences + it.marketOccurrences >= minOccurrences }

        return TriageQueue(
            entries = matching.sortedWith(comparatorFor(ranking)).take(limit),
            matching = matching.size,
            pending = entries.size,
            minOccurrences = minOccurrences,
            ranking = ranking,
            scopeSkills = demand.scopeSkills(),
        )
    }

    /**
     * What the candidate read outranks what the market says, under either ranking.
     *
     * The queue serves one job hunt rather than describing a market: a term met in an offer that
     * was actually analysed is a decision already waiting, and thousands of corpus mentions must
     * not push it down the page. Scope and corpus demand only break the tie beneath that.
     *
     * The final tie-break on the term itself is not decoration. Without a total order, two entries
     * with equal counts could swap places between requests, and a page boundary would show one of
     * them twice and the other never.
     */
    private fun comparatorFor(ranking: TriageRanking): Comparator<TriageEntry> {
        val ownFirst = compareByDescending<TriageEntry> { it.occurrences }
        val byDemand = when (ranking) {
            TriageRanking.SCOPE -> ownFirst
                .thenByDescending { it.inScopeDemand }
                .thenByDescending { it.marketOccurrences }

            TriageRanking.CORPUS -> ownFirst
                .thenByDescending { it.marketOccurrences }
                .thenByDescending { it.inScopeDemand }
        }
        return byDemand.thenBy { it.term }
    }
}
