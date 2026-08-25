package com.jankowski.rafal.jobassistant.profile

/**
 * The profile module's public surface.
 *
 * Per-entity editing deliberately does not appear here. Nothing outside this module has any business
 * writing a single skill or bullet, so CRUD stays internal and the cross-module contract remains
 * "read a profile, or replace it wholesale". The same is true of profile *lifecycle* -- creating,
 * listing, deleting, or changing the default -- which lives on an internal management service:
 * nothing outside this module has any business deciding which personas exist.
 */
interface ProfileService {

    /** The stored profile, or null when nothing has been created or imported yet for it. */
    fun current(profileId: Long): CandidateProfile?

    /** Like [current] but throws when absent - for callers that cannot proceed without it. */
    fun require(profileId: Long): CandidateProfile

    /**
     * Replaces the entire profile in one transaction. Still a full replace rather than a patch:
     * the imported document is the profile, and merging it into existing rows would need a matching
     * rule with no good answer for a renamed employer. Requires the profile itself (the `profile`
     * root row) to already exist - creating a persona and filling in its details are separate steps.
     *
     * Every entity id is reassigned, which strands the bullet ids stored against previously
     * generated CVs - hence the revision bump and the confirmation the UI puts in front of it.
     *
     * @throws ProfileImportException if any skill cannot be resolved or a bullet is tagged with
     *   an undeclared skill.
     */
    fun replace(profileId: Long, import: ProfileImport): CandidateProfile

    /**
     * Counts writes to the given profile. An analysis or a generated document records the value it
     * was produced from, which is the only way to tell output that still reflects the profile from
     * output that has been overtaken by an edit.
     */
    fun revision(profileId: Long): Long

    /**
     * Id of the profile flagged as default. The only cross-module way to resolve "which profile" when
     * a caller has not been told one explicitly - callers that must not silently guess should require
     * an explicit id instead.
     *
     * @throws IllegalStateException when no profile exists yet.
     */
    fun defaultProfileId(): Long
}
