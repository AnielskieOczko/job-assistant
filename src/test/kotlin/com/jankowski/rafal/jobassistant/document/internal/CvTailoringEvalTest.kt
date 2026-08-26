package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.RequirementFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.EvalFixtures
import com.jankowski.rafal.jobassistant.support.EvalScorecard
import com.jankowski.rafal.jobassistant.support.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import kotlin.test.assertTrue

/**
 * Measures how often a **live** model tailoring a CV cites things that do not exist.
 *
 * This suite needs no rubric and no judge: [CvSelection.from] already computes the answer as a
 * side effect of enforcing it. A bullet id the profile does not contain, or a skill name it does
 * not hold, is dropped — and counting the drops is an objective hallucination rate.
 *
 * The requirements come from the fixture's hand-written labels rather than from a live extraction,
 * so a tailoring score is not dragged around by extraction's mistakes. The fixture profile
 * deliberately lacks Kubernetes, Kafka and Terraform, which several fixtures ask for: a model
 * inclined to claim what the offer wants has every opportunity to do so here.
 *
 * Run with `./mvnw test -Peval`. Costs tokens; one model call per fixture.
 */
@Tag("eval")
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
internal class CvTailoringEvalTest(
    @Autowired private val aiServices: AiServiceFactory,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val models: ChatModelRegistry,
    @Autowired private val json: JsonMapper,
    @Autowired private val jdbc: JdbcClient,
) {

    private companion object {
        const val SUITE = "cv-tailoring"

        /**
         * A regression guard, not a quality target. Dropping a third of what the model asked for
         * still produces a truthful CV — [CvSelection] and [CvInvariant] see to that — but it means
         * the prompt has stopped steering, and the scorecard is where the real number lives.
         */
        const val MAX_DROP_RATE = 0.34
    }

    private lateinit var profile: CandidateProfile
    private var profileId: Long = 0

    @BeforeAll
    fun setUp() {
        jdbc.sql("delete from profile").update()
        profileId = management.create("Eval fixture").id
        profiles.replace(profileId, json.readValue<ProfileImport>(EvalFixtures.profileJson()))
        profile = profiles.require(profileId)

        EvalScorecard.describe("document.profile", models.profileNameFor(LlmTask.DOCUMENT))
    }

    fun fixtures(): List<String> = EvalFixtures.names()

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `tailoring cites only bullets and skills that exist`(fixture: String) {
        val expected: EvalFixtures.Expectation = json.readValue(EvalFixtures.labelJson(fixture))

        val tailored = aiServices.create(CvTailor::class.java, LlmTask.DOCUMENT).tailor(
            roleTitle = expected.title.ifBlank { "Backend Engineer" },
            company = "the company",
            requirements = ProfileBriefing.requirements(reportFor(expected)),
            profile = ProfileBriefing.profile(profile, catalog),
            language = "English",
        )

        val selection = CvSelection.from(tailored, profile, catalog)

        val bulletDropRate = rate(selection.droppedBulletIds.size, tailored.bullets.map { it.bulletId }.distinct().size)
        val skillDropRate = rate(selection.droppedSkillNames.size, tailored.skillNames.distinct().size)

        // The invariant is the shipping safety net; here it is simply another thing to count. A
        // rejection means the free-text summary or a rewritten bullet named an absent technology,
        // which CvSelection's id filtering cannot catch.
        val violations = CvInvariant.violations(
            listOfNotNull(selection.summaryLine).plus(selection.rewrittenText.values).joinToString(" "),
            catalog.findAll(),
            profile.heldSkillIds,
        )

        val summaryWords = selection.summaryLine?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0

        EvalScorecard.record(
            suite = SUITE,
            fixture = fixture,
            metrics = mapOf(
                "bulletDropRate" to bulletDropRate,
                "skillDropRate" to skillDropRate,
                "invariantViolations" to violations.size.toDouble(),
                "bulletsSelected" to selection.bulletOrder.size.toDouble(),
                "summaryWords" to summaryWords.toDouble(),
            ),
            notes = buildMap {
                if (selection.droppedBulletIds.isNotEmpty()) {
                    put("invented bullet ids", selection.droppedBulletIds.joinToString())
                }
                if (selection.droppedSkillNames.isNotEmpty()) {
                    put("unheld skills claimed", selection.droppedSkillNames.sorted().joinToString())
                }
                if (violations.isNotEmpty()) put("invariant would reject", violations.joinToString())
            },
        )

        assertTrue(
            bulletDropRate <= MAX_DROP_RATE,
            "$fixture: ${"%.0f%%".format(bulletDropRate * 100)} of selected bullet ids do not exist " +
                "(${selection.droppedBulletIds})",
        )
        assertTrue(
            skillDropRate <= MAX_DROP_RATE,
            "$fixture: ${"%.0f%%".format(skillDropRate * 100)} of claimed skills are not held " +
                "(${selection.droppedSkillNames})",
        )
        assertTrue(
            selection.bulletOrder.isNotEmpty(),
            "$fixture: tailoring selected no bullets at all",
        )
    }

    private fun rate(dropped: Int, requested: Int) = if (requested == 0) 0.0 else dropped.toDouble() / requested

    /**
     * The labelled requirements, given the statuses the deterministic diff would compute for the
     * fixture profile. Built here rather than by running an analysis so that the only live model
     * call in this suite is the one being measured.
     */
    private fun reportFor(expected: EvalFixtures.Expectation): AnalysisReport {
        val coverage = catalog.coverageFor(profile.heldSkillIds)

        fun findings(names: List<String>, importance: Importance) = names.mapIndexed { index, name ->
            val skill = catalog.resolve(name)
            RequirementFinding(
                id = index.toLong(),
                rawText = name,
                skillId = skill?.id,
                skillName = skill?.name,
                importance = importance,
                status = when (skill?.let { coverage.statusFor(it.id) }) {
                    CoverageStatus.MET -> RequirementStatus.MET
                    CoverageStatus.PARTIAL -> RequirementStatus.PARTIAL
                    CoverageStatus.MISSING -> RequirementStatus.MISSING
                    null -> RequirementStatus.UNRESOLVED
                },
                evidence = null,
                rationale = null,
            )
        }

        return AnalysisReport(
            id = 0,
            offerId = 0,
            profileId = profileId,
            state = AnalysisState.DONE,
            error = null,
            matchScore = null,
            summaryMarkdown = null,
            requirements = findings(expected.mustHaves, Importance.MUST_HAVE) +
                findings(expected.niceToHaves, Importance.NICE_TO_HAVE),
            languageRequirements = emptyList(),
            learningPlan = emptyList(),
            createdAt = Instant.now(),
            completedAt = Instant.now(),
        )
    }
}
