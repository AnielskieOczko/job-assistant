package com.jankowski.rafal.jobassistant.triage

import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillSuggestion
import java.time.Instant

/**
 * One term awaiting review, with every signal a decision needs.
 *
 * Three counters rather than one because they answer different questions and must not be added
 * together: [occurrences] is what this candidate read, [inScopeDemand] is what employers in this
 * job hunt ask for, and [marketOccurrences] is the whole ingested division including the QA, BA and
 * PM roles the candidate will never apply to.
 */
data class TriageEntry(
    val termId: Long,
    val term: String,
    /** Times the term appeared in an offer the candidate actually analysed. */
    val occurrences: Int,
    /** Times it appears anywhere in the ingested corpus. */
    val marketOccurrences: Int,
    /** Times it appears on a corpus offer that also asks for a scope skill. */
    val inScopeDemand: Int,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    /**
     * Catalog entries this term might mean by string similarity, best first.
     *
     * Computed on read rather than stored, because it is a pure function of the term and the
     * catalog: storing it would mean a suggestion that survives the skill it points at being
     * renamed. Deliberately not a decision - the reviewer still picks and still clicks approve.
     */
    val suggestions: List<SkillSuggestion> = emptyList(),
    /**
     * Catalog entries a *model* proposed, with its reasoning.
     *
     * Kept separate from [suggestions] rather than merged, because provenance is the point: one is
     * arithmetic over spellings and the other is a model's reading, and a reviewer weighs them
     * differently. Stored rather than recomputed - producing these costs a model call, so a page
     * load must never trigger one.
     */
    val modelSuggestions: List<ModelSuggestion> = emptyList(),
)

/**
 * A model's proposed reading of a term.
 *
 * Carries a [rationale] instead of a score: a model has no calibrated confidence to report, and
 * printing one would invite a reviewer to trust a number that means nothing. A sentence they can
 * check is the honest equivalent.
 */
data class ModelSuggestion(
    val skillId: Long,
    val skillName: String,
    val category: SkillCategory,
    val rationale: String?,
    /** Which model profile produced it, so a change in quality can be attributed. */
    val modelProfile: String?,
)

/**
 * A page of the review queue, with the numbers needed to say what is *not* on it.
 *
 * [matching] and [pending] are both returned on purpose. With 1,500 queued terms and a limit of
 * 100, a response carrying only its entries lets a short list read as a finished queue - the same
 * failure shape as an empty denominator reading as success. A caller can render "showing 100 of 412
 * matching, 1,540 queued" only if it is told all three.
 */
data class TriageQueue(
    val entries: List<TriageEntry>,
    /** Pending terms passing the frequency filter. The denominator for "showing X of Y". */
    val matching: Int,
    /** Every pending term, filter or no filter. What the queue would hold with no threshold. */
    val pending: Int,
    /** The threshold actually applied, echoed so a caller never has to assume the default. */
    val minOccurrences: Int,
    val ranking: TriageRanking,
    /**
     * The scope the in-scope numbers were measured against, as names the catalog resolved.
     *
     * Echoed for the same reason a rate has to carry its denominator: "in-scope demand" is
     * meaningless without saying what the scope was, and a misconfigured name silently dropping out
     * is exactly the case a reader needs to be able to see.
     */
    val scopeSkills: List<String>,
)

/** Which signal breaks the tie once the candidate's own count has had its say. */
enum class TriageRanking {
    /** Demand among offers that also ask for a scope skill. The default: this job hunt's market. */
    SCOPE,

    /** Demand across the whole ingested corpus, scope ignored. */
    CORPUS,
}

/**
 * What one suggestion run did.
 *
 * Counts rather than a rate, and every count named. A run that sent fifty terms and stored three is
 * a different event from one that sent three and stored three, and a single "3 suggestions" would
 * hide which happened. [droppedUnresolvable] climbing is the first sign the model has started
 * naming skills the catalog does not have.
 */
data class SuggestionRun(
    /** Terms on the page that was examined. */
    val termsConsidered: Int,
    /** Of those, how many were actually sent - terms with a stored suggestion are skipped. */
    val termsSent: Int,
    val suggestionsStored: Int = 0,
    /** Returned rows naming a skill the catalog could not resolve. Discarded, never queued. */
    val droppedUnresolvable: Int = 0,
    /** Returned rows for a term that was not asked about. */
    val droppedUnrequested: Int = 0,
)
