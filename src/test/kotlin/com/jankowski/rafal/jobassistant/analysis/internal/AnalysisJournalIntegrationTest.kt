package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * That [AnalysisJournal.saveFindings] writes its two collections in one transaction.
 *
 * This sits below `AnalysisFlowIntegrationTest` deliberately, and it is the one thing that test
 * cannot show. Through the pipeline seam nothing can fail *between* the two writes - the only
 * failure a scripted model can produce lands before them or after them - so a run that fails during
 * narration leaves both collections present whether or not they were ever atomic. The pipeline test
 * therefore passes identically against the broken arrangement this class exists to rule out, which
 * makes it a regression test for a different property rather than evidence for this one.
 *
 * Forcing the second write to fail needs a constraint, and the schema supplies one: `UNRESOLVED` is
 * a valid `offer_requirement.status` and *not* a valid `language_requirement.status`, so a finding
 * carrying it is written happily by the first `saveAll` and rejected by the second.
 *
 * This is a test of observable behaviour - which rows exist after a failure - not of Spring's
 * proxying. It would have failed before the journal was a bean of its own, because the
 * `@Transactional` on a self-invoked method was never intercepted and the first write was already
 * committed by the time the second was attempted.
 */
@IntegrationTest
internal class AnalysisJournalIntegrationTest(
    @Autowired private val journal: AnalysisJournal,
    @Autowired private val offers: OfferService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val jdbc: JdbcClient,
) {

    private var analysisId: Long = 0

    @BeforeEach
    fun setUp() {
        jdbc.sql("delete from job_offer").update()
        jdbc.sql("delete from profile").update()

        val profileId = management.create("Test").id
        profiles.replace(
            profileId,
            ProfileImport(details = ProfileDetails(fullName = "Rafal Jankowski", headline = "Engineer")),
        )
        val offerId = offers.paste("Some offer text that nobody will analyse.").offer.id

        analysisId = jdbc.sql(
            "insert into analysis (job_offer_id, profile_id, state) values (:offerId, :profileId, 'MATCHING') returning id"
        )
            .param("offerId", offerId)
            .param("profileId", profileId)
            .query(Long::class.java)
            .single()
    }

    private fun requirementCount() =
        jdbc.sql("select count(*) from offer_requirement where analysis_id = :id")
            .param("id", analysisId)
            .query(Int::class.java)
            .single()

    private val requirement = MatchedRequirement(
        rawText = "strong Kotlin experience",
        skillId = null,
        skillName = null,
        importance = Importance.MUST_HAVE,
        status = RequirementStatus.UNRESOLVED,
        evidence = null,
        rationale = null,
    )

    @Test
    fun `both collections are written together`() {
        journal.saveFindings(
            analysisId,
            listOf(requirement),
            listOf(LanguageFinding("English", LanguageLevel.B2, LanguageLevel.C1, RequirementStatus.MET)),
        )

        assertEquals(1, requirementCount())
    }

    @Test
    fun `a rejected language finding takes the requirements down with it`() {
        assertFailsWith<Exception> {
            journal.saveFindings(
                analysisId,
                listOf(requirement),
                // UNRESOLVED violates language_requirement_status_valid, so this write fails after
                // the requirements above have already been sent.
                listOf(LanguageFinding("English", LanguageLevel.B2, null, RequirementStatus.UNRESOLVED)),
            )
        }

        assertEquals(0, requirementCount(), "the requirement write must roll back with the language write")
    }
}
