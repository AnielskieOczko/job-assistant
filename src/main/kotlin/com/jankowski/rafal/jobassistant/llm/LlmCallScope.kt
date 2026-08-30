package com.jankowski.rafal.jobassistant.llm

/**
 * Records who and what the calls made inside [forProfile] are about, so their audit rows can be
 * erased with that profile and priced against the thing that caused them.
 *
 * This is the one place the module acknowledges profiles at all, and it does so by id only - no
 * type is imported and no module dependency is created. The subject follows the same rule: a
 * caller passes a bare string kind and a bare id, so `llm` never learns that an offer exists. The
 * alternative was an audit log whose rows could never be attributed to anyone, which is exactly
 * the property that made it survive profile deletion.
 *
 * A thread-local is sound here because the scope always opens on the thread that goes on to make
 * the calls: the analysis pipeline's `@Async` hop happens *before* the scope is entered, not
 * inside it. Anything that leaves the scope simply records no owner, which is why the columns are
 * nullable rather than constraints.
 */
object LlmCallScope {

    /**
     * The subject label for work driven by one job offer.
     *
     * A constant rather than a literal at each call site because the analysis pipeline, document
     * generation and the spend read-side all have to agree on the spelling, and a typo would show
     * up only as a cost that quietly stopped grouping.
     */
    const val SUBJECT_OFFER: String = "OFFER"

    private val current = ThreadLocal<Scope?>()

    /**
     * @param subjectKind what caused these calls, as an opaque label (`"OFFER"`). Paired with
     *   [subjectId] it answers "what did this offer cost me", repairs included, which per-call
     *   costs alone cannot.
     */
    fun <T> forProfile(
        profileId: Long,
        subjectKind: String? = null,
        subjectId: Long? = null,
        block: () -> T,
    ): T {
        val previous = current.get()
        current.set(Scope(profileId, subjectKind, subjectId))
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    /** The profile in scope, or null when a call is made outside one. */
    fun currentProfileId(): Long? = current.get()?.profileId

    /** What the calls in scope are about, or null when nothing said. */
    fun currentSubjectKind(): String? = current.get()?.subjectKind

    /** The id of the subject named by [currentSubjectKind]. */
    fun currentSubjectId(): Long? = current.get()?.subjectId

    private data class Scope(
        val profileId: Long,
        val subjectKind: String?,
        val subjectId: Long?,
    )
}
