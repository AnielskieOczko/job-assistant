package com.jankowski.rafal.jobassistant.catalog

enum class CoverageStatus {
    /** Held directly, or covered by a skill that IMPLIES it (Spring Boot covers Spring). */
    MET,

    /** Not held, but an adjacent skill is (Quarkus against a Spring Boot requirement). */
    PARTIAL,

    /** Nothing in the profile covers it. */
    MISSING,
}
