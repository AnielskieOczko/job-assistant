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
class DocumentGenerationIntegrationTest(
    @Autowired private val documents: DocumentService,
    @Autowired private val analyses: AnalysisService,
    @Autowired private val offers: OfferService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
) {

    private var kotlinBulletId = 0L
    private var springBulletId = 0L
    private var offerId = 0L

    @BeforeEach
    fun setUp() {
        models.resetAll()
        jdbc.sql("delete from job_offer").update()
        listOf("work_experience", "profile_skill", "profile_link", "education", "language_skill")
            .forEach { jdbc.sql("delete from $it").update() }
        jdbc.sql("delete from profile_details").update()

        val profile = profiles.replace(
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

        val analysisId = analyses.start(offerId)
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

        val document = documents.generate(offerId, DocumentType.CV)

        assertEquals(DocumentType.CV, document.type)
        assertTrue(document.html.contains("Rafal Jankowski"))
        assertTrue(document.html.contains("Built payment services in Kotlin."))
        assertTrue(document.html.contains("Kotlin"))
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

        val html = documents.generate(offerId, DocumentType.CV).html

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

        val html = documents.generate(offerId, DocumentType.CV).html

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

        val failure = assertThrows<FabricatedClaimException> { documents.generate(offerId, DocumentType.CV) }

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

        assertThrows<FabricatedClaimException> { documents.generate(offerId, DocumentType.CV) }
    }

    @Test
    fun `a refused document is not persisted`() {
        scriptCv(
            """
            {"summaryLine":"Kubernetes expert.","skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        runCatching { documents.generate(offerId, DocumentType.CV) }

        assertEquals(null, documents.latest(offerId, DocumentType.CV))
    }

    @Test
    fun `a skill the profile does not hold is dropped rather than listed`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin","Kubernetes","Apache Kafka"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        val html = documents.generate(offerId, DocumentType.CV).html

        assertTrue(html.contains("Kotlin"))
        assertFalse(html.contains("Kubernetes"), "an unheld skill must never reach the skills list")
        assertFalse(html.contains("Kafka"))
    }

    @Test
    fun `a bullet id that does not exist is ignored`() {
        scriptCv(
            """
            {"summaryLine":"Backend engineer.","skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":""},{"bulletId":987654,"text":"Invented achievement."}]}
            """.trimIndent()
        )

        val html = documents.generate(offerId, DocumentType.CV).html

        assertTrue(html.contains("Built payment services in Kotlin."))
        assertFalse(html.contains("Invented achievement."))
    }

    @Test
    fun `an empty selection falls back to the full profile rather than an empty CV`() {
        scriptCv("""{"summaryLine":"Backend engineer.","skillNames":[],"bullets":[]}""")

        val html = documents.generate(offerId, DocumentType.CV).html

        assertTrue(html.contains("Built payment services in Kotlin."))
        assertTrue(html.contains("Ran a Spring Boot platform."))
    }

    @Test
    fun `the tailoring prompt exposes bullet ids so choices can be traced`() {
        scriptCv(honestCv)

        documents.generate(offerId, DocumentType.CV)

        val prompt = models[LlmTask.DOCUMENT].requests.single().messages().joinToString { it.toString() }
        assertTrue(prompt.contains("[id=$kotlinBulletId]"))
        assertTrue(prompt.contains("Kubernetes [MISSING]"), "the tailor should see what the offer wants")
    }

    @Test
    fun `a cover letter renders its paragraphs`() {
        models[LlmTask.DOCUMENT].enqueue(
            """
            {"paragraphs":["I have spent six years building Kotlin services.",
                           "I have run Spring Boot in production throughout that time."]}
            """.trimIndent()
        )

        val document = documents.generate(offerId, DocumentType.COVER_LETTER)

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

        assertThrows<FabricatedClaimException> { documents.generate(offerId, DocumentType.COVER_LETTER) }
    }

    @Test
    fun `a cover letter claiming an absent skill is refused`() {
        models[LlmTask.DOCUMENT].enqueue(
            """{"paragraphs":["I have run Kubernetes clusters for five years."]}"""
        )

        assertThrows<FabricatedClaimException> { documents.generate(offerId, DocumentType.COVER_LETTER) }
    }

    @Test
    fun `generating without an analysis is refused`() {
        val fresh = offers.paste("A different posting entirely.").offer

        assertThrows<IllegalStateException> { documents.generate(fresh.id, DocumentType.CV) }
    }

    @Test
    fun `generating for an unknown offer is refused`() {
        assertThrows<NoSuchElementException> { documents.generate(999_999, DocumentType.CV) }
    }

    @Test
    fun `the latest document is retrievable and keeps its html`() {
        scriptCv(honestCv)
        val generated = documents.generate(offerId, DocumentType.CV)

        val latest = assertNotNull(documents.latest(offerId, DocumentType.CV))

        assertEquals(generated.id, latest.id)
        assertEquals(generated.html, latest.html)
    }
}
