package com.jankowski.rafal.jobassistant.catalog.internal

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

internal interface CanonicalSkillRepository : CrudRepository<CanonicalSkillRow, Long> {
    @Query("select * from canonical_skill order by name")
    fun findAllOrderByName(): List<CanonicalSkillRow>
}

internal interface SkillAliasRepository : CrudRepository<SkillAliasRow, Long> {
    fun findByNormalizedAlias(normalizedAlias: String): SkillAliasRow?
    fun findAllByNormalizedAliasIn(normalizedAliases: Collection<String>): List<SkillAliasRow>
}

internal interface UnmatchedTermRepository : CrudRepository<UnmatchedTermRow, Long> {
    fun findByNormalizedTerm(normalizedTerm: String): UnmatchedTermRow?

    @Query(
        """
        select * from unmatched_term
        where status = 'PENDING'
        order by occurrences desc, last_seen_at desc
        limit :limit
        """
    )
    fun findPending(limit: Int): List<UnmatchedTermRow>
}
