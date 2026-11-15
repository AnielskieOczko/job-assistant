package com.jankowski.rafal.jobassistant.profile

/**
 * Narrow read seam for the profile's [TargetRole], modelled on [ProfileCoverage].
 *
 * `market` and `career` read the target role through this interface without pulling in profile
 * write services.
 */
interface ProfileTargetRole {

    /**
     * The target role of [profileId], or of the default profile when it is null.
     *
     * Never throws for want of a profile or a target role: returns null when no persona exists yet
     * or when no target role has been configured for it.
     */
    fun of(profileId: Long?): TargetRole?
}
