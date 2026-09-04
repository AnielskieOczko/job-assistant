package com.jankowski.rafal.jobassistant.document

import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.LlmTask
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
import com.jankowski.rafal.jobassistant.profile.internal.ProfileCollections
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
import kotlin.test.assertNull

/**
 * Reuse copies a CV onto a second offer with no model call and no regeneration (issue #82). The
 * fabrication guard still runs against the copy, because the HTML is unchanged but the profile
 * might not be.
 */
@IntegrationTest
internal class DocumentReuseIntegrationTest(
    @Autowired private val documents: DocumentService,
    @Autowired private val analyses: AnalysisService,
    @Autowired private val offers: OfferService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val writes: ProfileWriteService,
    @Autowired private val collections: ProfileCollections,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
) {

    private var kotlinBulletId = 0L
    private var firstOfferId = 0L
    private var secondOfferId = 0L
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
                details = ProfileDetails(fullName = "Rafal Jankowski", email = "rafal@example.com"),
                links = emptyList(),
                skills = listOf(
                    SkillImport("Kotlin", Proficiency.EXPERT),
                    SkillImport("PostgreSQL", Proficiency.WORKING),
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
                languages = listOf(LanguageImport("English", LanguageLevel.C1)),
            )
        )
        kotlinBulletId = profile.bullets.single().id

        firstOfferId = offers.paste("Senior Backend Engineer at Acme. Kotlin required.").offer.id
        secondOfferId = offers.paste("Backend Engineer at Beta. Kotlin required too.").offer.id

        for (offerId in listOf(firstOfferId, secondOfferId)) {
            models[LlmTask.EXTRACTION].enqueue(
                """
                {"title":"Backend Engineer","company":"Acme","seniority":"SENIOR","detectedLanguage":"en",
                 "requirements":[{"rawText":"Kotlin","catalogSkill":"Kotlin","importance":"MUST_HAVE","rationale":""}],
                 "languageRequirements":[],"redFlags":[]}
                """.trimIndent()
            )
            models[LlmTask.NARRATIVE].enqueue("""{"summaryMarkdown":"Good match.","learningPlan":[]}""")
            val analysisId = analyses.start(offerId, profileId)
            await().atMost(Duration.ofSeconds(20)).until {
                analyses.findReport(analysisId)?.state?.isTerminal == true
            }
        }
    }

    private fun scriptCv() = models[LlmTask.DOCUMENT].enqueue(
        """
        {"summaryLine":"Backend engineer with Kotlin experience.",
         "skillNames":["Kotlin","PostgreSQL"],
         "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
        """.trimIndent()
    )

    @Test
    fun `reusing a CV copies it onto the target offer with provenance`() {
        scriptCv()
        val source = documents.generate(firstOfferId, profileId, DocumentType.CV)
        val callsBefore = countLlmCalls()

        val reused = documents.reuse(secondOfferId, profileId, source.id)

        assertEquals(secondOfferId, reused.offerId)
        assertEquals(source.html, reused.html)
        assertEquals(source.id, reused.sourceDocumentId)
        assertEquals(source.droppedSkillCount, reused.droppedSkillCount)
        assertEquals(source.droppedBulletCount, reused.droppedBulletCount)
        assertEquals(source.profileRevision, reused.profileRevision)
        assertNull(source.sourceDocumentId, "the original generation is not itself a copy")
        assertEquals(callsBefore, countLlmCalls(), "reuse must never call a model")
    }

    @Test
    fun `reuse is refused once the profile drops a skill the document claims`() {
        scriptCv()
        val source = documents.generate(firstOfferId, profileId, DocumentType.CV)
        val postgresSkillId = catalog.resolve("PostgreSQL")!!.id
        val profileSkillId = profiles.require(profileId).skills.single { it.skillId == postgresSkillId }.id
        writes.delete(collections.skills, profileId, profileSkillId)
        val rowsBefore = countDocuments()

        val failure = assertThrows<FabricatedClaimException> {
            documents.reuse(secondOfferId, profileId, source.id)
        }

        assertEquals(listOf("PostgreSQL"), failure.claims)
        assertEquals(rowsBefore, countDocuments(), "a refused reuse must leave no new row")
    }

    @Test
    fun `reusing a cover letter is refused`() {
        models[LlmTask.DOCUMENT].enqueue("""{"paragraphs":["I build Kotlin services."]}""")
        val letter = documents.generate(firstOfferId, profileId, DocumentType.COVER_LETTER)

        assertThrows<IllegalArgumentException> { documents.reuse(secondOfferId, profileId, letter.id) }
    }

    @Test
    fun `the library lists documents across offers for the profile`() {
        scriptCv()
        val first = documents.generate(firstOfferId, profileId, DocumentType.CV)
        val reused = documents.reuse(secondOfferId, profileId, first.id)

        val library = documents.library(profileId)

        assertEquals(setOf(first.id, reused.id), library.map { it.document.id }.toSet())
        val reusedEntry = library.single { it.document.id == reused.id }
        assertEquals(offers.findById(secondOfferId)!!.displayTitle, reusedEntry.offerTitle)
    }

    private fun countLlmCalls(): Int = jdbc.sql("select count(*) from llm_call").query(Int::class.java).single()

    private fun countDocuments(): Int = jdbc.sql("select count(*) from generated_document").query(Int::class.java).single()
}
