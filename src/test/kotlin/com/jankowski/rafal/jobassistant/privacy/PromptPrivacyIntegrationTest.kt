package com.jankowski.rafal.jobassistant.privacy

import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import com.jankowski.rafal.jobassistant.document.DocumentService
import com.jankowski.rafal.jobassistant.document.DocumentType
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.BulletImport
import com.jankowski.rafal.jobassistant.profile.ExperienceImport
import com.jankowski.rafal.jobassistant.profile.LanguageImport
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.LinkImport
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProjectImport
import com.jankowski.rafal.jobassistant.profile.SkillImport
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import com.jankowski.rafal.jobassistant.triage.TriageRanking
import com.jankowski.rafal.jobassistant.triage.internal.ModelTriageSuggestionService
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The end-to-end guarantee: run every model-calling flow there is and assert that no direct
 * identifier appears in anything that was handed to a model.
 *
 * Sentinel values are used rather than realistic ones so a hit cannot be a coincidence, and so the
 * assertion failure names exactly which field escaped.
 */
@IntegrationTest
internal class PromptPrivacyIntegrationTest(
    @Autowired private val documents: DocumentService,
    @Autowired private val analyses: AnalysisService,
    @Autowired private val offers: OfferService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val triageSuggestions: ModelTriageSuggestionService,
) {

    private var kotlinBulletId = 0L
    private var offerId = 0L
    private var profileId = 0L

    @BeforeEach
    fun setUp() {
        models.resetAll()
        jdbc.sql("delete from job_offer").update()
        jdbc.sql("delete from profile").update()
        jdbc.sql("delete from llm_call").update()

        profileId = management.create("Sentinel").id
        val profile = profiles.replace(
            profileId,
            ProfileImport(
                details = ProfileDetails(
                    fullName = SENTINEL_NAME,
                    headline = "Backend Engineer",
                    email = SENTINEL_EMAIL,
                    phone = SENTINEL_PHONE,
                    location = "Poland",
                    summary = "Backend engineer on the JVM.",
                ),
                links = listOf(LinkImport("GitHub", SENTINEL_LINK)),
                skills = listOf(
                    SkillImport("Kotlin", Proficiency.EXPERT, yearsOfExperience = "6".toBigDecimal()),
                    SkillImport("Spring Boot", Proficiency.PROFICIENT),
                ),
                experiences = listOf(
                    ExperienceImport(
                        company = "Acme",
                        roleTitle = "Senior Backend Engineer",
                        startedOn = LocalDate.of(2021, 1, 1),
                        bullets = listOf(BulletImport("Built payment services in Kotlin.", listOf("Kotlin"))),
                    )
                ),
                education = emptyList(),
                projects = listOf(
                    ProjectImport(
                        name = "Side project",
                        url = SENTINEL_PROJECT_URL,
                        skills = listOf("Kotlin"),
                        bullets = listOf(BulletImport("Built a CLI in Kotlin.", listOf("Kotlin"))),
                    )
                ),
                languages = listOf(LanguageImport("English", LanguageLevel.C1)),
            )
        )
        kotlinBulletId = profile.bullets.first().id

        // The offer carries a recruiter's contact details, as real ones routinely do.
        offerId = offers.paste(
            """
            Senior Backend Engineer at Acme. Kotlin and Spring Boot required.
            Questions? Contact anna.kowalska@recruiter.example.com or call +48 555 123 456.
            """.trimIndent()
        ).offer.id
    }

    private fun runAnalysis(): Long {
        models[LlmTask.EXTRACTION].enqueue(
            """
            {"title":"Senior Backend Engineer","company":"Acme","seniority":"SENIOR","detectedLanguage":"en",
             "requirements":[{"rawText":"Kotlin","catalogSkill":"Kotlin","importance":"MUST_HAVE","rationale":""}],
             "languageRequirements":[],"redFlags":[]}
            """.trimIndent()
        )
        models[LlmTask.NARRATIVE].enqueue("""{"summaryMarkdown":"Strong match.","learningPlan":[]}""")

        val analysisId = analyses.start(offerId, profileId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }
        return analysisId
    }

    private fun everythingSentToModels(): String =
        LlmTask.entries
            .flatMap { models[it].requests }
            .flatMap { it.messages() }
            .joinToString("\n") { message ->
                when (message) {
                    is SystemMessage -> message.text()
                    is UserMessage -> message.singleText()
                    else -> message.toString()
                }
            }

    private fun assertNoIdentifiersIn(sent: String) {
        assertFalse(sent.contains(SENTINEL_NAME, ignoreCase = true), "the candidate's name reached a model")
        assertFalse(sent.contains(SENTINEL_EMAIL, ignoreCase = true), "the candidate's email reached a model")
        assertFalse(sent.contains("555987654"), "the candidate's phone reached a model")
        assertFalse(sent.contains("QQVHANDLE", ignoreCase = true), "a profile link reached a model")
        assertFalse(sent.contains("ZZQXPROJECT", ignoreCase = true), "a project url reached a model")
    }

    /**
     * Triage carries no profile data at all - the terms come from public job boards and the catalog
     * is a public taxonomy - so this test is not expected to catch anything. It is here because the
     * value of this suite is *enumerating every model-calling flow*: a flow nobody added an
     * assertion for is a flow nobody checked, and the one that eventually interpolates a profile
     * field will look exactly like this one did.
     */
    @Test
    fun `no direct identifier reaches a model during triage suggestion`() {
        jdbc.sql(
            """
            insert into unmatched_term (term, normalized_term, occurrences, market_occurrences)
            values ('Triage Privacy Probe', 'triageprivacyprobe', 5, 0)
            """
        ).update()
        models[LlmTask.TRIAGE].enqueue(
            """{"suggestions":[{"term":"Triage Privacy Probe","catalogSkill":"Kotlin","rationale":"x"}]}"""
        )

        triageSuggestions.suggestFor(1, TriageRanking.CORPUS, 25)

        val sent = everythingSentToModels()
        assertTrue(sent.contains("Triage Privacy Probe"), "the flow did not actually run")
        assertNoIdentifiersIn(sent)
    }

    @Test
    fun `no direct identifier reaches a model during analysis`() {
        runAnalysis()

        val sent = everythingSentToModels()
        assertTrue(sent.contains("Kotlin"), "the flow did not actually run")
        assertNoIdentifiersIn(sent)
    }

    @Test
    fun `no direct identifier reaches a model while generating both documents`() {
        runAnalysis()

        models[LlmTask.DOCUMENT].enqueue(
            """
            {"summaryLine":"Backend engineer with six years on Kotlin services.",
             "skillNames":["Kotlin","Spring Boot"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent(),
            """{"paragraphs":["I have run Kotlin services in production for six years."]}""",
        )

        documents.generate(offerId, profileId, DocumentType.CV)
        documents.generate(offerId, profileId, DocumentType.COVER_LETTER)

        val sent = everythingSentToModels()
        assertTrue(sent.contains("Built payment services in Kotlin."), "the briefing was not actually sent")
        assertNoIdentifiersIn(sent)
    }

    @Test
    fun `the rendered CV still carries the name and contacts the model never saw`() {
        runAnalysis()
        models[LlmTask.DOCUMENT].enqueue(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        val document = documents.generate(offerId, profileId, DocumentType.CV)

        // The renderer supplies these from the database after the model has answered, which is why
        // withholding them from the prompt costs nothing.
        assertTrue(document.html.contains(SENTINEL_NAME), "the CV lost its name")
        assertTrue(document.html.contains(SENTINEL_EMAIL), "the CV lost its contact details")
        assertNoIdentifiersIn(everythingSentToModels())
    }

    @Test
    fun `recruiter contact details are scrubbed from the offer before extraction`() {
        runAnalysis()

        val sent = everythingSentToModels()
        assertFalse(sent.contains("anna.kowalska"), "a recruiter's email reached a model")
        assertFalse(sent.contains("555 123 456"), "a recruiter's phone reached a model")
        assertTrue(sent.contains("Senior Backend Engineer at Acme"), "the offer text was mangled")
    }

    @Test
    fun `a prompt carrying an identifier is refused rather than sent`() {
        // Proves the guard itself works, rather than only that today's builders happen to be clean.
        // The offer text is the one part of a prompt a user controls directly.
        val pollutedOfferId = offers.paste("Role at Acme. Ask for $SENTINEL_NAME. Kotlin required.").offer.id
        models[LlmTask.EXTRACTION].enqueue("""{"title":"x","company":"y","requirements":[]}""")

        val analysisId = analyses.start(pollutedOfferId, profileId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }

        val report = analyses.findReport(analysisId)!!
        assertTrue(report.state.name == "FAILED", "the analysis should have been refused")
        assertTrue(report.error!!.contains("fullName"), "the failure should name the offending field")
        assertFalse(report.error!!.contains(SENTINEL_NAME), "the error must not echo the value itself")
        assertNoIdentifiersIn(everythingSentToModels())
    }

    /**
     * `careerGoal` is free text, so it can carry the candidate's own name exactly as an offer can -
     * "Zzqxname Qqvsurname is looking to move into platform engineering" trips the same guard that
     * `a prompt carrying an identifier is refused rather than sent` proves for offer text. This is
     * expected behaviour, not a bug to route around: the fix belongs at the input, not here.
     */
    @Test
    fun `a career goal carrying the candidate's own name is refused rather than sent`() {
        profiles.replace(
            profileId,
            ProfileImport(
                details = ProfileDetails(
                    fullName = SENTINEL_NAME,
                    headline = "Backend Engineer",
                    careerGoal = "$SENTINEL_NAME is looking to move into platform engineering.",
                ),
                skills = listOf(SkillImport("Kotlin", Proficiency.EXPERT)),
            ),
        )
        models[LlmTask.EXTRACTION].enqueue(
            """{"title":"x","company":"y","requirements":[{"rawText":"Kotlin","catalogSkill":"Kotlin","importance":"MUST_HAVE","rationale":""}]}"""
        )

        val analysisId = analyses.start(offerId, profileId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }

        val report = analyses.findReport(analysisId)!!
        assertTrue(report.state.name == "FAILED", "the analysis should have been refused")
        assertTrue(report.error!!.contains("fullName"), "the failure should name the offending field")
        assertFalse(report.error!!.contains(SENTINEL_NAME), "the error must not echo the value itself")
    }

    @Test
    fun `the audit log never records an identifier`() {
        runAnalysis()

        val audited = jdbc.sql("select request_json from llm_call")
            .query(String::class.java)
            .list()
            .joinToString("\n")

        assertTrue(audited.isNotEmpty(), "nothing was audited, so this proves nothing")
        assertFalse(audited.contains(SENTINEL_NAME, ignoreCase = true))
        assertFalse(audited.contains(SENTINEL_EMAIL, ignoreCase = true))
    }

    private companion object {
        const val SENTINEL_NAME = "Zzqxname Qqvsurname"
        const val SENTINEL_EMAIL = "zzqxsentinel@example.invalid"
        const val SENTINEL_PHONE = "+48 555 987 654"
        const val SENTINEL_LINK = "https://github.com/QQVHANDLE"
        const val SENTINEL_PROJECT_URL = "https://github.com/ZZQXPROJECT/side-project"
    }
}
