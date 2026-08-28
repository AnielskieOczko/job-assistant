package com.jankowski.rafal.jobassistant.market

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import db.migration.V16__market_occurrences_count_mentions
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
 * The recount migration, exercised against rows that carry poll counts.
 *
 * Like the refold migration this cannot be observed the usual way: by the time a test runs Flyway
 * has already applied V16 to a freshly seeded database with an empty corpus, where it had nothing
 * to do. So it is invoked directly over a corpus built to look like the one a poll leaves behind.
 */
@IntegrationTest
class MarketOccurrenceRecountIntegrationTest {

    @Autowired lateinit var jdbc: JdbcClient
    @Autowired lateinit var dataSource: DataSource

    private val suffix = System.nanoTime()

    @BeforeEach
    fun clearCorpusAndQueue() {
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from unmatched_term").update()
    }

    private fun runMigration() {
        dataSource.connection.use { connection ->
            V16__market_occurrences_count_mentions().migrate(object : Context {
                override fun getConfiguration(): Configuration? = null
                override fun getConnection(): Connection = connection
            })
        }
    }

    /** One corpus offer asking for each of [skillNames]. */
    private fun offerAskingFor(vararg skillNames: String) {
        val id = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, payload)
            values ('test', :key, 'Test Offer', cast('{}' as jsonb))
            returning id
            """
        ).param("key", "key-${System.nanoTime()}").query(Long::class.java).single()

        skillNames.forEach { name ->
            jdbc.sql(
                """
                insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
                values (:id, :name, 'UNKNOWN', null)
                """
            ).param("id", id).param("name", name).update()
        }
    }

    /** A queue row as the old rule left it: a count of polls, not of employers. */
    private fun queueTerm(term: String, key: String, pollCount: Int, ownOccurrences: Int = 0) {
        jdbc.sql(
            """
            insert into unmatched_term (term, normalized_term, occurrences, market_occurrences)
            values (:term, :key, :own, :market)
            """
        )
            .param("term", term).param("key", key)
            .param("own", ownOccurrences).param("market", pollCount)
            .update()
    }

    private fun marketCountOf(key: String): Int = jdbc
        .sql("select market_occurrences from unmatched_term where normalized_term = :key")
        .param("key", key).query(Int::class.java).single()

    @Test
    fun `a poll count is replaced by the number of offers asking`() {
        val term = "Test automation $suffix"
        val key = "testautomation$suffix"
        repeat(3) { offerAskingFor(term) }
        // What three polls of those same three offers had left behind.
        queueTerm(term, key, pollCount = 3)

        runMigration()

        assertThat(marketCountOf(key)).isEqualTo(3)
    }

    /**
     * The coincidence worth separating: with one offer per poll the old and new numbers agree, so
     * the count has to be driven by offers rather than by anything that merely tracked them.
     */
    @Test
    fun `the count follows the corpus, not the number of polls that built it`() {
        val term = "Manual testing $suffix"
        val key = "manualtesting$suffix"
        repeat(7) { offerAskingFor(term) }
        queueTerm(term, key, pollCount = 1)

        runMigration()

        assertThat(marketCountOf(key)).isEqualTo(7)
    }

    @Test
    fun `spellings of one term sum onto the single queue row they share`() {
        val key = "apitesting$suffix"
        offerAskingFor("API testing $suffix")
        offerAskingFor("api testing $suffix")
        offerAskingFor("API Testing $suffix")
        queueTerm("API testing $suffix", key, pollCount = 1)

        runMigration()

        assertThat(marketCountOf(key)).isEqualTo(3)
    }

    /**
     * A term the corpus never mentions is zeroed rather than left holding a poll count. The corpus
     * accumulates and is never pruned, so no evidence in it means no employer in it ever asked, and
     * leaving the old value would keep two meanings mixed in one column.
     */
    @Test
    fun `a term the corpus does not mention is zeroed`() {
        val key = "vanished$suffix"
        queueTerm("Vanished Term $suffix", key, pollCount = 4)
        offerAskingFor("Something Else $suffix")

        runMigration()

        assertThat(marketCountOf(key)).isZero()
    }

    /** The candidate's own counter ranks the queue and is no business of this migration. */
    @Test
    fun `the candidate's own occurrence count is untouched`() {
        val term = "Read In An Offer $suffix"
        val key = "readinanoffer$suffix"
        repeat(2) { offerAskingFor(term) }
        queueTerm(term, key, pollCount = 1, ownOccurrences = 5)

        runMigration()

        val own = jdbc.sql("select occurrences from unmatched_term where normalized_term = :key")
            .param("key", key).query(Int::class.java).single()
        assertThat(own).isEqualTo(5)
        assertThat(marketCountOf(key)).isEqualTo(2)
    }

    /** A mention the catalog already places is not queue business and must not be counted. */
    @Test
    fun `resolved mentions are not counted`() {
        val term = "Resolved Term $suffix"
        val key = "resolvedterm$suffix"
        val skillId = jdbc.sql("select id from canonical_skill order by id limit 1")
            .query(Long::class.java).single()

        offerAskingFor(term)
        val offerId = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, payload)
            values ('test', :key, 'Resolved Offer', cast('{}' as jsonb))
            returning id
            """
        ).param("key", "resolved-${System.nanoTime()}").query(Long::class.java).single()
        jdbc.sql(
            """
            insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
            values (:id, :name, 'UNKNOWN', :skillId)
            """
        ).param("id", offerId).param("name", term).param("skillId", skillId).update()

        queueTerm(term, key, pollCount = 1)

        runMigration()

        assertThat(marketCountOf(key)).isEqualTo(1)
    }

    /** On a fresh database there is no corpus and nothing queued, so the migration must do nothing. */
    @Test
    fun `an empty corpus and an empty queue are a no-op`() {
        runMigration()

        val queued = jdbc.sql("select count(*) from unmatched_term").query(Int::class.java).single()
        assertThat(queued).isZero()
    }
}
