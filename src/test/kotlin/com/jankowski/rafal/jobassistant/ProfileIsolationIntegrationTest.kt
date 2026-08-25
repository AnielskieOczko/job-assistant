package com.jankowski.rafal.jobassistant

import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.document.DocumentService
import com.jankowski.rafal.jobassistant.document.DocumentType
import com.jankowski.rafal.jobassistant.document.FabricatedClaimException
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.BulletImport
import com.jankowski.rafal.jobassistant.profile.ExperienceImport
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.SkillImport
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
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
 * The test the roadmap's "risk that matters" callout demands: two profiles with disjoint skills,
 * proving neither the analysis path nor the document path ever unions their held skills.
 *
 * A union here would let a CV for the consultant persona claim Kubernetes because the developer
 * persona holds it - the exact fabrication the whole app exists to prevent, arriving through the
 * new door multiple profiles opens. This spans `profile`, `analysis` and `document`, so it lives
 * at the base package rather than inside any one module, matching `ApiContractTest` and
 * `ModularityTest`.
 */
@IntegrationTest
internal class ProfileIsolationIntegrationTest(
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val analyses: AnalysisService,
    @Autowired private val documents: DocumentService,
    @Autowired private val offers: OfferService,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
) {

    private var profileA: Long = 0
    private var profileB: Long = 0
    private var offerId: Long = 0

    private var kotlinBulletId = 0L
    private var kubernetesBulletId = 0L

    @BeforeEach
    fun setUp() {
        models.resetAll()
        jdbc.sql("delete from job_offer").update()
        jdbc.sql("delete from profile").update()
        jdbc.sql("delete from unmatched_term").update()

        // Profile A: "Java developer" - Kotlin and Spring Boot only.
        profileA = management.create("Java developer").id
        val savedA = profiles.replace(
            profileA,
            ProfileImport(
                // Deliberately not "Java Dev" - "Java" is itself a catalog skill this profile does
                // not hold, and CvInvariant scans the whole rendered page, including the header.
                details = ProfileDetails(fullName = "Alex Morgan"),
                skills = listOf(
                    SkillImport("Kotlin", Proficiency.EXPERT),
                    SkillImport("Spring Boot", Proficiency.PROFICIENT),
                ),
                experiences = listOf(
                    ExperienceImport(
                        company = "Acme",
                        roleTitle = "Backend Engineer",
                        startedOn = LocalDate.of(2021, 1, 1),
                        bullets = listOf(BulletImport("Built payment services in Kotlin.", listOf("Kotlin"))),
                    )
                ),
            ),
        )
        kotlinBulletId = savedA.bullets.single().id

        // Profile B: "Cloud consultant" - Kubernetes and Terraform only. Disjoint from A: neither
        // skill set implies or relates to the other's (checked against V2__catalog_seed.sql).
        profileB = management.create("Cloud consultant").id
        val savedB = profiles.replace(
            profileB,
            ProfileImport(
                details = ProfileDetails(fullName = "Sam Rivera"),
                skills = listOf(
                    SkillImport("Kubernetes", Proficiency.EXPERT),
                    SkillImport("Terraform", Proficiency.PROFICIENT),
                ),
                experiences = listOf(
                    ExperienceImport(
                        company = "Initech",
                        roleTitle = "Platform Engineer",
                        startedOn = LocalDate.of(2021, 1, 1),
                        bullets = listOf(BulletImport("Ran production clusters on Kubernetes.", listOf("Kubernetes"))),
                    )
                ),
            ),
        )
        kubernetesBulletId = savedB.bullets.single().id

        offerId = offers.paste("Platform role. Needs Kotlin and Kubernetes.").offer.id
    }

    private val extraction = """
        {
          "title": "Platform Engineer",
          "company": "Example",
          "seniority": "SENIOR",
          "detectedLanguage": "en",
          "requirements": [
            {"rawText":"Kotlin","catalogSkill":"Kotlin","importance":"MUST_HAVE","rationale":""},
            {"rawText":"Kubernetes","catalogSkill":"Kubernetes","importance":"MUST_HAVE","rationale":""}
          ],
          "languageRequirements": [],
          "redFlags": []
        }
    """.trimIndent()

    private val narrative = """{"summaryMarkdown":"See requirements.","learningPlan":[]}"""

    private fun runAnalysis(profileId: Long): AnalysisReport {
        models[LlmTask.EXTRACTION].enqueue(extraction)
        models[LlmTask.NARRATIVE].enqueue(narrative)
        val analysisId = analyses.start(offerId, profileId)
        await().atMost(Duration.ofSeconds(20)).until {
            analyses.findReport(analysisId)?.state?.isTerminal == true
        }
        return analyses.findReport(analysisId)!!
    }

    // -------------------------------------------------------------- analysis

    @Test
    fun `profile A is MET on its own skill and MISSING on the other profile's`() {
        val report = runAnalysis(profileA)

        val kotlin = report.requirements.single { it.skillName == "Kotlin" }
        val kubernetes = report.requirements.single { it.skillName == "Kubernetes" }

        assertEquals(RequirementStatus.MET, kotlin.status)
        assertEquals(RequirementStatus.MISSING, kubernetes.status)
    }

    @Test
    fun `profile B is MET on its own skill and MISSING on the other profile's`() {
        val report = runAnalysis(profileB)

        val kotlin = report.requirements.single { it.skillName == "Kotlin" }
        val kubernetes = report.requirements.single { it.skillName == "Kubernetes" }

        assertEquals(RequirementStatus.MISSING, kotlin.status)
        assertEquals(RequirementStatus.MET, kubernetes.status)
    }

    // -------------------------------------------------------------- documents

    @Test
    fun `a CV for profile A claiming profile B's skill is refused`() {
        runAnalysis(profileA)
        models[LlmTask.DOCUMENT].enqueue(
            """
            {"summaryLine":"Backend engineer experienced with Kubernetes at scale.",
             "skillNames":["Kotlin"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        val failure = assertThrows<FabricatedClaimException> {
            documents.generate(offerId, profileA, DocumentType.CV, "English")
        }

        assertEquals(listOf("Kubernetes"), failure.claims)
        assertNull(documents.latest(offerId, DocumentType.CV, profileA), "a refused document must not persist")
    }

    @Test
    fun `a CV for profile B claiming profile A's skill is refused`() {
        runAnalysis(profileB)
        models[LlmTask.DOCUMENT].enqueue(
            """
            {"summaryLine":"Platform engineer who also ships services in Kotlin.",
             "skillNames":["Kubernetes"],
             "bullets":[{"bulletId":$kubernetesBulletId,"text":"Ran production clusters on Kubernetes."}]}
            """.trimIndent()
        )

        val failure = assertThrows<FabricatedClaimException> {
            documents.generate(offerId, profileB, DocumentType.CV, "English")
        }

        assertEquals(listOf("Kotlin"), failure.claims)
        assertNull(documents.latest(offerId, DocumentType.CV, profileB), "a refused document must not persist")
    }

    /** Control: an honest generation, naming only what its own profile holds, must succeed. */
    @Test
    fun `an honest CV naming only its own profile's skills succeeds`() {
        runAnalysis(profileA)
        models[LlmTask.DOCUMENT].enqueue(
            """
            {"summaryLine":"Backend engineer focused on Kotlin services.",
             "skillNames":["Kotlin","Spring Boot"],
             "bullets":[{"bulletId":$kotlinBulletId,"text":"Built payment services in Kotlin."}]}
            """.trimIndent()
        )

        val document = documents.generate(offerId, profileA, DocumentType.CV, "English")

        assertEquals(DocumentType.CV, document.type)
        assertEquals(profileA, document.profileId)
    }
}
