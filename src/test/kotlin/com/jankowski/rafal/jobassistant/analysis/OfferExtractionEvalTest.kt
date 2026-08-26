package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.analysis.internal.AnalysisPromptFormatter
import com.jankowski.rafal.jobassistant.analysis.internal.ExtractedOffer
import com.jankowski.rafal.jobassistant.analysis.internal.OfferExtractor
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
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
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertTrue

/**
 * Measures extraction quality against hand-labelled offers using a **live** model.
 *
 * Excluded from the default build: run with `./mvnw test -Peval`. Costs tokens. The point is not
 * to pass but to be comparable — change a prompt or swap a model, re-run this, and see whether
 * recall moved. Without it, prompt edits are guesswork. Every number here also lands in
 * `target/eval-report.md` via [EvalScorecard], because comparing runs means having the previous
 * one written down.
 *
 * Deliberately does not import the stub LLM configuration; this is the one test that talks to a
 * real provider.
 */
@Tag("eval")
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class OfferExtractionEvalTest(
    @Autowired private val aiServices: AiServiceFactory,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val models: ChatModelRegistry,
    @Autowired private val json: JsonMapper,
) {

    private companion object {
        const val SUITE = "offer-extraction"
    }

    /**
     * Both assertions below judge the same extraction. Memoising it keeps them scoring one sample
     * rather than two independent ones, and halves the tokens a run costs.
     */
    private val extractions = ConcurrentHashMap<String, ExtractedOffer>()

    @BeforeAll
    fun describeRun() {
        EvalScorecard.describe("extraction.profile", models.profileNameFor(LlmTask.EXTRACTION))
    }

    fun fixtures(): List<String> = EvalFixtures.names()

    private fun expectation(fixture: String): EvalFixtures.Expectation =
        json.readValue(EvalFixtures.labelJson(fixture))

    private fun extract(fixture: String): ExtractedOffer = extractions.computeIfAbsent(fixture) {
        aiServices
            .create(OfferExtractor::class.java, LlmTask.EXTRACTION)
            .extract(EvalFixtures.offerText(it), AnalysisPromptFormatter.catalogListing(catalog.findAll()))
    }

    /**
     * Recall is the number that matters: a missed requirement silently disappears from the gap
     * report, whereas a spurious one is visible and easy to dismiss. The threshold is a
     * regression guard, not a quality target.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `extraction recalls the labelled skills`(fixture: String) {
        val expected = expectation(fixture)
        val extracted = extract(fixture)

        val found = extracted.requirements
            .mapNotNull { requirement ->
                requirement.catalogSkill.ifBlank { null }?.let { catalog.resolve(it) }
                    ?: catalog.resolve(requirement.rawText)
            }
            .map { it.name }
            .toSet()

        val expectedAll = (expected.mustHaves + expected.niceToHaves)
            .mapNotNull { catalog.resolve(it)?.name }
            .toSet()

        val hit = expectedAll intersect found
        val missed = expectedAll - found
        val extra = found - expectedAll

        val recall = if (expectedAll.isEmpty()) 1.0 else hit.size.toDouble() / expectedAll.size
        val precision = if (found.isEmpty()) 0.0 else hit.size.toDouble() / found.size
        val languageCorrect = extracted.detectedLanguage.equals(expected.detectedLanguage, ignoreCase = true)

        EvalScorecard.record(
            suite = SUITE,
            fixture = fixture,
            metrics = mapOf(
                "recall" to recall,
                "precision" to precision,
                "languageCorrect" to if (languageCorrect) 1.0 else 0.0,
            ),
            notes = buildMap {
                if (missed.isNotEmpty()) put("missed", missed.sorted().joinToString())
                if (extra.isNotEmpty()) put("extra", extra.sorted().joinToString())
                if (!languageCorrect) put("language", "expected ${expected.detectedLanguage}, got ${extracted.detectedLanguage}")
            },
        )

        assertTrue(recall >= 0.6, "recall ${"%.2f".format(recall)} below 0.6 for $fixture; missed $missed")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `must-haves are not silently demoted to nice-to-haves`(fixture: String) {
        val expected = expectation(fixture)
        if (expected.mustHaves.isEmpty()) return

        val extracted = extract(fixture)

        val extractedMustHaves = extracted.requirements
            .filter { it.importance.equals("MUST_HAVE", ignoreCase = true) }
            .mapNotNull { catalog.resolve(it.catalogSkill.ifBlank { it.rawText })?.name }
            .toSet()

        val expectedMustHaves = expected.mustHaves.mapNotNull { catalog.resolve(it)?.name }.toSet()
        val demoted = expectedMustHaves - extractedMustHaves
        val kept = (expectedMustHaves.size - demoted.size).toDouble() / expectedMustHaves.size

        EvalScorecard.record(
            suite = SUITE,
            fixture = fixture,
            metrics = mapOf("mustHaveRetention" to kept),
            notes = if (demoted.isEmpty()) emptyMap() else mapOf("demoted" to demoted.sorted().joinToString()),
        )

        assertTrue(
            demoted.size <= expectedMustHaves.size / 2,
            "over half the must-haves were missed or demoted for $fixture: $demoted",
        )
    }
}
