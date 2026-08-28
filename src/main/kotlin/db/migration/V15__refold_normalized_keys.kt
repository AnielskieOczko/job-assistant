package db.migration

import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp

/**
 * Repairs `normalized_alias` and `normalized_term` after [SkillNormalizer] learned to fold accented
 * letters instead of deleting them.
 *
 * Written in Kotlin rather than SQL on purpose. The keys have to agree with [SkillNormalizer]
 * *exactly*, and a hand-written `translate()` table plus `unaccent` would agree only for the
 * characters someone remembered: Turkish `ğ` decomposes under NFD and would fold in Kotlin while a
 * `translate` table dropped it. Calling the real function makes the agreement structural instead of
 * argued - which is the same reasoning behind the drift test this repository already runs.
 *
 * Coupling a migration to application code is normally a trap, because the code moves on and the
 * migration is frozen. It is safe here in the one way that matters: on a fresh database this runs
 * against a freshly seeded, pure-ASCII catalog and an empty `unmatched_term`, so it is a no-op
 * whatever [SkillNormalizer] later becomes. A migration that *inserted* rows would not have that
 * property, and should not be written this way.
 */
@Suppress("ClassName")
class V15__refold_normalized_keys : BaseJavaMigration() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun migrate(context: Context) {
        val connection = context.connection
        refoldAliases(connection)
        refoldUnmatchedTerms(connection)
        resolveTermsThatNowMatchAnAlias(connection)
    }

    private data class AliasRow(val id: Long, val skillId: Long, val alias: String, val storedKey: String)

    /**
     * Recomputes every alias key.
     *
     * Where two aliases of the **same** skill collapse onto one key the extra row is dropped: it was
     * always a duplicate, it simply could not be seen while the spellings keyed differently. Where
     * they belong to **different** skills nothing is repointed - the row keeps its old key and a
     * warning names both skills. Silently moving an alias from one skill to another is exactly the
     * failure this repository refuses elsewhere, and a human can resolve it from the log.
     */
    private fun refoldAliases(connection: Connection) {
        val rows = mutableListOf<AliasRow>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "select id, canonical_skill_id, alias, normalized_alias from skill_alias order by id"
            ).use { rs ->
                while (rs.next()) {
                    rows += AliasRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4))
                }
            }
        }

        var updated = 0
        var dropped = 0
        var skipped = 0

        rows.groupBy { SkillNormalizer.normalize(it.alias) }
            .forEach { (newKey, group) ->
                if (newKey.isEmpty()) return@forEach

                if (group.map { it.skillId }.distinct().size > 1) {
                    val keeper = group.firstOrNull { it.storedKey == newKey } ?: group.first()
                    group.filter { it.id != keeper.id }.forEach { loser ->
                        skipped++
                        log.warn(
                            "Alias '{}' (skill {}) would fold onto '{}', already held by skill {} via '{}'. " +
                                "Left unchanged - resolve it by hand rather than repointing it.",
                            loser.alias, loser.skillId, newKey, keeper.skillId, keeper.alias,
                        )
                    }
                    if (keeper.storedKey != newKey) {
                        updateAliasKey(connection, keeper.id, newKey)
                        updated++
                    }
                    return@forEach
                }

                val keeper = group.first()
                group.drop(1).forEach { duplicate ->
                    connection.prepareStatement("delete from skill_alias where id = ?").use {
                        it.setLong(1, duplicate.id)
                        it.executeUpdate()
                    }
                    dropped++
                    log.info(
                        "Dropped alias '{}' of skill {}: it folds onto the same key as '{}'.",
                        duplicate.alias, duplicate.skillId, keeper.alias,
                    )
                }
                if (keeper.storedKey != newKey) {
                    updateAliasKey(connection, keeper.id, newKey)
                    updated++
                }
            }

        log.info("Refolded skill_alias: {} updated, {} duplicates dropped, {} left for review", updated, dropped, skipped)
    }

    private fun updateAliasKey(connection: Connection, id: Long, key: String) {
        connection.prepareStatement("update skill_alias set normalized_alias = ? where id = ?").use {
            it.setString(1, key)
            it.setLong(2, id)
            it.executeUpdate()
        }
    }

    private data class TermRow(
        val id: Long,
        val term: String,
        val storedKey: String,
        val occurrences: Int,
        val marketOccurrences: Int,
        val firstSeenAt: Timestamp,
        val lastSeenAt: Timestamp,
        val status: String,
        val resolvedSkillId: Long?,
    )

    /**
     * Recomputes every queued term's key, **merging** rows that now collapse together.
     *
     * Merging rather than dropping is the point: the counters are the queue's ranking, so losing one
     * would quietly demote a term. The survivor takes the summed counts, the earliest first sighting
     * and the latest last sighting, and inherits any decision already made - a term reviewed under
     * one spelling stays reviewed under both.
     */
    private fun refoldUnmatchedTerms(connection: Connection) {
        val rows = mutableListOf<TermRow>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                select id, term, normalized_term, occurrences, market_occurrences,
                       first_seen_at, last_seen_at, status, resolved_skill_id
                from unmatched_term order by id
                """
            ).use { rs ->
                while (rs.next()) {
                    rows += TermRow(
                        id = rs.getLong(1),
                        term = rs.getString(2),
                        storedKey = rs.getString(3),
                        occurrences = rs.getInt(4),
                        marketOccurrences = rs.getInt(5),
                        firstSeenAt = rs.getTimestamp(6),
                        lastSeenAt = rs.getTimestamp(7),
                        status = rs.getString(8),
                        resolvedSkillId = rs.getLong(9).takeUnless { rs.wasNull() },
                    )
                }
            }
        }

        var merged = 0
        var updated = 0

        rows.groupBy { SkillNormalizer.normalize(it.term) }
            .forEach { (newKey, group) ->
                if (newKey.isEmpty()) return@forEach

                val keeper = group.first()
                val losers = group.drop(1)

                // Losers go first: the survivor cannot take the shared key while they still hold it.
                losers.forEach { loser ->
                    connection.prepareStatement("delete from unmatched_term where id = ?").use {
                        it.setLong(1, loser.id)
                        it.executeUpdate()
                    }
                    merged++
                }

                val decided = group.firstOrNull { it.status != "PENDING" }
                connection.prepareStatement(
                    """
                    update unmatched_term
                       set normalized_term = ?, occurrences = ?, market_occurrences = ?,
                           first_seen_at = ?, last_seen_at = ?, status = ?, resolved_skill_id = ?
                     where id = ?
                    """
                ).use {
                    it.setString(1, newKey)
                    it.setInt(2, group.sumOf { row -> row.occurrences })
                    it.setInt(3, group.sumOf { row -> row.marketOccurrences })
                    it.setTimestamp(4, group.minOf { row -> row.firstSeenAt })
                    it.setTimestamp(5, group.maxOf { row -> row.lastSeenAt })
                    it.setString(6, decided?.status ?: "PENDING")
                    val resolved = group.firstNotNullOfOrNull { row -> row.resolvedSkillId }
                    if (resolved == null) it.setNull(7, java.sql.Types.BIGINT) else it.setLong(7, resolved)
                    it.setLong(8, keeper.id)
                    it.executeUpdate()
                }
                if (keeper.storedKey != newKey || losers.isNotEmpty()) updated++
            }

        log.info("Refolded unmatched_term: {} rows updated, {} merged away", updated, merged)
    }

    /**
     * Approves any pending term whose new key now matches an alias.
     *
     * Folding can make a spelling resolve that never could before. Leaving it pending would keep the
     * review queue advertising work that is already done, which is how a queue stops being read.
     * Every migration that adds aliases owes this same step.
     */
    private fun resolveTermsThatNowMatchAnAlias(connection: Connection) {
        val resolved = connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                update unmatched_term t
                   set status = 'APPROVED', resolved_skill_id = a.canonical_skill_id
                  from skill_alias a
                 where a.normalized_alias = t.normalized_term
                   and t.status = 'PENDING'
                """
            )
        }
        if (resolved > 0) log.info("Approved {} queued terms that now resolve through an alias", resolved)
    }
}
