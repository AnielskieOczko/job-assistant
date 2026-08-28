package com.jankowski.rafal.jobassistant.market

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.market.internal.SolidJobsPage
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedSolidJobsClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/**
 * Ingestion end to end against a real Postgres, with the source scripted from a page captured from
 * the live API. The fixture is real data on purpose: the mapping is only worth testing against the
 * shapes the source actually sends, including the ones the research missed.
 */
@IntegrationTest
class MarketIngestionIntegrationTest {

    @Autowired lateinit var market: MarketOfferService
    @Autowired lateinit var client: ScriptedSolidJobsClient
    @Autowired lateinit var catalog: SkillCatalog
    @Autowired lateinit var jdbc: JdbcClient
    @Autowired lateinit var jsonMapper: JsonMapper

    @BeforeEach
    fun reset() {
        client.reset()
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from unmatched_term").update()
    }

    private fun fixturePage(): SolidJobsPage =
        jsonMapper.readValue(requireNotNull(javaClass.getResource("/market/solid-jobs-page.json")).readText())

    @Test
    fun `ingests a page into the corpus with salary and skills mapped`() {
        client.enqueue(fixturePage())

        val report = market.ingest()

        assertThat(report.error).isNull()
        assertThat(report.offersSeen).isEqualTo(4)
        assertThat(report.offersInserted).isEqualTo(4)
        assertThat(report.offersUpdated).isZero()

        val salaried = jdbc.sql(
            "select count(*) from market_offer where salary_from is not null and salary_currency = 'PLN'"
        ).query(Int::class.java).single()
        assertThat(salaried).isEqualTo(4)

        // The B2B-versus-employment distinction is the field Polish offers usually bury in prose.
        val employmentTypes = jdbc.sql("select distinct employment_type from market_offer")
            .query(String::class.java).list()
        assertThat(employmentTypes).containsExactlyInAnyOrder("B2B", "UoP")
    }

    @Test
    fun `re-ingesting the same page refreshes rather than duplicates`() {
        client.enqueue(fixturePage(), fixturePage())

        market.ingest()
        val second = market.ingest()

        assertThat(second.offersInserted).isZero()
        assertThat(second.offersUpdated).isEqualTo(4)
        assertThat(jdbc.sql("select count(*) from market_offer").query(Int::class.java).single()).isEqualTo(4)

        // first_seen_at is half of the window every statistic drawn from this corpus declares, so a
        // row re-seen on every poll must not keep reporting itself as new.
        val movedFirstSeen = jdbc.sql("select count(*) from market_offer where first_seen_at > last_seen_at")
            .query(Int::class.java).single()
        assertThat(movedFirstSeen).isZero()
    }

    @Test
    fun `NiceToHave survives ingestion as a level, because it is the source's only importance signal`() {
        client.enqueue(fixturePage())

        market.ingest()

        val levels = jdbc.sql("select distinct level from market_offer_skill").query(String::class.java).list()
        assertThat(levels).contains(MarketSkillLevel.NICE_TO_HAVE.name)
        assertThat(levels).doesNotContain(MarketSkillLevel.UNKNOWN.name)
    }

    @Test
    fun `unplaceable terms are queued for review under the market counter, not the candidate's`() {
        client.enqueue(fixturePage())

        val report = market.ingest()

        assertThat(report.distinctUnresolvedTerms).isGreaterThan(0)

        val queued = catalog.pendingUnmatchedTerms(limit = 500)
        assertThat(queued).isNotEmpty
        // Polish soft skills are in the fixture and are not in a 210-entry technology catalog.
        assertThat(queued.map { it.term }).contains(TEAM_MANAGEMENT)
        // The whole point of the second counter: market volume must not rank the queue.
        assertThat(queued).allSatisfy {
            assertThat(it.occurrences).isZero()
            assertThat(it.marketOccurrences).isGreaterThanOrEqualTo(1)
        }
        // Exact, not a floor: every unresolved term in this fixture is asked for by exactly one of
        // the four offers, so anything other than 1 means the counter is measuring something else.
        assertThat(queued.single { it.term == TEAM_MANAGEMENT }.marketOccurrences).isEqualTo(1)
    }

