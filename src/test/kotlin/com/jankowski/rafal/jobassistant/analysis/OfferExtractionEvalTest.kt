package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.analysis.internal.AnalysisPromptFormatter
import com.jankowski.rafal.jobassistant.analysis.internal.OfferExtractor
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.support.TestcontainersConfiguration
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
import kotlin.test.assertTrue

/**
 * Measures extraction quality against hand-labelled offers using a **live** model.
 *
 * Excluded from the default build: run with `./mvnw test -Peval`. Costs tokens. The point is not
 * to pass but to be comparable — change a prompt or swap a model, re-run this, and see whether
 * recall moved. Without it, prompt edits are guesswork.
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
    @Autowired private val json: JsonMapper,
) {

    data class Expectation(
        val title: String = "",
        val detectedLanguage: String = "",
        val mustHaves: List<String> = emptyList(),
        val niceToHaves: List<String> = emptyList(),
        val languages: List<ExpectedLanguage> = emptyList(),
    )

    data class ExpectedLanguage(val language: String = "", val level: String = "")

    fun fixtures(): List<String> = listOf(
        "01-senior-kotlin-backend",
        "02-paraphrased-devops",
        "03-polish-java-offer",
        "04-fullstack-react",
        "05-vague-junior",
    )

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/eval/offers/$name")) { "missing fixture $name" }
            .bufferedReader().readText()

    /**
     * Recall is the number that matters: a missed requirement silently disappears from the gap
     * report, whereas a spurious one is visible and easy to dismiss. The threshold is a
     * regression guard, not a quality target.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `extraction recalls the labelled skills`(fixture: String) {
        val offerText = resource("$fixture.txt")
        val expected: Expectation = json.readValue(resource("$fixture.json"))

        val extracted = aiServices
            .create(OfferExtractor::class.java, LlmTask.EXTRACTION)
            .extract(offerText, AnalysisPromptFormatter.catalogListing(catalog.findAll()))

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

        println(
            """
            |
            |=== $fixture ===
            |  language : expected ${expected.detectedLanguage}, got ${extracted.detectedLanguage}
            |  recall   : ${"%.2f".format(recall)}  (${hit.size}/${expectedAll.size})
            |  precision: ${"%.2f".format(precision)}
            |  missed   : ${missed.sorted()}
            |  extra    : ${extra.sorted()}
            |  languages: ${extracted.languageRequirements}
            """.trimMargin()
        )

        assertTrue(recall >= 0.6, "recall ${"%.2f".format(recall)} below 0.6 for $fixture; missed $missed")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `must-haves are not silently demoted to nice-to-haves`(fixture: String) {
        val offerText = resource("$fixture.txt")
        val expected: Expectation = json.readValue(resource("$fixture.json"))
        if (expected.mustHaves.isEmpty()) return

        val extracted = aiServices
            .create(OfferExtractor::class.java, LlmTask.EXTRACTION)
            .extract(offerText, AnalysisPromptFormatter.catalogListing(catalog.findAll()))

        val extractedMustHaves = extracted.requirements
            .filter { it.importance.equals("MUST_HAVE", ignoreCase = true) }
            .mapNotNull { catalog.resolve(it.catalogSkill.ifBlank { it.rawText })?.name }
            .toSet()

        val expectedMustHaves = expected.mustHaves.mapNotNull { catalog.resolve(it)?.name }.toSet()
        val demoted = expectedMustHaves - extractedMustHaves

        println("=== $fixture must-haves: expected $expectedMustHaves, demoted or missed $demoted")

        assertTrue(
            demoted.size <= expectedMustHaves.size / 2,
            "over half the must-haves were missed or demoted for $fixture: $demoted",
        )
    }
}
