package com.jankowski.rafal.jobassistant.triage

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import com.jankowski.rafal.jobassistant.triage.internal.ModelTriageSuggestionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * The model half of triage, against a scripted model.
 *
 * The assertions that matter are the refusals. A suggestion is a candidate put in front of a
 * person, so the interesting question is never "did it suggest something" but "what happens when it
 * suggests something that is not real".
 */
@IntegrationTest
internal class ModelTriageSuggestionIntegrationTest(
    @Autowired private val service: ModelTriageSuggestionService,
    @Autowired private val triage: TriageService,
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun reset() {
        models.resetAll()
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from unmatched_term").update()
    }

    private fun queueTerm(term: String, own: Int = 5) {
        jdbc.sql(
            """
            insert into unmatched_term (term, normalized_term, occurrences, market_occurrences)
            values (:term, :key, :own, 0)
            """
        ).param("term", term).param("key", SkillNormalizer.normalize(term)).param("own", own).update()
    }

    private fun script(json: String) = models[LlmTask.TRIAGE].enqueue(json)

    private fun modelCalls() = models[LlmTask.TRIAGE].requests.size

    @Test
    fun `a suggestion the catalog can resolve is stored and shown on the queue`() {
        queueTerm("Analiza wymagan testowych")
        script(
            """
            {"suggestions":[{"term":"Analiza wymagan testowych","catalogSkill":"Kotlin",
              "rationale":"Test rationale."}]}
            """
        )

        val run = service.suggestFor(1, TriageRanking.CORPUS, 25)

        assertThat(run.suggestionsStored).isEqualTo(1)
        val entry = triage.queue(1, TriageRanking.CORPUS, 100)
            .entries.single { it.term == "Analiza wymagan testowych" }
        assertThat(entry.modelSuggestions).singleElement()
            .satisfies({
                assertThat(it.skillName).isEqualTo("Kotlin")
                assertThat(it.rationale).isEqualTo("Test rationale.")
                assertThat(it.modelProfile).isNotBlank()
            })
    }

    /**
     * The central guarantee, `CvSelection.from` transplanted: the model may select from the list it
     * was handed, never create. A name the catalog cannot resolve is discarded outright - storing it
     * would let a model put a skill into the review pipeline.
     */
    @Test
    fun `a suggested skill the catalog does not have is dropped, not stored`() {
        queueTerm("Some Unplaceable Term")
        script(
            """
            {"suggestions":[{"term":"Some Unplaceable Term","catalogSkill":"Blockchain Whisperer",
              "rationale":"Invented."}]}
            """
        )

        val run = service.suggestFor(1, TriageRanking.CORPUS, 25)

        assertThat(run.suggestionsStored).isZero()
        assertThat(run.droppedUnresolvable).isEqualTo(1)
        assertThat(triage.queue(1, TriageRanking.CORPUS, 100).entries)
            .allSatisfy { assertThat(it.modelSuggestions).isEmpty() }
    }

    /** A row for a term nobody asked about has no queue row to attach to, and guessing would be worse. */
    @Test
    fun `a suggestion for a term that was not sent is dropped`() {
        queueTerm("Real Queued Term")
        script(
            """
            {"suggestions":[{"term":"A Term Never Asked About","catalogSkill":"Kotlin",
              "rationale":"Unrequested."}]}
            """
        )

        val run = service.suggestFor(1, TriageRanking.CORPUS, 25)

        assertThat(run.droppedUnrequested).isEqualTo(1)
        assertThat(run.suggestionsStored).isZero()
    }

    /** A model emitting an explicit null must not become a null List behind a non-null type. */
    @Test
    fun `a null suggestions array is read as empty rather than exploding`() {
        queueTerm("Null Probe Term")
        script("""{"suggestions":null}""")

        val run = service.suggestFor(1, TriageRanking.CORPUS, 25)

        assertThat(run.suggestionsStored).isZero()
        assertThat(run.termsSent).isEqualTo(1)
    }

    /**
     * Reading the queue must never spend a token. A page load that silently called a model would
     * contradict the approval-gated eval environment this project runs everywhere else.
     */
    @Test
    fun `reading the queue never calls a model`() {
        queueTerm("Untouched Term")

        triage.queue(1, TriageRanking.CORPUS, 100)
        triage.queue(1, TriageRanking.SCOPE, 100)

        // Nothing was scripted; a call would have failed for want of a response.
        assertThat(modelCalls()).isZero()
    }

    /** Re-asking about a term already answered costs a call and tells the reviewer nothing new. */
    @Test
    fun `a term that already has a suggestion is not sent again`() {
        queueTerm("Repeat Term")
        script("""{"suggestions":[{"term":"Repeat Term","catalogSkill":"Kotlin","rationale":"First."}]}""")
        service.suggestFor(1, TriageRanking.CORPUS, 25)

        val second = service.suggestFor(1, TriageRanking.CORPUS, 25)

        assertThat(second.termsConsidered).isEqualTo(1)
        assertThat(second.termsSent).isZero()
        assertThat(modelCalls()).isEqualTo(1)
    }

    /** A suggestion is a candidate, never a decision: it cannot change what a term resolves to. */
    @Test
    fun `storing a suggestion does not resolve the term`() {
        queueTerm("Still Unresolved Term")
        script(
            """
            {"suggestions":[{"term":"Still Unresolved Term","catalogSkill":"Kotlin","rationale":"x"}]}
            """
        )

        service.suggestFor(1, TriageRanking.CORPUS, 25)

        assertThat(catalog.resolve("Still Unresolved Term")).isNull()
        assertThat(triage.queue(1, TriageRanking.CORPUS, 100).entries.map { it.term })
            .contains("Still Unresolved Term")
    }
}
