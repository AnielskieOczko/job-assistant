package com.jankowski.rafal.jobassistant.catalog

enum class CoverageStatus {
    /** Held directly, or covered by a skill that IMPLIES it (Spring Boot covers Spring). */
    MET,

    /** Not held, but an adjacent skill is (Quarkus against a Spring Boot requirement). */
    PARTIAL,

    /** Nothing in the profile covers it. */
    MISSING;

    /**
     * Where this status sits when what the candidate lacks leads: MISSING, then PARTIAL, then MET.
     *
     * Written out rather than taken from [ordinal]. The declaration order above happens to give the
     * right answer today, which is exactly why it must not be depended on: reordering the enum for
     * readability would silently invert every ranking built on it, with no test naming the
     * connection. `SkillCoverageTest` asserts the order independently of the declaration, so that
     * reordering fails a test rather than a dashboard.
     */
    val unmetRank: Int
        get() = when (this) {
            MISSING -> 0
            PARTIAL -> 1
            MET -> 2
        }

    companion object {
        /**
         * Orders statuses MISSING, PARTIAL, MET - the one home for "lead with what is unmet".
         *
         * A table led by Java - held, and asked for by 165 offers - is a true table that answers
         * nothing, so every surface ranking demand against a profile leads with the unmet end.
         *
         * A ranking built on this still needs its own final tie-break on a name or an id: without a
         * *total* order two entries that compare equal can swap between requests, and a page
         * boundary then shows one of them twice and the other never.
         */
        val UNMET_FIRST: Comparator<CoverageStatus> = compareBy { it.unmetRank }
    }
}
