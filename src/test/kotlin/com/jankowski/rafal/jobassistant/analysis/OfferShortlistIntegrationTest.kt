package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The cross-offer ranking: which saved offer the candidate matches best.
 *
 * Analyses are inserted directly rather than run through the pipeline. What is under test is the
 * join and the ordering - which analysis of which offer for which profile wins, and what an
 * unanalysed offer looks like - and driving two scripted model calls per row would only add ways
 * for the test to fail for reasons that are not the subject.
 */
@IntegrationTest
internal class OfferShortlistIntegrationTest(
    @Autowired private val analyses: AnalysisService,
    @Autowired private val offers: OfferService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val jdbc: JdbcClient,
) {

    private var profileId: Long = 0
    private var otherProfileId: Long = 0

    @BeforeEach
    fun setUp() {
        jdbc.sql("delete from job_offer").update()
        jdbc.sql("delete from profile").update()

        profileId = management.create("Primary").id
        otherProfileId = management.create("Second persona").id
    }

    @Test
    fun `ranks offers by the score of their latest analysis, best first`() {
        val weak = offerId("Weak match")
        val strong = offerId("Strong match")
        val middling = offerId("Middling match")

        analysis(weak, profileId, score = 0.20)
        analysis(strong, profileId, score = 0.95)
        analysis(middling, profileId, score = 0.55)

        val shortlist = analyses.shortlist(profileId)

        assertEquals(listOf(strong, middling, weak), shortlist.entries.map { it.offer.id })
        assertEquals(0.95, shortlist.entries.first().score?.matchScore)
        assertEquals(3, shortlist.scored)
        assertEquals(3, shortlist.total)
        assertEquals(profileId, shortlist.profileId)
    }

    @Test
    fun `an offer carries the latest analysis, not the best or the first`() {
        val offer = offerId("Re-analysed")

        analysis(offer, profileId, score = 0.90, createdAt = hoursAgo(3))
        analysis(offer, profileId, score = 0.10, createdAt = hoursAgo(1))

        val entry = analyses.shortlist(profileId).entries.single()

        // The profile lost a skill, or the offer was re-extracted. The shortlist reports where the
        // candidate stands now; cherry-picking the older, higher run would show a number nothing
        // currently backs.
        assertEquals(0.10, entry.score?.matchScore)
    }

    @Test
    fun `scores are scoped to the profile asked about`() {
        val offer = offerId("One offer, two personas")

        analysis(offer, profileId, score = 0.30)
        analysis(offer, otherProfileId, score = 0.80)

        assertEquals(0.30, analyses.shortlist(profileId).entries.single().score?.matchScore)
        assertEquals(0.80, analyses.shortlist(otherProfileId).entries.single().score?.matchScore)
    }

    @Test
    fun `an unanalysed offer is listed as unscored rather than as a zero`() {
        val analysed = offerId("Analysed")
        val untouched = offerId("Never analysed")

        analysis(analysed, profileId, score = 0.10)

        val shortlist = analyses.shortlist(profileId)

        assertEquals(listOf(analysed, untouched), shortlist.entries.map { it.offer.id })
        assertNull(shortlist.entries.last().score)
        // Both offers are listed; only one of them is a ranking.
        assertEquals(1, shortlist.scored)
        assertEquals(2, shortlist.total)
    }

    @Test
    fun `an unfinished or failed analysis leaves its offer unscored`() {
        val running = offerId("Still running")
        val failed = offerId("Failed")

        analysis(running, profileId, score = null, state = "NARRATING")
        analysis(failed, profileId, score = null, state = "FAILED")

        assertEquals(0, analyses.shortlist(profileId).scored)
    }

    @Test
    fun `a completed analysis that scored nothing leaves its offer unscored`() {
        val offer = offerId("Nothing scoreable")

        // DONE with a null match_score: every must-have was soft or unresolvable, so there was no
        // denominator. Reporting that as 0% would claim a measurement that was never made.
        analysis(offer, profileId, score = null)

        val entry = analyses.shortlist(profileId).entries.single()

        assertNull(entry.score)
        assertEquals(0, analyses.shortlist(profileId).scored)
    }

    @Test
    fun `equally scored offers keep the same order across repeated calls`() {
        repeat(5) { analysis(offerId("Tied offer $it"), profileId, score = 0.50) }

        val first = analyses.shortlist(profileId).entries.map { it.offer.id }
        val second = analyses.shortlist(profileId).entries.map { it.offer.id }
        val third = analyses.shortlist(profileId).entries.map { it.offer.id }

        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(first.sortedDescending(), first)
    }

    @Test
    fun `the scoring rule travels with the score`() {
        val old = offerId("Scored under V1")
        val current = offerId("Scored under V2")

        analysis(old, profileId, score = 0.60, scoringRule = ScoringRule.V1_ALL_CATEGORIES)
        analysis(current, profileId, score = 0.40, scoringRule = ScoringRule.V2_SOFT_EXCLUDED)

        val byOffer = analyses.shortlist(profileId).entries.associateBy { it.offer.id }

        assertEquals(ScoringRule.V1_ALL_CATEGORIES, byOffer.getValue(old).score?.scoringRule)
        assertEquals(ScoringRule.V2_SOFT_EXCLUDED, byOffer.getValue(current).score?.scoringRule)
    }

    @Test
    fun `falls back to the default profile when none is named`() {
        val offer = offerId("Default profile")
        analysis(offer, profileId, score = 0.70)

        val shortlist = analyses.shortlist(null)

        // `management.create` makes the first persona the default.
        assertEquals(profileId, shortlist.profileId)
        assertEquals(0.70, shortlist.entries.single().score?.matchScore)
    }

    @Test
    fun `an install with no profile lists every offer unscored rather than failing`() {
        val offer = offerId("Before any persona exists")
        jdbc.sql("delete from profile").update()

        val shortlist = analyses.shortlist(null)

        assertNull(shortlist.profileId)
        assertEquals(listOf(offer), shortlist.entries.map { it.offer.id })
        assertNull(shortlist.entries.single().score)
        assertEquals(0, shortlist.scored)
        assertEquals(1, shortlist.total)
    }

    @Test
    fun `the entry carries the application, so the list needs no second request`() {
        val offer = offerId("With its application")
        analysis(offer, profileId, score = 0.50)

        val entry = analyses.shortlist(profileId).entries.single()

        assertEquals(offer, entry.application.offerId)
        assertNotNull(entry.offer.displayTitle)
    }

    private var nextOffer = 0

    private fun offerId(title: String): Long =
        offers.paste("$title\nUnique body ${nextOffer++} for deduplication.").offer.id

    private fun hoursAgo(hours: Long): Instant =
        Instant.now().minus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS)

    private fun analysis(
        offerId: Long,
        profileId: Long,
        score: Double?,
        state: String = "DONE",
        scoringRule: ScoringRule = ScoringRule.V2_SOFT_EXCLUDED,
        createdAt: Instant = hoursAgo(0),
    ) {
        jdbc.sql(
            """
            insert into analysis (job_offer_id, profile_id, state, match_score, scoring_rule,
                                  created_at, completed_at)
            values (:offerId, :profileId, :state, :score, :rule, :createdAt, :createdAt)
            """
        )
            .param("offerId", offerId)
            .param("profileId", profileId)
            .param("state", state)
            .param("score", score?.toBigDecimal())
            .param("rule", scoringRule.name)
            // JdbcClient is raw JDBC: pgjdbc wants an explicit offset rather than an Instant.
            .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
            .update()
    }
}
