package com.jankowski.rafal.jobassistant.profile

/** The profile module's public surface. */
interface ProfileService {

    /** The stored profile, or null when nothing has been imported yet. */
    fun current(): CandidateProfile?

    /** Like [current] but throws when absent - for callers that cannot proceed without it. */
    fun require(): CandidateProfile

    /**
     * Replaces the entire profile in one transaction. Full replace rather than patch: the profile
     * is a single hand-authored document, and partial updates would make it hard to reason about
     * what a CV was generated from.
     *
     * @throws ProfileImportException if any skill cannot be resolved or a bullet is tagged with
     *   an undeclared skill.
     */
    fun replace(import: ProfileImport): CandidateProfile
}
