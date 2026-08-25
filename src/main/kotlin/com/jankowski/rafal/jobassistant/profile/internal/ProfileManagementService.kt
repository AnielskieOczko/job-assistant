package com.jankowski.rafal.jobassistant.profile.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A profile's identity, without its contents - the shape the switcher and the profile list need. */
internal data class ProfileSummary(val id: Long, val name: String, val isDefault: Boolean)

/**
 * Creating, listing, deleting and defaulting profiles themselves.
 *
 * Kept separate from [ProfileWriteService], which edits what is *inside* one profile: nothing
 * outside this module has any business deciding which personas exist, but "which personas exist"
 * is a different concern from "what does this one contain", and the two services never need each
 * other's repositories.
 */
@Service
internal class ProfileManagementService(
    private val profiles: ProfileRepository,
    private val jdbc: JdbcClient,
) {

    fun list(): List<ProfileSummary> =
        profiles.findAllOrdered().map { ProfileSummary(it.id!!, it.name, it.isDefault) }

    /** The first profile ever created becomes the default; nothing else does. */
    @Transactional
    fun create(name: String): ProfileSummary {
        val isFirst = profiles.countAll() == 0L
        val saved = profiles.save(ProfileRow(name = name, isDefault = isFirst))
        return ProfileSummary(saved.id!!, saved.name, saved.isDefault)
    }

    @Transactional
    fun setDefault(profileId: Long): ProfileSummary {
        val row = profiles.findById(profileId).orElseThrow { unknown(profileId) }
        jdbc.sql("update profile set is_default = false where is_default").update()
        jdbc.sql("update profile set is_default = true where id = :id").param("id", profileId).update()
        return ProfileSummary(profileId, row.name, true)
    }

    /**
     * Refuses to delete the default profile while another one exists - deleting the last profile is
     * always allowed, since there is then no "other" default to fall back to.
     */
    @Transactional
    fun delete(profileId: Long) {
        val row = profiles.findById(profileId).orElseThrow { unknown(profileId) }
        if (row.isDefault && profiles.countAll() > 1) {
            throw ProfileConflictException(
                "${row.name} is the default profile. Set another profile as default before deleting it."
            )
        }
        profiles.delete(row)
    }

    private fun unknown(id: Long) = UnknownProfileEntityException("No profile $id.")
}
