package com.jankowski.rafal.jobassistant.catalog.internal

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("canonical_skill")
internal data class CanonicalSkillRow(
    @Id val id: Long? = null,
    val name: String,
    val category: String,
    // Explicit rather than relying on `default now()`: Spring Data JDBC writes every mapped
    // property, so a null here would be sent as an explicit NULL and violate the constraint.
    val createdAt: Instant = Instant.now(),
)

@Table("skill_alias")
internal data class SkillAliasRow(
    @Id val id: Long? = null,
    val canonicalSkillId: Long,
    val alias: String,
    val normalizedAlias: String,
)

@Table("unmatched_term")
internal data class UnmatchedTermRow(
    @Id val id: Long? = null,
    val term: String,
    val normalizedTerm: String,
    val occurrences: Int,
    val marketOccurrences: Int = 0,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val status: String,
    val resolvedSkillId: Long?,
)
