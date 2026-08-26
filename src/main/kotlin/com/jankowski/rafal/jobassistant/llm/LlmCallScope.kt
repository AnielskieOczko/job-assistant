package com.jankowski.rafal.jobassistant.llm

/**
 * Records which profile the calls made inside [forProfile] are about, so their audit rows can be
 * erased with that profile.
 *
 * This is the one place the module acknowledges profiles at all, and it does so by id only - no
 * type is imported and no module dependency is created. The alternative was an audit log whose
 * rows could never be attributed to anyone, which is exactly the property that made it survive
 * profile deletion.
 *
 * A thread-local is sound here because the scope always opens on the thread that goes on to make
 * the calls: the analysis pipeline's `@Async` hop happens *before* the scope is entered, not
 * inside it. Anything that leaves the scope simply records no owner, which is why the column is
 * nullable rather than a constraint.
 */
object LlmCallScope {

    private val current = ThreadLocal<Long?>()

    fun <T> forProfile(profileId: Long, block: () -> T): T {
        val previous = current.get()
        current.set(profileId)
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    /** The profile in scope, or null when a call is made outside one. */
    fun currentProfileId(): Long? = current.get()
}
