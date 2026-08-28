package com.jankowski.rafal.jobassistant.triage.internal

import com.jankowski.rafal.jobassistant.triage.ModelSuggestion
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * Stored model suggestions.
 *
 * `JdbcClient` rather than Spring Data JDBC: every read here joins to `canonical_skill` to render a
 * suggestion, and the write is an upsert an aggregate save cannot express.
 */
@Repository
internal class TriageSuggestionRepository(private val jdbc: JdbcClient) {

    /**
     * Replaces a term's suggestions with [skillIds], keeping the rationale for each.
     *
     * Delete-then-insert rather than merge: a re-run is a fresh opinion, and leaving a previous
     * model's discarded guess on the row would show a reviewer something nothing now proposes.
     */
    fun replaceFor(termId: Long, suggestions: List<Pair<Long, String?>>, modelProfile: String?) {
        jdbc.sql("delete from triage_suggestion where unmatched_term_id = :termId")
            .param("termId", termId)
            .update()

        suggestions.forEach { (skillId, rationale) ->
            jdbc.sql(
                """
                insert into triage_suggestion (unmatched_term_id, canonical_skill_id, rationale, model_profile)
                values (:termId, :skillId, :rationale, :modelProfile)
                on conflict (unmatched_term_id, canonical_skill_id) do update
                    set rationale = excluded.rationale, model_profile = excluded.model_profile
                """
            )
                .param("termId", termId)
                .param("skillId", skillId)
                .param("rationale", rationale)
                .param("modelProfile", modelProfile)
                .update()
        }
    }

    /** Suggestions for the given terms, keyed by term id. Empty map for an empty request. */
    fun findFor(termIds: Collection<Long>): Map<Long, List<ModelSuggestion>> {
        if (termIds.isEmpty()) return emptyMap()

        return jdbc.sql(
            """
            select s.unmatched_term_id, s.canonical_skill_id, s.rationale, s.model_profile,
                   cs.name, cs.category
            from triage_suggestion s
            join canonical_skill cs on cs.id = s.canonical_skill_id
            where s.unmatched_term_id in (:termIds)
            order by s.unmatched_term_id, cs.name
            """
        )
            .param("termIds", termIds)
            .query { rs, _ ->
                rs.getLong("unmatched_term_id") to ModelSuggestion(
                    skillId = rs.getLong("canonical_skill_id"),
                    skillName = rs.getString("name"),
                    category = SkillCategory.valueOf(rs.getString("category")),
                    rationale = rs.getString("rationale"),
                    modelProfile = rs.getString("model_profile"),
                )
            }
            .list()
            .groupBy({ it.first }, { it.second })
    }

    /** Term ids that already carry a suggestion, so a re-run can skip them. */
    fun termsWithSuggestions(termIds: Collection<Long>): Set<Long> {
        if (termIds.isEmpty()) return emptySet()

        return jdbc.sql(
            "select distinct unmatched_term_id from triage_suggestion where unmatched_term_id in (:termIds)"
        )
            .param("termIds", termIds)
            .query { rs, _ -> rs.getLong("unmatched_term_id") }
            .list()
            .toSet()
    }
}
