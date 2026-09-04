package com.jankowski.rafal.jobassistant.polish.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillMentions
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.polish.UnusablePolishException
import com.jankowski.rafal.jobassistant.polish.PolishField
import com.jankowski.rafal.jobassistant.polish.PolishSuggestion
import com.jankowski.rafal.jobassistant.polish.ProsePolishService
import com.jankowski.rafal.jobassistant.profile.ProfileService
import dev.langchain4j.guardrail.OutputGuardrailException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Asks a model to rewrite one field, then reads the answer back before anyone sees it.
 *
 * Three properties hold this feature inside rule one, and all three are here rather than in a
 * prompt:
 *
 * - **Nothing is written.** This service has no repository and no write path of any kind. The
 *   suggestion goes back over HTTP and stops there; the profile changes only when the candidate
 *   clicks accept and the client calls the ordinary CRUD endpoint.
 * - **The suggestion is scanned before it is shown.** [SkillMentions] - the reading the CV
 *   fabrication guard runs on a finished document - is run on the suggestion, and any catalog skill
 *   the profile does not hold comes back named. Flagged rather than refused, because the reader is
 *   the candidate and not an employer.
 * - **The model gets the field and nothing else.** No employer, no dates, no URL, no profile id.
 *   The privacy invariant would refuse a call carrying a project URL, and building the prompt from
 *   the entity is exactly how one would get there.
 */
@Service
internal class DefaultProsePolishService(
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
    private val aiServices: AiServiceFactory,
    private val models: ChatModelRegistry,
) : ProsePolishService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun polish(profileId: Long, field: PolishField, text: String): PolishSuggestion {
        val original = text.trim()
        require(original.isNotBlank()) {
            "There is nothing to polish. This rewrites text you have written; it does not write a $field for you."
        }
        require(original.length <= ProsePolishService.MAX_TEXT_LENGTH) {
            "A $field is at most ${ProsePolishService.MAX_TEXT_LENGTH} characters to polish; this one is ${original.length}."
        }

        // Read for the held skills only, but read before the call: an unknown profile should cost
        // nothing, and a suggestion that cannot be scanned must not be shown.
        val profile = profiles.require(profileId)

        // A guardrail failure is a refusal to show what came back, not a server fault, so it is
        // translated rather than propagated: the caller gets the same 422 an empty answer produces,
        // carrying the reason the guardrail gave.
        val suggestion = try {
            aiServices
                .create(ProsePolisher::class.java, LlmTask.POLISH)
                .polish(field = field.name, guidance = PolishBriefs.of(field), text = original)
                .polishedOrBlank()
        } catch (refused: OutputGuardrailException) {
            throw UnusablePolishException(field, refused.message)
        }

        if (suggestion.isEmpty()) throw UnusablePolishException(field)

        val unheld = SkillMentions.unheld(suggestion, catalog.findAll(), profile.heldSkillIds)
        if (unheld.isNotEmpty()) {
            // Worth a line in the log for the same reason `dropped_skill_count` is persisted: one
            // flagged suggestion means nothing, and a model that has started reaching for
            // technologies the profile cannot back shows up as this climbing.
            log.info("Polish suggestion for {} names {} skill(s) the profile does not hold", field, unheld.size)
        }

        return PolishSuggestion(
            field = field,
            original = original,
            suggestion = suggestion,
            unheldSkills = unheld,
            modelProfile = models.profileNameFor(LlmTask.POLISH),
        )
    }
}
