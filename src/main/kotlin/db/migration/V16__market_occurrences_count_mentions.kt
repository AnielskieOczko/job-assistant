package db.migration

import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.Connection

/**
 * Rewrites `unmatched_term.market_occurrences` from a count of *polls* into a count of *mentions*.
 *
 * Ingestion accumulated its unresolved terms into a `Set` and recorded them once per run, so a term
 * asked for by 44 employers incremented by one and read as `1`. Every market-sourced row therefore
 * carries roughly the number of times the poll has run, which is a measure of this application's
 * uptime rather than of the market. `UnmatchedTerm` always documented the other meaning -- "asked
 * for 47 times by the market" -- so this restores the number the column already claimed to hold.
 *
 * The corpus itself was never wrong: `market_offer_skill` is keyed `(market_offer_id, skill_name)`,
 * so it has held one row per offer per term all along and the true count is simply a `count(*)`.
 * That makes this a recomputation rather than a reconstruction -- nothing has to be inferred.
 *
 * Written in Kotlin for the same reason as V15: the counts key on [SkillNormalizer]'s output, and a
 * hand-written SQL equivalent would agree with it only for the characters someone remembered. That
 * matters more here than usual, because the terms being counted are largely the Polish ones V15 had
 * just taught the normaliser to fold. Calling the real function makes the agreement structural.
 *
 * The safety property that makes coupling a migration to live code acceptable also holds: on a
 * fresh database `market_offer_skill` and `unmatched_term` are both empty, so this is a no-op
 * whatever [SkillNormalizer] later becomes. It only ever writes counts derived from rows already
 * present, and never inserts.
 */
@Suppress("ClassName")
class V16__market_occurrences_count_mentions : BaseJavaMigration() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun migrate(context: Context) {
        val connection = context.connection
        val mentions = corpusMentionsByKey(connection)
        applyCounts(connection, mentions)
    }

    /**
     * Counts unresolved corpus mentions per normalised key.
     *
     * Grouped on the key rather than the raw name so that two spellings of one term sum into the
     * single queue row they share, matching what `recordUnmatchedFromMarket` does at runtime.
     */
    private fun corpusMentionsByKey(connection: Connection): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                select skill_name, count(*) as mentions
                from market_offer_skill
                where canonical_skill_id is null
                group by skill_name
                """
            ).use { rs ->
                while (rs.next()) {
                    val key = SkillNormalizer.normalize(rs.getString(1))
                    if (key.isEmpty()) continue
                    counts[key] = (counts[key] ?: 0) + rs.getInt(2)
                }
            }
        }
        return counts
    }

    /**
     * Sets each queued term's counter to what the corpus actually asks for.
     *
     * A term the corpus no longer mentions is zeroed rather than left alone. Its stored value is a
     * poll count under a column that now means mentions, so keeping it would leave two meanings
     * mixed in one column - the thing this migration exists to end. Zero is also the truthful
     * answer: the corpus is cumulative and never pruned, so no evidence in it means no employer in
     * it ever asked. Nothing is lost that was not already wrong, and `occurrences` -- the counter
     * for offers the candidate actually read - is not touched at all.
     */
    private fun applyCounts(connection: Connection, mentions: Map<String, Int>) {
        var updated = 0
        var zeroed = 0

        connection.prepareStatement(
            "update unmatched_term set market_occurrences = ? where normalized_term = ?"
        ).use { statement ->
            mentions.forEach { (key, count) ->
                statement.setInt(1, count)
                statement.setString(2, key)
                updated += statement.executeUpdate()
            }
        }

        if (mentions.isEmpty()) {
            connection.createStatement().use { statement ->
                zeroed = statement.executeUpdate(
                    "update unmatched_term set market_occurrences = 0 where market_occurrences <> 0"
                )
            }
        } else {
            connection.prepareStatement(
                """
                update unmatched_term set market_occurrences = 0
                where market_occurrences <> 0 and normalized_term <> all (?)
                """
            ).use { statement ->
                statement.setArray(1, connection.createArrayOf("text", mentions.keys.toTypedArray()))
                zeroed = statement.executeUpdate()
            }
        }

        log.info(
            "Recounted market_occurrences: {} terms set from {} corpus keys, {} zeroed as unmentioned",
            updated,
            mentions.size,
            zeroed,
        )
    }
}
