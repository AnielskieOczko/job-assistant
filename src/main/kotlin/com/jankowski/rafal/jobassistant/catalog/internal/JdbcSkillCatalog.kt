package com.jankowski.rafal.jobassistant.catalog.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import com.jankowski.rafal.jobassistant.catalog.SkillSuggestion
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTerm
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTermStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
internal class JdbcSkillCatalog(
    private val skills: CanonicalSkillRepository,
    private val aliases: SkillAliasRepository,
    private val unmatched: UnmatchedTermRepository,
    private val jdbc: JdbcClient,
) : SkillCatalog {

    override fun findById(id: Long): CanonicalSkill? =
        skills.findById(id).orElse(null)?.toDomain()

    override fun findAllById(ids: Collection<Long>): List<CanonicalSkill> =
        if (ids.isEmpty()) emptyList() else skills.findAllById(ids).map { it.toDomain() }

    override fun findAll(): List<CanonicalSkill> =
        skills.findAllOrderByName().map { it.toDomain() }

    override fun resolve(term: String): CanonicalSkill? {
        val normalized = SkillNormalizer.normalize(term)
        if (normalized.isEmpty()) return null
        val alias = aliases.findByNormalizedAlias(normalized) ?: return null
        return findById(alias.canonicalSkillId)
    }

    override fun resolveAll(terms: Collection<String>): Map<String, CanonicalSkill?> {
        if (terms.isEmpty()) return emptyMap()
        val byNormalized = terms.associateWith { SkillNormalizer.normalize(it) }
        val lookups = byNormalized.values.filter { it.isNotEmpty() }.toSet()
        if (lookups.isEmpty()) return terms.associateWith { null }

        val skillIdByNormalized = aliases.findAllByNormalizedAliasIn(lookups)
            .associate { it.normalizedAlias to it.canonicalSkillId }
        val skillsById = findAllById(skillIdByNormalized.values.toSet()).associateBy { it.id }

        return byNormalized.mapValues { (_, normalized) ->
            skillIdByNormalized[normalized]?.let { skillsById[it] }
        }
    }

    override fun coverageFor(heldSkillIds: Set<Long>): SkillCoverage {
        if (heldSkillIds.isEmpty()) return SkillCoverage.EMPTY

        val implied = edges(heldSkillIds, "IMPLIES")
        val related = edges(heldSkillIds, "RELATED")

        // A skill reachable by IMPLIES is fully covered, so it must not also count as PARTIAL,
        // and a skill held directly is never explained by an edge.
        return SkillCoverage(
            held = heldSkillIds,
            impliedBy = implied - heldSkillIds,
            relatedBy = related - heldSkillIds - implied.keys,
        )
    }

    /** Maps each reachable target skill to one held skill that reaches it. */
    private fun edges(fromIds: Set<Long>, kind: String): Map<Long, Long> =
        jdbc.sql(
            "select to_skill_id, min(from_skill_id) as from_skill_id " +
                "from skill_relation where kind = :kind and from_skill_id in (:ids) " +
                "group by to_skill_id"
        )
            .param("kind", kind)
            .param("ids", fromIds)
            .query { rs, _ -> rs.getLong("to_skill_id") to rs.getLong("from_skill_id") }
            .list()
            .toMap()

    @Transactional
    override fun recordUnmatched(term: String) {
        val trimmed = term.trim()
        val normalized = SkillNormalizer.normalize(trimmed)
        if (normalized.isEmpty()) return

        jdbc.sql(
            """
            insert into unmatched_term (term, normalized_term)
            values (:term, :normalized)
            on conflict (normalized_term) do update
                set occurrences = unmatched_term.occurrences + 1,
                    last_seen_at = now()
            """
        ).param("term", trimmed).param("normalized", normalized).update()
    }

    @Transactional
    override fun recordUnmatchedFromMarket(mentions: Map<String, Int>) {
        // Grouped on the normalised key, not the raw one: "Power Apps" and "power apps" are the
        // same queue entry, so their counts are summed rather than one spelling winning. The first
        // spelling seen supplies the display term, exactly as it did before.
        val byNormalized = LinkedHashMap<String, Pair<String, Int>>()
        mentions.forEach { (rawTerm, count) ->
            val term = rawTerm.trim()
            if (term.isEmpty() || count <= 0) return@forEach
            val normalized = SkillNormalizer.normalize(term)
            if (normalized.isEmpty()) return@forEach

            val running = byNormalized[normalized]
            byNormalized[normalized] =
                if (running == null) term to count else running.first to (running.second + count)
        }

        byNormalized.forEach { (normalized, entry) ->
            val (term, count) = entry
            // Set, never accumulate. The caller hands over the corpus's own count, so a re-poll of
            // unchanged listings must leave the number where it is -- see recordUnmatchedFromMarket
            // on SkillCatalog for why incrementing would rank the queue by how often we looked.
            jdbc.sql(
                """
                insert into unmatched_term (term, normalized_term, occurrences, market_occurrences)
                values (:term, :normalized, 0, :count)
                on conflict (normalized_term) do update
                    set market_occurrences = :count,
                        last_seen_at = now()
                """
            ).param("term", term).param("normalized", normalized).param("count", count).update()
        }
    }

    override fun suggest(term: String, limit: Int): List<SkillSuggestion> =
        suggestAll(listOf(term), limit)[term] ?: emptyList()

    override fun suggestAll(terms: Collection<String>, limit: Int): Map<String, List<SkillSuggestion>> {
        if (terms.isEmpty()) return emptyMap()

        // Loaded once for the whole batch. The queue asks for a hundred terms at a time, and doing
        // this per term would be a hundred full reads of the catalog to answer one page.
        val skillsById = skills.findAllOrderByName().associateBy { requireNotNull(it.id) }
        val candidates = candidateIndex(skillsById)

        return terms.associateWith { term ->
            SkillSimilarity.rank(SkillNormalizer.normalize(term), candidates, limit)
                .mapNotNull { match ->
                    val skill = skillsById[match.skillId] ?: return@mapNotNull null
                    SkillSuggestion(
                        skillId = match.skillId,
                        skillName = skill.name,
                        category = SkillCategory.valueOf(skill.category),
                        matchedAlias = match.spelling,
                        // Rounded for the wire only, after ranking: a chip label reading
                        // 0.6666666666666666 helps nobody, and rounding earlier would create ties
                        // the ordering would then have to break arbitrarily.
                        score = round(match.score),
                    )
                }
        }
    }

    /**
     * Every spelling the catalog knows, canonical names and aliases alike.
     *
     * Names are included explicitly rather than trusted to be present as aliases. They almost always
     * are - `createSkill` registers one - but a name missing from `skill_alias` would silently make
     * a skill unsuggestable, and the failure would look like the scoring being wrong.
     */
    private fun candidateIndex(
        skillsById: Map<Long, CanonicalSkillRow>,
    ): List<SkillSimilarity.Candidate> {
        val fromNames = skillsById.map { (id, row) ->
            SkillSimilarity.Candidate(id, row.name, SkillNormalizer.normalize(row.name))
        }
        val fromAliases = aliases.findAll().map { row ->
            SkillSimilarity.Candidate(row.canonicalSkillId, row.alias, row.normalizedAlias)
        }
        return fromNames + fromAliases
    }

    private fun round(score: Double) = Math.round(score * 1000.0) / 1000.0

    override fun pendingUnmatchedTerms(limit: Int): List<UnmatchedTerm> =
        unmatched.findPending(limit).map { it.toDomain() }

    override fun allPendingUnmatchedTerms(): List<UnmatchedTerm> =
        unmatched.findAllPending().map { it.toDomain() }

    @Transactional
    override fun approveUnmatchedTerm(termId: Long, skillId: Long): CanonicalSkill {
        val row = unmatched.findById(termId).orElseThrow {
            IllegalArgumentException("No unmatched term $termId")
        }
        val skill = findById(skillId)
            ?: throw IllegalArgumentException("No canonical skill $skillId")

        // Approving means "this phrasing means that skill", so it becomes a permanent alias and
        // every future extraction resolves it without another trip through the review queue.
        //
        // That promise cannot be kept if the key already belongs to a different skill. skill_alias
        // is unique on normalized_alias, so the existing row keeps winning every resolve() and the
        // approval would be a lie the queue then hides: the term leaves PENDING stamped against a
        // skill it will never resolve to, and no later review can surface it again. Refuse, and
        // name the owner so the reviewer can approve against it instead.
        val existing = aliases.findByNormalizedAlias(row.normalizedTerm)
        if (existing != null && existing.canonicalSkillId != skillId) {
            val owner = findById(existing.canonicalSkillId)?.name ?: "another skill"
            throw CatalogConflictException(
                "\"${row.term}\" already resolves to \"$owner\", so it cannot also mean " +
                    "\"${skill.name}\". Approve it against \"$owner\", or reject it."
            )
        }
        if (existing == null) {
            aliases.save(
                SkillAliasRow(
                    canonicalSkillId = skillId,
                    alias = row.term,
                    normalizedAlias = row.normalizedTerm,
                )
            )
        }
        unmatched.save(row.copy(status = "APPROVED", resolvedSkillId = skillId))
        return skill
    }

    @Transactional
    override fun createSkill(
        name: String,
        category: SkillCategory,
        aliases: Collection<String>,
    ): CanonicalSkill {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "skill name must not be blank" }

        val existing = skills.findAllOrderByName().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val skill = existing?.toDomain()
            ?: skills.save(CanonicalSkillRow(name = trimmed, category = category.name)).toDomain()

        (listOf(trimmed) + aliases).forEach { candidate ->
            val normalized = SkillNormalizer.normalize(candidate)
            if (normalized.isEmpty()) return@forEach

            val owner = this.aliases.findByNormalizedAlias(normalized)
            require(owner == null || owner.canonicalSkillId == skill.id) {
                "alias '$candidate' already resolves to a different skill"
            }
            if (owner == null) {
                this.aliases.save(
                    SkillAliasRow(
                        canonicalSkillId = skill.id,
                        alias = candidate.trim(),
                        normalizedAlias = normalized,
                    )
                )
            }
        }
        return skill
    }

    @Transactional
    override fun updateSkill(id: Long, name: String, category: SkillCategory): CanonicalSkill {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "skill name must not be blank" }

        val row = skills.findById(id).orElse(null) ?: throw UnknownSkillException("No canonical skill $id")

        val conflict = skills.findAllOrderByName()
            .firstOrNull { it.id != id && it.name.equals(trimmed, ignoreCase = true) }
        if (conflict != null) {
            throw CatalogConflictException("A skill named \"$trimmed\" already exists.")
        }

        val updated = skills.save(row.copy(name = trimmed, category = category.name)).toDomain()

        // The new name has to resolve too, exactly as createSkill registers one for a fresh skill.
        // Old aliases are left alone, so nothing that already resolved via the previous name breaks.
        val normalized = SkillNormalizer.normalize(trimmed)
        if (normalized.isNotEmpty() && aliases.findByNormalizedAlias(normalized) == null) {
            aliases.save(SkillAliasRow(canonicalSkillId = id, alias = trimmed, normalizedAlias = normalized))
        }
        return updated
    }

    @Transactional
    override fun deleteSkill(id: Long) {
        val row = skills.findById(id).orElse(null) ?: throw UnknownSkillException("No canonical skill $id")
        try {
            skills.delete(row)
        } catch (_: DataIntegrityViolationException) {
            throw CatalogConflictException(
                "\"${row.name}\" is still held by a profile or cited by a bullet, and cannot be deleted."
            )
        }
    }

    @Transactional
    override fun rejectUnmatchedTerm(termId: Long) {
        val row = unmatched.findById(termId).orElseThrow {
            IllegalArgumentException("No unmatched term $termId")
        }
        unmatched.save(row.copy(status = "REJECTED"))
    }
}

private fun CanonicalSkillRow.toDomain() = CanonicalSkill(
    id = requireNotNull(id) { "persisted skill without id" },
    name = name,
    category = SkillCategory.valueOf(category),
)

private fun UnmatchedTermRow.toDomain() = UnmatchedTerm(
    id = requireNotNull(id) { "persisted term without id" },
    term = term,
    occurrences = occurrences,
    marketOccurrences = marketOccurrences,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    status = UnmatchedTermStatus.valueOf(status),
    resolvedSkillId = resolvedSkillId,
)
