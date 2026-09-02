package com.jankowski.rafal.jobassistant.profile

import com.jankowski.rafal.jobassistant.catalog.SkillCoverage

/**
 * "What does the candidate have?" - answered once, for every module that asks.
 *
 * `analysis`, `market` and anything that follows all need the same thing: a profile's skills
 * expanded through the catalog's relation graph, so a requirement can be looked up rather than
 * judged. Each of them used to resolve it themselves, and `market` additionally carried the rule
 * for what an *absent* profile means as a private detail of a dashboard service. That rule is
 * stated here instead, because a second dashboard arriving with a third interpretation of "there is
 * no persona yet" is exactly how two screens start disagreeing about the same install.
 *
 * This lives in `profile` rather than in `catalog`: `catalog` depends on nothing and must keep
 * depending on nothing - that is the whole reason `triage` exists as its own module (ADR-0003) -
 * whereas `profile` already depends on `catalog`, so this is a new type on an existing edge.
 *
 * It answers a question and holds nothing. Coverage is never persisted: a verdict stored against a
 * profile would go stale the moment a skill was added to it.
 */
interface ProfileCoverage {

    /**
     * The coverage of [profileId], or of the default profile when it is null.
     *
     * Never throws for want of a profile. A null id with no persona at all, and an id whose profile
     * has no details row yet, both yield [SkillCoverage.EMPTY] - a fresh install still has a
     * meaningful demand table, every row of which reads MISSING, which is true rather than a
     * placeholder.
     *
     * **Callers that must not silently guess should pass an explicit id.** An analysis run is
     * pinned to the profile its row was queued against and would be wrong to fall back to whichever
     * profile happens to be the default.
     */
    fun of(profileId: Long?): SkillCoverage

    /**
     * The coverage of a profile the caller already holds.
     *
     * For callers that read the profile for other reasons - an analysis stamps its revision and
     * describes its evidence from the same object - so that coverage cannot be computed from a
     * second, later read of a profile that has been edited in between.
     */
    fun of(profile: CandidateProfile): SkillCoverage
}
