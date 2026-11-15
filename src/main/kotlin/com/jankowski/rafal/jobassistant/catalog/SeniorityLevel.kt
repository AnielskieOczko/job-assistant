package com.jankowski.rafal.jobassistant.catalog

/**
 * Seniority levels observed in the market corpus and used as a target level on a profile.
 *
 * Lives in `catalog` because both `profile` (Target Role) and `market` (offer seniority mix)
 * share it, and `catalog` depends on nothing.
 */
enum class SeniorityLevel {
    INTERN,
    JUNIOR,
    REGULAR,
    SENIOR;

    companion object {
        /**
         * Maps raw corpus strings (`Senior`, `Regular`, `Junior`, `Intern`, `None`) to a
         * [SeniorityLevel], returning `null` for `None`, blank, or unrecognised values.
         */
        fun parse(raw: String?): SeniorityLevel? {
            val normalized = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return when (normalized) {
                "intern" -> INTERN
                "junior" -> JUNIOR
                "regular", "mid" -> REGULAR
                "senior" -> SENIOR
                "none" -> null
                else -> null
            }
        }
    }
}
