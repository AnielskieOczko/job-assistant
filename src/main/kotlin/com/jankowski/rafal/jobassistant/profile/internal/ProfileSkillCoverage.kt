package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileCoverage
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.springframework.stereotype.Service

/**
 * Reads a profile and expands it through the catalog. The whole of [ProfileCoverage] apart from the
 * fallback, which is the part with a rule in it.
 */
@Service
internal class ProfileSkillCoverage(
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
) : ProfileCoverage {

    override fun of(profileId: Long?): SkillCoverage {
        // defaultProfileId throws when no persona exists at all, which is a legitimate state on a
        // fresh install rather than an error worth propagating to whoever asked.
        val id = profileId ?: runCatching { profiles.defaultProfileId() }.getOrNull() ?: return SkillCoverage.EMPTY
        val profile = profiles.current(id) ?: return SkillCoverage.EMPTY
        return of(profile)
    }

    override fun of(profile: CandidateProfile): SkillCoverage = catalog.coverageFor(profile.heldSkillIds)
}
