package com.jankowski.rafal.jobassistant.profile

import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel

/**
 * What the candidate is aiming at, as structured data: target seniority, canonical skills, and
 * optional title keywords. Distinct from `ProfileDetails.careerGoal`, which stays free prose.
 */
data class TargetRole(
    val targetLevel: SeniorityLevel? = null,
    val skills: List<TargetRoleSkill> = emptyList(),
    val titleKeywords: List<String> = emptyList(),
)

data class TargetRoleSkill(
    val id: Long,
    val name: String,
)
