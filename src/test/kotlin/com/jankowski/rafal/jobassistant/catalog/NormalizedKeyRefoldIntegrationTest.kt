package com.jankowski.rafal.jobassistant.catalog

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import db.migration.V15__refold_normalized_keys
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Connection
import javax.sql.DataSource

/**
 * The refold migration, exercised against rows that carry pre-fold keys.
 *
 * It cannot be observed the usual way: by the time a test runs, Flyway has already applied V15 to a
 * freshly seeded database where it had nothing to do. So the migration is invoked directly over
 * rows inserted to look like the ones a developer's database actually holds.
 */
@IntegrationTest
class NormalizedKeyRefoldIntegrationTest {

    @Autowired lateinit var jdbc: JdbcClient
    @Autowired lateinit var dataSource: DataSource
    @Autowired lateinit var catalog: SkillCatalog

    private val suffix = System.nanoTime()

    @BeforeEach
    fun clearQueue() {
        jdbc.sql("delete from unmatched_term").update()
    }

    private fun runMigration() {
        dataSource.connection.use { connection ->
            V15__refold_normalized_keys().migrate(object : Context {
                override fun getConfiguration(): Configuration? = null
                override fun getConnection(): Connection = connection
            })
        }
    }

    private fun queueTerm(term: String, staleKey: String, occurrences: Int = 1, market: Int = 0) {
        jdbc.sql(
            """
            insert into unmatched_term (term, normalized_term, occurrences, market_occurrences)
            values (:term, :key, :occurrences, :market)
            """
        )
            .param("term", term).param("key", staleKey)
            .param("occurrences", occurrences).param("market", market)
            .update()
    }

    @Test
    fun `a stale key computed before folding is repaired`() {
        // What the old rule produced: the diacritics were deleted rather than folded.
        queueTerm("Zarządzanie zespołem", "zarzdzaniezespoem")

        runMigration()

        val key = jdbc.sql("select normalized_term from unmatched_term where term = :t")
            .param("t", "Zarządzanie zespołem")
            .query(String::class.java).single()
        assertThat(key).isEqualTo(SkillNormalizer.normalize("Zarządzanie zespołem"))
        assertThat(key).isEqualTo("zarzadzaniezespolem")
    }

    @Test
    fun `two spellings that now fold together are merged, keeping every count`() {
        queueTerm("Współpraca", "wsppraca", occurrences = 2, market = 30)
        queueTerm("Wspolpraca", "wspolpraca", occurrences = 5, market = 11)

        runMigration()

        val rows = jdbc.sql(
            "select term, occurrences, market_occurrences from unmatched_term where normalized_term = 'wspolpraca'"
        ).query { rs, _ -> Triple(rs.getString(1), rs.getInt(2), rs.getInt(3)) }.list()

        // One row survives, and it carries the sum - the counters rank the queue, so losing one
        // would silently demote the term.
        assertThat(rows).hasSize(1)
        assertThat(rows.single().second).isEqualTo(7)
        assertThat(rows.single().third).isEqualTo(41)
    }

    @Test
    fun `a decision already taken survives the merge`() {
        val skill = catalog.createSkill("Refold Target $suffix", SkillCategory.OTHER)
        queueTerm("Dokładność", "dokadno")
        queueTerm("Dokladnosc", "dokladnosc")
        jdbc.sql("update unmatched_term set status = 'REJECTED' where term = 'Dokladnosc'").update()

        runMigration()

        val status = jdbc.sql("select status from unmatched_term where normalized_term = 'dokladnosc'")
            .query(String::class.java).single()
        assertThat(status).isEqualTo("REJECTED")
        catalog.deleteSkill(skill.id)
    }

    @Test
    fun `a term that now matches an alias stops waiting in the queue`() {
        // "Kubernetes" is seeded, so this term resolves the moment its key is computed correctly.
        queueTerm("Kubernetes", "kubernets")

        runMigration()

        val row = jdbc.sql(
            "select status, resolved_skill_id from unmatched_term where term = 'Kubernetes'"
        ).query { rs, _ -> rs.getString(1) to rs.getLong(2) }.single()
        assertThat(row.first).isEqualTo("APPROVED")
        assertThat(row.second).isEqualTo(requireNotNull(catalog.resolve("Kubernetes")).id)
    }

    @Test
    fun `every stored alias key agrees with the normalizer`() {
        // The mirror of the drift test that guards skill_alias. A queued term whose key disagrees
        // with the normalizer is invisible to resolution and duplicates on the next sighting.
        queueTerm("Analiza wymagań", SkillNormalizer.normalize("Analiza wymagań"))

        val drifted = jdbc.sql("select term, normalized_term from unmatched_term")
            .query { rs, _ -> rs.getString(1) to rs.getString(2) }
            .list()
            .filter { (term, stored) -> SkillNormalizer.normalize(term) != stored }

        assertThat(drifted).isEmpty()
    }
}
