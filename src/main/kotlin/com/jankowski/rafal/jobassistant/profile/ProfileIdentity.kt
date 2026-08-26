package com.jankowski.rafal.jobassistant.profile

/**
 * The parts of a profile that identify the person behind it, without the bulk of what they have
 * done.
 *
 * This exists so the privacy guard can ask "what values must never appear in an outgoing prompt"
 * without loading every experience and bullet of every profile on every model call. The profile
 * module owns the answer because it is the only place that knows which column is a name and which
 * is an employer; classifying those values as identifiers, and deciding what to do about them, is
 * someone else's job.
 */
data class ProfileIdentity(
    val profileId: Long,
    val fullName: String,
    val email: String?,
    val phone: String?,
    val linkUrls: List<String>,
)
