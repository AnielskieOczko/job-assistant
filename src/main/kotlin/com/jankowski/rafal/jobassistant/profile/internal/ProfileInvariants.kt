package com.jankowski.rafal.jobassistant.profile.internal

/**
 * The rules that hold no matter which write path reached the profile.
 *
 * Import expresses them over skill *names*, because that is what a hand-authored document contains
 * and what its rejection message has to name. Per-entity editing expresses them over ids, because
 * the UI picked from the catalog and already knows them. The rule itself is the same either way, so
 * it lives here once rather than being restated in both.
 */
internal object ProfileInvariants {

    /**
     * Skill ids tagged on a bullet that the profile does not declare.
     *
     * A bullet is the evidence behind a claim, so a tag with no matching entry in `skills[]` would
     * let a skill reach a CV with nothing behind it - the one thing the profile exists to prevent.
     */
    fun undeclaredTags(declaredSkillIds: Set<Long>, taggedSkillIds: Set<Long>): Set<Long> =
        taggedSkillIds - declaredSkillIds
}

/** A bullet standing in the way of deleting a skill, named so the caller can go and fix it. */
internal data class BlockingBullet(val id: Long, val text: String)

/**
 * A write that the profile's current contents refuse. Distinct from a malformed request: the body
 * is fine, it just cannot be reconciled with what is already stored.
 */
internal class ProfileConflictException(
    message: String,
    val blockingBullets: List<BlockingBullet> = emptyList(),
) : RuntimeException(message)

/** A request naming an id the profile or the catalog does not have. */
internal class UnknownProfileEntityException(message: String) : RuntimeException(message)
