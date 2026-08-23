package com.jankowski.rafal.jobassistant.catalog

/** A skill as the rest of the application knows it. Free-text skill names never cross a module boundary. */
data class CanonicalSkill(
    val id: Long,
    val name: String,
    val category: SkillCategory,
)