    /**
     * The counter's whole purpose is to say how much the market wants a term, so it has to move
     * with the number of employers asking rather than the number of times we looked.
     */
    @Test
    fun `the market counter counts offers asking, not polls run`() {
        val page = fixturePage()
        val asking = page.jobs.single { offer -> offer.skills.any { it.name == TEAM_MANAGEMENT } }
        // A second employer asking for the same thing, which the fixture alone does not contain.
        val twin = asking.copy(jobOfferKey = asking.jobOfferKey + "-twin")
        client.enqueue(page.copy(jobs = page.jobs + twin))

        market.ingest()

        val queued = catalog.pendingUnmatchedTerms(500).single { it.term == TEAM_MANAGEMENT }
        assertThat(queued.marketOccurrences).isEqualTo(2)
    }

    /**
     * Offers are upserted by key, so a daily poll re-serves the same listings. Accumulating would
     * multiply a term's demand by the number of polls and rank the queue by how long a term had
     * been listed; recomputing from the corpus makes an unchanged re-poll a no-op.
     */
    @Test
    fun `re-polling unchanged listings leaves the market counter where it is`() {
        client.enqueue(fixturePage(), fixturePage())

        market.ingest()
        val afterFirst = catalog.pendingUnmatchedTerms(500).associate { it.term to it.marketOccurrences }
        market.ingest()
        val afterSecond = catalog.pendingUnmatchedTerms(500).associate { it.term to it.marketOccurrences }

        assertThat(afterFirst).isNotEmpty
        assertThat(afterSecond).isEqualTo(afterFirst)
    }

    @Test
    fun `a skill the catalog knows is resolved to its canonical id`() {
        client.enqueue(fixturePage())

        market.ingest()

        val java = requireNotNull(catalog.resolve("Java")) { "seed catalog should carry Java" }
        val resolvedToJava = jdbc.sql(
            "select count(*) from market_offer_skill where canonical_skill_id = :id"
        ).param("id", java.id).query(Int::class.java).single()
        assertThat(resolvedToJava).isGreaterThan(0)
    }

    @Test
    fun `an offer that lists no skills is still ingested`() {
        client.enqueue(fixturePage())

        market.ingest()

        val withoutSkills = jdbc.sql(
            """
            select count(*) from market_offer o
            where not exists (select 1 from market_offer_skill s where s.market_offer_id = o.id)
            """
        ).query(Int::class.java).single()
        assertThat(withoutSkills).isEqualTo(1)
    }

    @Test
    fun `the corpus summary reports the window a statistic would have to declare`() {
        client.enqueue(fixturePage())
        market.ingest()

        val summary = market.corpusSummary().single()

        assertThat(summary.source).isEqualTo("solid.jobs")
        assertThat(summary.offers).isEqualTo(4)
        assertThat(summary.firstSeenAt).isNotNull()
        assertThat(summary.lastSeenAt).isNotNull()
    }

    @Test
    fun `a source failure leaves the rows already committed and reports the run as failed`() {
        val exploding = object : com.jankowski.rafal.jobassistant.market.internal.SolidJobsClient {
            override fun fetchPage(division: String, pageIndex: Int, pageSize: Int): SolidJobsPage =
                throw IllegalStateException("connection reset")
        }
        val isolated = com.jankowski.rafal.jobassistant.market.internal.MarketIngestion(
            client = exploding,
            repository = com.jankowski.rafal.jobassistant.market.internal.MarketOfferRepository(jdbc),
            catalog = catalog,
            properties = com.jankowski.rafal.jobassistant.market.internal.MarketProperties(),
            jsonMapper = jsonMapper,
        )

        val report = isolated.ingest()

        assertThat(report.error).contains("connection reset")
        assertThat(report.offersSeen).isZero()
    }

    private companion object {
        /** A Polish soft skill the fixture carries that a 210-entry technology catalog cannot place. */
        const val TEAM_MANAGEMENT = "Zarządzanie zespołem"
    }
}
