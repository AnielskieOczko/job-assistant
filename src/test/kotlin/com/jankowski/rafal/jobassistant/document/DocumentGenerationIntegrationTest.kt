package com.jankowski.rafal.jobassistant.document

import com.jankowski.rafal.jobassistant.analysis.AnalysisService
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
import com.jankowski.rafal.jobassistant.profile.SkillImport
import com.jankowski.rafal.jobassistant.profile.internal.ConsentClauseRequest
import com.jankowski.rafal.jobassistant.profile.internal.DetailsRequest
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.profile.internal.ProfileWriteService
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CV and cover letter generation against scripted models, including the case that matters most:
 * a model that tries to put a technology on the CV that the candidate has never used.
 */
@IntegrationTest
internal class DocumentGenerationIntegrationTest(
    @Autowired private val documents: DocumentService,
    @Autowired private val analyses: AnalysisService,
    @Autowired private val offers: OfferService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val writes: ProfileWriteService,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
) {

    private var kotlinBulletId = 0L
    private var springBulletId = 0L
    private var offerId = 0L
    private var profileId = 0L

    @BeforeEach
    fun setUp() {
        models.resetAll()
        jdbc.sql("delete from job_offer").update()
        jdbc.sql("delete from profile").update()

        profileId = management.create("Test").id
        val profile = profiles.replace(
            profileId,
            ProfileImport(
                details = ProfileDetails(
                    fullName = "Rafal Jankowski",
                    headline = "Backend Engineer",
                    email = "rafal@example.com",
                    location = "Poland",
                    summary = "Backend engineer on the JVM.",
                ),
                links = listOf(LinkImport("GitHub", "https://github.com/example")),
                skills = listOf(
                    SkillImport("Kotlin", Proficiency.EXPERT, yearsOfExperience = "6".toBigDecimal()),
                    SkillImport("Spring Boot", Proficiency.PROFICIENT),
                    SkillImport("PostgreSQL", Proficiency.WORKING),
                ),
                experiences = listOf(
                    ExperienceImport(
                        company = "Acme",
                        roleTitle = "Senior Backend Engineer",
                        startedOn = LocalDate.of(2021, 1, 1),
                        bullets = listOf(
                            BulletImport("Built payment services in Kotlin.", listOf("Kotlin")),
                            BulletImport("Ran a Spring Boot platform.", listOf("Spring Boot")),
                        ),
                    )
                ),
                education = emptyList(),
                languages = listOf(LanguageImport("English", LanguageLevel.C1)),
            )
        )
        kotlinBulletId = profile.bullets.first { it.text.startsWith("Built payment") }.id
        springBulletId = profile.bullets.first { it.text.startsWith("Ran a Spring") }.id

        offerId = offers.paste("Senior Backend Engineer at Acme. Kotlin and Kubernetes required.").offer.id

        models[LlmTask.EXTRACTION].enqueue(
            """
            {"title":"Senior Backend Engineer","company":"Acme","seniority":"SENIOR","detectedLanguage":"en",
             "requirements":[
               {"rawText":"Kotlin","catalogSkill":"Kotlin","importance":"MUST_HAVE","rationale":""},
               {"rawText":"Kubernetes","catalogSkill":"Kubernetes","importance":"MUST_HAVE","rationale":""}],
             "languageRequirements":[],"redFlags":[]}
            """.trimIndent()
        )
        models[LlmTask.NARRATIVE].enqueue(
            """{"summaryMarkdown":"Kubernetes is the gap.","learningPlan":[]}"""
        )

        val analysisId = analyses.start(offerId, profileId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }
    }

    private fun scriptCv(json: String) = models[LlmTask.DOCUMENT].enqueue(json)

    private val honestCv
        get() = """
            {"summaryLine":"Backend engineer with six years on Kotlin services.",
             "skillNames":["Kotlin","Spring Boot","PostgreSQL"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."},
                        {"bulletId":$springBulletId,"text":"Ran a Spring Boot platform."}]}
        """.trimIndent()

    @Test
    fun `a tailored CV renders the selected bullets and skills`() {
        scriptCv(honestCv)

        val document = documents.generate(offerId, profileId, DocumentType.CV)

        assertEquals(DocumentType.CV, document.type)
        assertTrue(document.html.contains("Rafal Jankowski"))
        assertTrue(document.html.contains("Built payment services in Kotlin."))
        assertTrue(document.html.contains("Kotlin"))
        // The stored HTML stays true, but the profile can move on underneath it.
        assertEquals(profiles.revision(profileId), document.profileRevision)
    }

    @Test
    fun `the model's bullet order is respected`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.",
             "skillNames":["Spring Boot","Kotlin"],
             "bullets":[{"bulletId":$springBulletId,"text":""},{"bulletId":$kotlinBulletId,"text":""}]}
            """.trimIndent()
        )

        val html = documents.generate(offerId, profileId, DocumentType.CV).html

        assertTrue(html.indexOf("Spring Boot platform") < html.indexOf("payment services"))
    }

    @Test
    fun `a rewritten bullet replaces the original wording`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Shipped payment services in Kotlin, end to end."}]}
            """.trimIndent()
        )

        val html = documents.generate(offerId, profileId, DocumentType.CV).html

        assertTrue(html.contains("Shipped payment services in Kotlin, end to end."))
        assertFalse(html.contains("Built payment services in Kotlin."))
    }

    @Test
    fun `a CV claiming a skill the profile lacks is refused`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer experienced with Kubernetes at scale.",
             "skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        val failure = assertThrows<FabricatedClaimException> { documents.generate(offerId, profileId, DocumentType.CV) }

        assertEquals(listOf("Kubernetes"), failure.claims)
    }

    @Test
    fun `a fabricated claim inside a rewritten bullet is refused too`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Deployed Kotlin services onto Kubernetes."}]}
            """.trimIndent()
        )

        assertThrows<FabricatedClaimException> { documents.generate(offerId, profileId, DocumentType.CV) }
    }

    /**
     * Asserted against the table as well as through [DocumentService.latest], because `generate` is
     * no longer `@Transactional` and this is the property that had to survive removing it. Nothing
     * rolls a refusal back now: what keeps the row from existing is that `enforceNoFabrication` runs
     * before the save, and a count over the whole table is what shows it — `latest` filters by
     * profile and type, so it would answer null for a row written under either.
     */
    @Test
    fun `a refused document is not persisted`() {
        scriptCv(
            """
            {"summaryLine":"Kubernetes expert.","skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        runCatching { documents.generate(offerId, profileId, DocumentType.CV) }

        assertEquals(null, documents.latest(offerId, DocumentType.CV))
        assertEquals(
            0,
            jdbc.sql("select count(*) from generated_document").query(Int::class.java).single(),
            "a refused generation must leave no row at all, drop counters included",
        )
    }

    @Test
    fun `a skill the profile does not hold is dropped rather than listed`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin","Kubernetes","Apache Kafka"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        val document = documents.generate(offerId, profileId, DocumentType.CV)

        assertTrue(document.html.contains("Kotlin"))
        assertFalse(document.html.contains("Kubernetes"), "an unheld skill must never reach the skills list")
        assertFalse(document.html.contains("Kafka"))
        assertEquals(2, document.droppedSkillCount, "both unheld skills should be counted, not just filtered")
        assertEquals(0, document.droppedBulletCount)
    }

    @Test
    fun `a bullet id that does not exist is ignored`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":""},{"bulletId":987654,"text":"Invented achievement."}]}
            """.trimIndent()
        )

        val document = documents.generate(offerId, profileId, DocumentType.CV)

        assertTrue(document.html.contains("Built payment services in Kotlin."))
        assertFalse(document.html.contains("Invented achievement."))
        assertEquals(1, document.droppedBulletCount, "the invented bullet id should be counted, not just ignored")
        assertEquals(0, document.droppedSkillCount)
    }

    /**
     * The counts are a fabrication *rate*, so a clean generation has to report zero - otherwise a
     * rising number could not be told apart from a rising number of documents.
     */
    @Test
    fun `an honest CV reports nothing discarded`() {
        scriptCv(honestCv)

        val document = documents.generate(offerId, profileId, DocumentType.CV)

        assertEquals(0, document.droppedBulletCount)
        assertEquals(0, document.droppedSkillCount)
    }

    @Test
    fun `the discard counts survive being read back`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin","Kubernetes"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":""},{"bulletId":987654,"text":"Invented."}]}
            """.trimIndent()
        )

        val generated = documents.generate(offerId, profileId, DocumentType.CV)
        val reloaded = documents.findById(generated.id)

        assertEquals(1, reloaded?.droppedBulletCount)
        assertEquals(1, reloaded?.droppedSkillCount)
    }

    @Test
    fun `an empty selection falls back to the full profile rather than an empty CV`() {
        scriptCv("""{"summaryLine":"Backend engineer.","skillNames":[],"bullets":[]}""")

        val html = documents.generate(offerId, profileId, DocumentType.CV).html

        assertTrue(html.contains("Built payment services in Kotlin."))
        assertTrue(html.contains("Ran a Spring Boot platform."))
    }

    @Test
    fun `the tailoring prompt exposes bullet ids so choices can be traced`() {
        scriptCv(honestCv)

        documents.generate(offerId, profileId, DocumentType.CV)

        val prompt = models[LlmTask.DOCUMENT].requests.single().messages().joinToString { it.toString() }
        assertTrue(prompt.contains("[id=$kotlinBulletId]"))
        assertTrue(prompt.contains("Kubernetes [MISSING]"), "the tailor should see what the offer wants")
    }

    @Test
    fun `the tailoring prompt includes the candidate's stated career goal`() {
        writes.putDetails(
            profileId,
            DetailsRequest(
                fullName = "Rafal Jankowski",
                headline = "Backend Engineer",
                careerGoal = "I'm moving from support engineering into backend development.",
            ),
        )
        scriptCv(honestCv)

        documents.generate(offerId, profileId, DocumentType.CV)

        val prompt = models[LlmTask.DOCUMENT].requests.single().messages().joinToString { it.toString() }
        assertTrue(prompt.contains("I'm moving from support engineering into backend development."))
    }

    /**
     * The invariant has no notion of aspiration any more than it has of negation: naming a
     * technology that only appears in the stated goal still fails the document, exactly as if the
     * model had invented it from nowhere.
     */
    @Test
    fun `a cover letter naming a technology only mentioned in the stated career goal is refused`() {
        writes.putDetails(
            profileId,
            DetailsRequest(fullName = "Rafal Jankowski", careerGoal = "I want to grow into Kubernetes-based platform work."),
        )
        models[LlmTask.DOCUMENT].enqueue(
            """{"paragraphs":["I'm working toward Kubernetes-based platform roles."]}"""
        )

        assertThrows<FabricatedClaimException> { documents.generate(offerId, profileId, DocumentType.COVER_LETTER) }
    }

    /** A letter selects nothing by id, so it has no drop count of its own to report. */
    @Test
    fun `a cover letter reports no discards`() {
        models[LlmTask.DOCUMENT].enqueue(
            """{"paragraphs":["I build Kotlin services.","I would like to help."]}"""
        )

        val document = documents.generate(offerId, profileId, DocumentType.COVER_LETTER)

        assertEquals(0, document.droppedBulletCount)
        assertEquals(0, document.droppedSkillCount)
    }

    @Test
    fun `a cover letter renders its paragraphs`() {
        models[LlmTask.DOCUMENT].enqueue(
            """
            {"paragraphs":["I have spent six years building Kotlin services.",
                           "I have run Spring Boot in production throughout that time."]}
            """.trimIndent()
        )

        val document = documents.generate(offerId, profileId, DocumentType.COVER_LETTER)

        assertEquals(DocumentType.COVER_LETTER, document.type)
        assertTrue(document.html.contains("six years building Kotlin services"))
        assertTrue(document.html.contains("Re: Senior Backend Engineer at Acme"))
    }

    @Test
    fun `a cover letter naming an absent skill is refused even when the mention is honest`() {
        // The invariant is absolute: naming a technology the profile lacks fails the letter, even
        // in a truthful negative sentence. The prompt is written to match.
        models[LlmTask.DOCUMENT].enqueue(
            """{"paragraphs":["I have not used Kubernetes, but I learn quickly."]}"""
        )

        assertThrows<FabricatedClaimException> { documents.generate(offerId, profileId, DocumentType.COVER_LETTER) }
    }

    @Test
    fun `a cover letter claiming an absent skill is refused`() {
        models[LlmTask.DOCUMENT].enqueue(
            """{"paragraphs":["I have run Kubernetes clusters for five years."]}"""
        )

        assertThrows<FabricatedClaimException> { documents.generate(offerId, profileId, DocumentType.COVER_LETTER) }
    }

    @Test
    fun `generating without an analysis is refused`() {
        val fresh = offers.paste("A different posting entirely.").offer

        assertThrows<IllegalStateException> { documents.generate(fresh.id, profileId, DocumentType.CV) }
    }

    @Test
    fun `generating for an unknown offer is refused`() {
        assertThrows<NoSuchElementException> { documents.generate(999_999, profileId, DocumentType.CV) }
    }

    // ------------------------------------------------------- consent clauses

    @Test
    fun `a CV renders the matching consent clause with the company substituted`() {
        writes.addConsentClause(
            profileId,
            ConsentClauseRequest(language = "English", text = "I consent to processing of my data by {{company}}."),
        )
        scriptCv(honestCv)

        val document = documents.generate(offerId, profileId, DocumentType.CV, "English")

        assertTrue(document.html.contains("I consent to processing of my data by Acme."))
        assertEquals("English", document.consentClauseLanguage)
    }

    @Test
    fun `a CV omits the consent clause and records null when no clause matches the language`() {
        writes.addConsentClause(profileId, ConsentClauseRequest(language = "Polish", text = "Zgoda."))
        scriptCv(honestCv)

        val document = documents.generate(offerId, profileId, DocumentType.CV, "English")

        assertFalse(document.html.contains("Zgoda."))
        assertEquals(null, document.consentClauseLanguage)
    }

    @Test
    fun `a cover letter never carries a consent clause`() {
        writes.addConsentClause(profileId, ConsentClauseRequest(language = "English", text = "I consent."))
        models[LlmTask.DOCUMENT].enqueue("""{"paragraphs":["I build Kotlin services."]}""")

        val document = documents.generate(offerId, profileId, DocumentType.COVER_LETTER, "English")

        assertFalse(document.html.contains("I consent."))
        assertEquals(null, document.consentClauseLanguage)
    }

    /**
     * The clause is user-authored free text, so `CvInvariant` still scans it exactly as it scans a
     * rewritten bullet - excluding it would leave a way for the rendered page to name a technology
     * the profile does not hold. See issue #52's first trap.
     */
    @Test
    fun `a consent clause naming a skill the profile lacks fails the generation`() {
        writes.addConsentClause(
            profileId,
            ConsentClauseRequest(language = "English", text = "Processed using our Kubernetes-based systems."),
        )
        scriptCv(honestCv)

        val failure = assertThrows<FabricatedClaimException> {
            documents.generate(offerId, profileId, DocumentType.CV, "English")
        }

        assertEquals(listOf("Kubernetes"), failure.claims)
    }

    @Test
    fun `an offer with no company name leaves the placeholder unsubstituted rather than inventing one`() {
        writes.addConsentClause(
            profileId,
            ConsentClauseRequest(language = "English", text = "I consent to processing by {{company}}."),
        )
        val blankCompanyOfferId = offers.paste("A role with an unresolved employer name. Kotlin required.").offer.id
        models[LlmTask.EXTRACTION].enqueue(
            """
            {"title":"Backend Engineer","company":"","seniority":"MID","detectedLanguage":"en",
             "requirements":[{"rawText":"Kotlin","catalogSkill":"Kotlin","importance":"MUST_HAVE","rationale":""}],
             "languageRequirements":[],"redFlags":[]}
            """.trimIndent()
        )
        models[LlmTask.NARRATIVE].enqueue("""{"summaryMarkdown":"Strong match.","learningPlan":[]}""")
        val analysisId = analyses.start(blankCompanyOfferId, profileId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }
        scriptCv(honestCv)

        val document = documents.generate(blankCompanyOfferId, profileId, DocumentType.CV, "English")

        assertTrue(document.html.contains("I consent to processing by {{company}}."))
        assertEquals("English", document.consentClauseLanguage)
    }

    @Test
    fun `the latest document is retrievable and keeps its html`() {
        scriptCv(honestCv)
        val generated = documents.generate(offerId, profileId, DocumentType.CV)

        val latest = assertNotNull(documents.latest(offerId, DocumentType.CV))

        assertEquals(generated.id, latest.id)
        assertEquals(generated.html, latest.html)
    }
}
