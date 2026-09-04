package com.jankowski.rafal.jobassistant.market

import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.market.internal.SolidJobsPage
import com.jankowski.rafal.jobassistant.market.internal.SolidJobsPages
import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.OfferOrigin
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.SkillImport
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import com.jankowski.rafal.jobassistant.support.ScriptedSolidJobsClient
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Copying one corpus listing into the offer list.
 *
 * The corpus and the offer list are separate tables precisely so that thousands of observations do
 * not become thousands of applications nobody chose, so the tests that matter most here are the
 * ones about what promotion does *not* do: ingestion creates no offers, and a promoted listing is
 * one offer however many times it is promoted.
 */
@IntegrationTest
internal class MarketPromotionIntegrationTest(
    @Autowired private val promotion: MarketPromotion,
    @Autowired private val market: MarketOfferService,
    @Autowired private val offers: OfferService,
    @Autowired private val analyses: AnalysisService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val models: ScriptedModels,
    @Autowired private val client: ScriptedSolidJobsClient,
    @Autowired private val jsonMapper: JsonMapper,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun setUp() {
        models.resetAll()
        client.reset()
        jdbc.sql("delete from job_offer").update()
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from profile").update()
    }

    private fun ingestFixture() {
        client.enqueue(fixturePage())
        market.ingest()
    }

    private fun fixturePage(): SolidJobsPage = SolidJobsPages.parse(
        requireNotNull(javaClass.getResource("/market/solid-jobs-page.json")).readText(),
        jsonMapper,
    )

    private fun corpusId(titleFragment: String): Long = jdbc
        .sql("select id from market_offer where title like :title order by id limit 1")
        .param("title", "%$titleFragment%")
        .query(Long::class.java)
        .single()

    /**
     * The separation the whole corpus design rests on. Stated as its own assertion because the
     * failure it guards against is silent: nothing about a poll would look wrong on the day the
     * offer list quietly filled up with listings nobody chose.
     */
    @Test
    fun `ingestion alone creates no job offers`() {
        ingestFixture()

        assertEquals(4, jdbc.sql("select count(*) from market_offer").query(Int::class.java).single())
        assertEquals(0, jdbc.sql("select count(*) from job_offer").query(Int::class.java).single())
        assertTrue(offers.list().isEmpty())
    }

    @Test
    fun `promoting a listing stores it as an offer with its provenance and a saved application`() {
        ingestFixture()
        val marketOfferId = corpusId("Java Developer")

        val promoted = promotion.promote(marketOfferId)

        assertFalse(promoted.deduplicated)
        assertEquals(marketOfferId, promoted.marketOfferId)

        val offer = assertNotNull(offers.findById(promoted.offerId))
        assertEquals(OfferOrigin.MARKET, offer.origin)
        assertEquals(marketOfferId, offer.marketOfferId)
        assertEquals(ApplicationStatus.SAVED, assertNotNull(offers.applicationFor(offer.id)).status)
    }

    /** The link back to the posting is what `JobOffer.sourceUrl` was already for. */
    @Test
    fun `a promoted offer carries the employer's prose and the listing's facts`() {
        ingestFixture()

        val offer = assertNotNull(offers.findById(promotion.promote(corpusId("Java Developer")).offerId))

        assertTrue(offer.rawText.startsWith("Java Developer"))
        assertTrue(offer.rawText.contains("Czym będziesz się zajmować?"))
        assertNotNull(offer.sourceUrl)
        // Markup is converted, never stored: the extractor reads prose, not a div.
        assertFalse(offer.rawText.contains("<p>"))
        assertFalse(offer.rawText.contains("class="))
    }

    /**
     * The corpus's skills are its own structured field, already resolved against the catalog.
     * Feeding them back in would have the extractor read our resolution and hand it back, making
     * the resulting match score the market dashboard's coverage number under a second name.
     */
    @Test
    fun `the listing's resolved skill list is not written into the offer text`() {
        ingestFixture()
        val marketOfferId = corpusId("Java Developer")
        val skills = jdbc.sql("select skill_name from market_offer_skill where market_offer_id = :id")
            .param("id", marketOfferId)
            .query(String::class.java)
            .list()
            .filterNotNull()

        val offer = assertNotNull(offers.findById(promotion.promote(marketOfferId).offerId))

        // Only skills the employer's own prose happens to mention may appear.
        val header = offer.rawText.substringBefore("\n\n")
        assertTrue(skills.none { header.contains(it) }, "header must not restate the skill list: $header")
    }

    @Test
    fun `promoting the same listing twice returns the offer already stored`() {
        ingestFixture()
        val marketOfferId = corpusId("Java Developer")

        val first = promotion.promote(marketOfferId)
        val second = promotion.promote(marketOfferId)

        assertFalse(first.deduplicated)
        assertTrue(second.deduplicated)
        assertEquals(first.offerId, second.offerId)
        assertEquals(1, jdbc.sql("select count(*) from job_offer").query(Int::class.java).single())
    }

    /**
     * A row ingested before V28 has no prose and can only gain one by being re-polled, which a
     * delisted listing never will be. Refusing says so; composing an offer out of the structured
     * fields would produce a gap report that looks like every other one and is not comparable.
     */
    @Test
    fun `a listing with no posting text is refused rather than invented`() {
        ingestFixture()
        val marketOfferId = corpusId("Java Developer")
        jdbc.sql("update market_offer set description = null where id = :id")
            .param("id", marketOfferId)
            .update()

        val failure = assertThrows<OfferNotPromotableException> { promotion.promote(marketOfferId) }

        assertEquals(marketOfferId, failure.marketOfferId)
        assertEquals(0, jdbc.sql("select count(*) from job_offer").query(Int::class.java).single())
    }

    @Test
    fun `an unknown listing is a missing element rather than an empty offer`() {
        assertThrows<NoSuchElementException> { promotion.promote(999_999) }
    }

    /**
     * Promotion is a shortcut, not a second class of offer: a promoted row has to run the same
     * pipeline a pasted one does, or the corpus has produced something that only looks like an
     * offer until someone tries to use it.
     */
    @Test
    fun `a promoted offer analyses exactly like a pasted one`() {
        ingestFixture()
        val profileId = management.create("Test").id
        profiles.replace(
            profileId,
            ProfileImport(
                details = ProfileDetails(fullName = "Test Candidate", headline = "Backend Engineer"),
                skills = listOf(SkillImport("Kotlin", Proficiency.PROFICIENT)),
            ),
        )
        val offerId = promotion.promote(corpusId("Java Developer")).offerId

        models[LlmTask.EXTRACTION].enqueue(
            """
            {"title":"Java Developer","company":"Acme","seniority":"SENIOR","detectedLanguage":"pl",
             "requirements":[{"rawText":"Java","catalogSkill":"Java","importance":"MUST_HAVE","rationale":""}],
             "languageRequirements":[],"redFlags":[]}
            """.trimIndent()
        )
        models[LlmTask.NARRATIVE].enqueue("""{"summaryMarkdown":"Java is the gap.","learningPlan":[]}""")

        val analysisId = analyses.start(offerId, profileId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }

        val report = assertNotNull(analyses.findReport(analysisId))
        assertEquals(AnalysisState.DONE, report.state, report.error)
        assertEquals(1, report.requirements.size)
        // Extraction writes back onto the offer, promoted or pasted alike.
        assertEquals("Java Developer", assertNotNull(offers.findById(offerId)).title)
    }

    /**
     * Promotion never rewrites what the candidate found for themselves. A poll turning a
     * hand-read offer into a market row would erase the distinction the corpus exists to keep.
     */
    @Test
    fun `a listing whose text was already pasted by hand keeps its pasted origin`() {
        ingestFixture()
        val marketOfferId = corpusId("Java Developer")
        val promotedText = assertNotNull(
            offers.findById(promotion.promote(marketOfferId).offerId)
        ).rawText
        jdbc.sql("delete from job_offer").update()

        val pasted = offers.paste(promotedText)
        val promoted = promotion.promote(marketOfferId)

        assertTrue(promoted.deduplicated)
        assertEquals(pasted.offer.id, promoted.offerId)
        val offer = assertNotNull(offers.findById(promoted.offerId))
        assertEquals(OfferOrigin.PASTED, offer.origin)
        assertNull(offer.marketOfferId)
    }
}
