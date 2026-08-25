package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.BulletImport
import com.jankowski.rafal.jobassistant.profile.ExperienceImport
import com.jankowski.rafal.jobassistant.profile.LanguageImport
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.SkillImport
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole analysis pipeline against scripted models: extraction, the deterministic diff, the
 * narrative, persistence and the async state machine. Only the HTTP call to a provider is faked.
 */
@IntegrationTest
class AnalysisFlowIntegrationTest(
    @Autowired private val analyses: AnalysisService,
    @Autowired private val offers: OfferService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun setUp() {
        models.resetAll()
        jdbc.sql("delete from job_offer").update()
        listOf("work_experience", "profile_skill", "profile_link", "education", "language_skill")
            .forEach { jdbc.sql("delete from $it").update() }
        jdbc.sql("delete from profile_details").update()
        jdbc.sql("delete from unmatched_term").update()

        profiles.replace(
            ProfileImport(
                details = ProfileDetails(fullName = "Rafal Jankowski", headline = "Backend Engineer"),
                skills = listOf(
                    SkillImport("Kotlin", Proficiency.EXPERT),
                    SkillImport("Spring Boot", Proficiency.PROFICIENT),
                    SkillImport("PostgreSQL", Proficiency.WORKING),
                ),
                experiences = listOf(
                    ExperienceImport(
                        company = "Acme",
                        roleTitle = "Backend Engineer",
                        startedOn = LocalDate.of(2021, 1, 1),
                        bullets = listOf(
                            BulletImport("Built payment services in Kotlin.", listOf("Kotlin")),
                            BulletImport("Ran a Spring Boot platform.", listOf("Spring Boot")),
                        ),
                    )
                ),
                languages = listOf(
                    LanguageImport("Polish", LanguageLevel.NATIVE),
                    LanguageImport("English", LanguageLevel.C1),
                ),
            )
        )
    }

    private val offerText = """
        Senior Backend Engineer at Acme
        You have strong Kotlin and Spring Boot experience.
        You have run production workloads on Kubernetes.
        Quarkus is a plus. English B2 required.
    """.trimIndent()

    private val extraction = """
        {
          "title": "Senior Backend Engineer",
          "company": "Acme",
          "seniority": "SENIOR",
          "detectedLanguage": "en",
          "requirements": [
            {"rawText":"strong Kotlin experience","catalogSkill":"Kotlin","importance":"MUST_HAVE","rationale":"listed first"},
            {"rawText":"Spring Boot experience","catalogSkill":"Spring Boot","importance":"MUST_HAVE","rationale":""},
            {"rawText":"production workloads on Kubernetes","catalogSkill":"Kubernetes","importance":"MUST_HAVE","rationale":""},
            {"rawText":"Quarkus is a plus","catalogSkill":"Quarkus","importance":"NICE_TO_HAVE","rationale":"explicit plus"},
            {"rawText":"experience with Frobnication Engine","catalogSkill":"","importance":"NICE_TO_HAVE","rationale":""}
          ],
          "languageRequirements": [{"language":"English","level":"B2"}],
          "redFlags": ["No salary range given"]
        }
    """.trimIndent()

    private val narrative = """
        {
          "summaryMarkdown": "You cover two of three must-haves. Kubernetes is the real gap.",
          "learningPlan": [
            {"skill":"Kubernetes","why":"Every must-have list for this role includes it.",
             "practiceProject":"Deploy a two-service app to a local cluster.","effortEstimate":"2-3 weeks part time"}
          ]
        }
    """.trimIndent()

    private fun scriptHappyPath() {
        models[LlmTask.EXTRACTION].enqueue(extraction)
        models[LlmTask.NARRATIVE].enqueue(narrative)
    }

    private fun runAnalysis(text: String = offerText): AnalysisReport {
        val offer = offers.paste(text).offer
        val analysisId = analyses.start(offer.id)

        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }
        return assertNotNull(analyses.findReport(analysisId))
    }

    @Test
    fun `a completed analysis reaches DONE with a summary`() {
        scriptHappyPath()

        val report = runAnalysis()

        assertEquals(AnalysisState.DONE, report.state)
        assertNull(report.error)
        assertTrue(assertNotNull(report.summaryMarkdown).contains("Kubernetes"))
        assertNotNull(report.completedAt)
    }

    /**
     * A gap report is only meaningful next to the profile it was computed from. Without the stamp
     * the UI cannot tell a current report from one that tells you to learn something you have
     * since added.
     */
    @Test
    fun `a completed analysis records the profile revision it read`() {
        scriptHappyPath()

        val report = runAnalysis()

        assertEquals(profiles.revision(), report.profileRevision)
    }

    @Test
    fun `held and implied skills come out MET with evidence`() {
        scriptHappyPath()

        val report = runAnalysis()
        val kotlin = report.requirements.single { it.skillName == "Kotlin" }

        assertEquals(RequirementStatus.MET, kotlin.status)
        assertEquals(Importance.MUST_HAVE, kotlin.importance)
        assertTrue(assertNotNull(kotlin.evidence).contains("payment services"))
    }

    @Test
    fun `an uncovered must-have comes out MISSING`() {
        scriptHappyPath()

        val kubernetes = runAnalysis().requirements.single { it.skillName == "Kubernetes" }

        assertEquals(RequirementStatus.MISSING, kubernetes.status)
        assertNull(kubernetes.evidence)
    }

    @Test
    fun `an adjacent skill comes out PARTIAL and names what is held`() {
        scriptHappyPath()

        val quarkus = runAnalysis().requirements.single { it.skillName == "Quarkus" }

        assertEquals(RequirementStatus.PARTIAL, quarkus.status)
        assertTrue(assertNotNull(quarkus.evidence).contains("Spring Boot"))
    }

    @Test
    fun `a requirement the catalog cannot place is UNRESOLVED and queued for review`() {
        scriptHappyPath()

        val report = runAnalysis()
        val unresolved = report.requirements.single { it.status == RequirementStatus.UNRESOLVED }

        assertEquals("experience with Frobnication Engine", unresolved.rawText)
        assertNull(unresolved.skillId)
        assertTrue(
            catalog.pendingUnmatchedTerms(100).any { it.term.contains("Frobnication") },
            "unresolved requirements must reach the review queue, not vanish",
        )
    }

    @Test
    fun `the score covers must-haves only and is explained`() {
        scriptHappyPath()

        val report = runAnalysis()

        // Kotlin MET, Spring Boot MET, Kubernetes MISSING -> 2/3. Quarkus is nice-to-have.
        assertEquals(0.6667, assertNotNull(report.matchScore), 0.0001)
        assertEquals("(2 met + 0.5 x 0 partial) / 3 must-have requirements", report.scoreExplanation)
    }

    @Test
    fun `language requirements are compared by CEFR level, not by the model`() {
        scriptHappyPath()

        val english = runAnalysis().languageRequirements.single()

        assertEquals("English", english.language)
        assertEquals(LanguageLevel.B2, english.requiredLevel)
        assertEquals(LanguageLevel.C1, english.heldLevel)
        assertEquals(RequirementStatus.MET, english.status)
    }

    @Test
    fun `the learning plan is stored in priority order without invented links`() {
        scriptHappyPath()

        val plan = runAnalysis().learningPlan

        assertEquals(1, plan.size)
        assertEquals("Kubernetes", plan.single().skillName)
        assertNotNull(plan.single().skillId, "a plan item naming a catalog skill should resolve it")
        assertTrue(plan.single().practiceProject!!.isNotBlank())
    }

    @Test
    fun `extraction metadata lands on the offer`() {
        scriptHappyPath()

        val report = runAnalysis()
        val offer = assertNotNull(offers.findById(report.offerId))

        assertEquals("Senior Backend Engineer", offer.title)
        assertEquals("Acme", offer.company)
        assertEquals("en", offer.detectedLanguage)
    }

    @Test
    fun `starting an analysis moves the application to ANALYZED`() {
        scriptHappyPath()

        val report = runAnalysis()

        assertEquals(ApplicationStatus.ANALYZED, assertNotNull(offers.applicationFor(report.offerId)).status)
    }

    @Test
    fun `the narrator is shown the computed statuses so it cannot contradict them`() {
        scriptHappyPath()

        runAnalysis()

        val prompt = models[LlmTask.NARRATIVE].requests.single().messages().joinToString { it.toString() }
        assertTrue(prompt.contains("Kubernetes"))
        assertTrue(prompt.contains("MISSING"))
        assertTrue(prompt.contains("status: MET"))
    }

    @Test
    fun `the extractor is given the catalog to match against`() {
        scriptHappyPath()

        runAnalysis()

        val prompt = models[LlmTask.EXTRACTION].requests.first().messages().joinToString { it.toString() }
        assertTrue(prompt.contains("Kotlin [LANGUAGE]"))
        assertTrue(prompt.contains("Senior Backend Engineer at Acme"), "the offer text itself must be sent")
    }

    @Test
    fun `a model failure ends in FAILED with the reason recorded`() {
        models[LlmTask.EXTRACTION].enqueueFailure(RuntimeException("provider exploded"))

        val report = runAnalysis()

        assertEquals(AnalysisState.FAILED, report.state)
        assertTrue(assertNotNull(report.error).contains("provider exploded"))
    }

    @Test
    fun `analysing without a profile fails fast instead of queueing doomed work`() {
        jdbc.sql("delete from profile_details").update()
        val offer = offers.paste(offerText).offer

        assertThrows<IllegalStateException> { analyses.start(offer.id) }
    }

    @Test
    fun `analysing an unknown offer fails fast`() {
        assertThrows<NoSuchElementException> { analyses.start(999_999) }
    }

    @Test
    fun `latestForOffer returns the most recent run`() {
        scriptHappyPath()
        val report = runAnalysis()

        assertEquals(report.id, assertNotNull(analyses.latestForOffer(report.offerId)).id)
    }

    @Test
    fun `aggregate gaps count each offer once and rank must-have gaps first`() {
        scriptHappyPath()
        runAnalysis()
        scriptHappyPath()
        runAnalysis("A different posting.\nWe need Kubernetes and Kotlin.")

        val aggregate = analyses.aggregateGaps()

        assertEquals(2, aggregate.analysedOffers)
        val kubernetes = aggregate.entries.first()
        assertEquals("Kubernetes", kubernetes.skillName)
        assertEquals(2, kubernetes.demandCount)
        assertEquals(2, kubernetes.gapCount)
        assertEquals(2, kubernetes.mustHaveGapCount)
        assertEquals(1.0, kubernetes.gapRatio)

        val kotlinEntry = aggregate.entries.single { it.skillName == "Kotlin" }
        assertEquals(0, kotlinEntry.gapCount)
    }

    @Test
    fun `re-analysing the same offer does not double its vote in the aggregate`() {
        scriptHappyPath()
        val first = runAnalysis()

        scriptHappyPath()
        val second = analyses.start(first.offerId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(second)?.state?.isTerminal == true
        }

        val aggregate = analyses.aggregateGaps()
        assertEquals(1, aggregate.analysedOffers)
        assertEquals(1, aggregate.entries.single { it.skillName == "Kubernetes" }.demandCount)
    }
}
