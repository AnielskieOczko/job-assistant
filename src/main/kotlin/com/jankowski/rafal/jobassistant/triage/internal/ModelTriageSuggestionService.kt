package com.jankowski.rafal.jobassistant.triage.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.triage.SuggestionRun
import com.jankowski.rafal.jobassistant.triage.TriageRanking
import com.jankowski.rafal.jobassistant.triage.TriageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Asks a model to read the terms string similarity could not place.
 *
 * **Explicitly invoked, never implicit.** `GET /queue` does not call this and must not: a page load
 * that silently spends tokens would contradict the approval-gated eval environment and the
 * deliberately tiny analysis pool. Suggestions are stored so a reviewer sees them without paying
 * for them twice.
 *
 * One call per batch rather than one per term. The catalog listing dominates the prompt, so fifty
 * separate calls would send it fifty times to answer the same question.
 */
@Service
internal class ModelTriageSuggestionService(
    private val triage: TriageService,
    private val catalog: SkillCatalog,
    private val suggestions: TriageSuggestionRepository,
    private val aiServices: AiServiceFactory,
    private val models: ChatModelRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun suggestFor(minOccurrences: Int, ranking: TriageRanking, limit: Int): SuggestionRun {
        require(limit in 1..MAX_BATCH) { "batch must be between 1 and $MAX_BATCH terms" }

        val queue = triage.queue(minOccurrences, ranking, limit)
        val alreadyDone = suggestions.termsWithSuggestions(queue.entries.map { it.termId })
        // Terms with a stored suggestion are skipped rather than refreshed: re-asking costs a call
        // and answers a question already on the reviewer's screen.
        val candidates = queue.entries.filter { it.termId !in alreadyDone }

        if (candidates.isEmpty()) {
            return SuggestionRun(termsConsidered = queue.entries.size, termsSent = 0)
        }

        val skills = catalog.findAll()
        val byTerm = candidates.associateBy { it.term }
        val modelProfile = models.profileNameFor(LlmTask.TRIAGE)
        val readings = aiServices
            .create(TriageSuggester::class.java, LlmTask.TRIAGE)
            .suggest(candidates.joinToString("\n") { "- ${it.term}" }, catalogListing(skills))
            .suggestionsOrEmpty()

        var stored = 0
        var unresolvable = 0
        var unrequested = 0

        readings.forEach { reading ->
            val entry = byTerm[reading.term.trim()]
            if (entry == null) {
                // A term nobody asked about. The model either altered the spelling it was told to
                // echo, or invented the term outright; either way there is no queue row to attach
                // it to and guessing which one was meant would be the fabrication this design exists
                // to prevent.
                unrequested++
                return@forEach
            }

            val skill = catalog.resolve(reading.catalogSkill.trim())
            if (skill == null) {
                // CvSelection.from transplanted: the model may select from the list it was given,
                // never create. A name the catalog cannot resolve is discarded rather than queued,
                // because queueing it would let a model put a term into the review pipeline.
                unresolvable++
                return@forEach
            }

            suggestions.replaceFor(
                termId = entry.termId,
                suggestions = listOf(skill.id to reading.rationale.ifBlank { null }),
                modelProfile = modelProfile,
            )
            stored++
        }

        if (unresolvable > 0 || unrequested > 0) {
            log.info(
                "Triage suggestions: {} stored, {} named a skill the catalog does not have, {} for terms not asked about",
                stored, unresolvable, unrequested,
            )
        }

        return SuggestionRun(
            termsConsidered = queue.entries.size,
            termsSent = candidates.size,
            suggestionsStored = stored,
            droppedUnresolvable = unresolvable,
            droppedUnrequested = unrequested,
        )
    }

    /** Same shape the extraction prompt uses, so the model sees one catalog format, not two. */
    private fun catalogListing(skills: List<CanonicalSkill>): String =
        skills.joinToString("\n") { "- ${it.name} [${it.category}]" }

    private companion object {
        /**
         * Terms per call. The catalog listing dominates the prompt, so batching is nearly free up to
         * the point where the *response* gets long enough to risk truncation.
         */
        const val MAX_BATCH = 50
    }
}
