package com.jankowski.rafal.jobassistant.support

/**
 * The labelled offers under `src/test/resources/eval/offers`, shared by every eval-tier suite.
 *
 * Each fixture is a `NN-name.txt` offer paired with a `NN-name.json` hand-label. Extraction scores
 * itself against the labels; the document suites use them as the requirements a CV is tailored to,
 * which keeps a tailoring score from being dragged around by extraction's mistakes.
 */
object EvalFixtures {

    data class Expectation(
        val title: String = "",
        val detectedLanguage: String = "",
        val mustHaves: List<String> = emptyList(),
        val niceToHaves: List<String> = emptyList(),
        val languages: List<ExpectedLanguage> = emptyList(),
    )

    data class ExpectedLanguage(val language: String = "", val level: String = "")

    fun names(): List<String> = listOf(
        "01-senior-kotlin-backend",
        "02-paraphrased-devops",
        "03-polish-java-offer",
        "04-fullstack-react",
        "05-vague-junior",
    )

    fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/eval/$name")) { "missing fixture $name" }
            .bufferedReader().readText()

    fun offerText(fixture: String): String = resource("offers/$fixture.txt")

    fun labelJson(fixture: String): String = resource("offers/$fixture.json")

    /** The candidate every document eval tailors for. Deliberately lacks Kubernetes, Kafka and Terraform. */
    fun profileJson(): String = resource("profile.json")
}
