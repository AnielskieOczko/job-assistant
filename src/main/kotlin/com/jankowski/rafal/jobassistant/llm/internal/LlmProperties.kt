package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmTask
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Named model profiles plus the task-to-profile routing.
 *
 * OpenRouter, Requesty, Ollama and LM Studio are all OpenAI-compatible, so a "provider" here is
 * nothing more than a base URL, a key and a model name. That is why there is no provider SPI.
 */
@ConfigurationProperties(prefix = "job-assistant.llm")
data class LlmProperties(
    val profiles: Map<String, ModelProfile> = emptyMap(),
    val tasks: Map<LlmTask, String> = emptyMap(),
    val audit: AuditProperties = AuditProperties(),
) {
    fun profileNameFor(task: LlmTask): String =
        tasks[task] ?: throw IllegalStateException(
            "No model profile configured for task $task. Set job-assistant.llm.tasks.${task.name.lowercase()}."
        )

    fun profileFor(task: LlmTask): ModelProfile {
        val name = profileNameFor(task)
        return profiles[name] ?: throw IllegalStateException(
            "Task $task points at model profile '$name', which is not defined under " +
                "job-assistant.llm.profiles. Known profiles: ${profiles.keys.sorted()}."
        )
    }
}

/** How long a copy of a prompt is kept. [Duration.ZERO] keeps them forever. */
data class AuditProperties(
    val retention: Duration = Duration.ofDays(30),
)

data class ModelProfile(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.2,
    /**
     * Whether the provider honours OpenAI strict JSON-schema mode. Small local models generally
     * do not, so they fall back to prompt-level instructions plus the repair guardrail.
     */
    val strictSchema: Boolean = true,
    val timeout: Duration = Duration.ofSeconds(120),
    val maxRetries: Int = 2,
    val logRequests: Boolean = false,
    val logResponses: Boolean = false,
)
