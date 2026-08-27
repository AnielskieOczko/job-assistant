package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.ChatModelAuditing
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import dev.langchain4j.model.chat.Capability
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds one [ChatModel] per task from the configured profiles.
 *
 * Per task rather than per profile so the audit listener can name the task even when two tasks
 * share a provider. LangChain4j's Spring Boot autoconfiguration is deliberately unused: it binds a
 * single model per type, which cannot express per-task routing.
 */
internal class OpenAiCompatibleChatModelRegistry(
    private val properties: LlmProperties,
    private val auditing: ChatModelAuditing,
) : ChatModelRegistry {

    private val log = LoggerFactory.getLogger(OpenAiCompatibleChatModelRegistry::class.java)
    private val models = ConcurrentHashMap<LlmTask, ChatModel>()

    override fun profileNameFor(task: LlmTask): String = properties.profileNameFor(task)

    override fun forTask(task: LlmTask): ChatModel = models.computeIfAbsent(task) { build(it) }

    private fun build(task: LlmTask): ChatModel {
        val profileName = properties.profileNameFor(task)
        val profile = properties.profileFor(task)

        check(profile.apiKey.isNotBlank()) {
            "Model profile '$profileName' (task $task) has no API key. Set the environment " +
                "variable referenced by job-assistant.llm.profiles.$profileName.api-key."
        }
        log.info("Task {} -> profile '{}' ({} at {})", task, profileName, profile.model, profile.baseUrl)

        return OpenAiChatModel.builder()
            .baseUrl(profile.baseUrl)
            .apiKey(profile.apiKey)
            .modelName(profile.model)
            .temperature(profile.temperature)
            .timeout(profile.timeout)
            .maxRetries(profile.maxRetries)
            .strictJsonSchema(profile.strictSchema)
            .logRequests(profile.logRequests)
            .logResponses(profile.logResponses)
            .apply {
                // Declaring the capability is what makes AiServices send a JSON schema rather than
                // asking for JSON in prose. Only claim it when the provider actually honours it.
                if (profile.strictSchema) {
                    supportedCapabilities(setOf(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                }
                // Merged into the request body as-is. Empty for providers that need nothing extra.
                if (profile.customParameters.isNotEmpty()) {
                    customParameters(profile.customParameters)
                }
            }
            .listeners(listOf(auditing.listenerFor(task, profileName)))
            .build()
    }
}
