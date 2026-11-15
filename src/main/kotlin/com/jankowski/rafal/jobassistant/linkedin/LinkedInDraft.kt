package com.jankowski.rafal.jobassistant.linkedin

import java.time.Instant

enum class LinkedInSection {
    HEADLINE,
    ABOUT,
    EXPERIENCE_ENTRY,
    PROJECT_ENTRY,
    SKILL_OR_CREDENTIAL_POST,
}

data class LinkedInDraft(
    val id: Long,
    val profileId: Long,
    val section: LinkedInSection,
    val subjectId: Long? = null,
    val profileRevision: Long,
    val stale: Boolean,
    val content: String,
    val unheldSkills: List<String> = emptyList(),
    val modelProfile: String? = null,
    val createdAt: Instant,
)

interface LinkedInDraftService {
    fun draft(profileId: Long, section: LinkedInSection, subjectId: Long? = null, notes: String? = null): LinkedInDraft
    fun list(profileId: Long): List<LinkedInDraft>
}
