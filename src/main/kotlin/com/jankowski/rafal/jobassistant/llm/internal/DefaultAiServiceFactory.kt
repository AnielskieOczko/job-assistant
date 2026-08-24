package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import dev.langchain4j.service.AiServices

internal class DefaultAiServiceFactory(
    private val models: ChatModelRegistry,
) : AiServiceFactory {

    override fun <T : Any> create(serviceType: Class<T>, task: LlmTask): T =
        AiServices.builder(serviceType)
            .chatModel(models.forTask(task))
            .outputGuardrails(JsonOutputGuardrail())
            .build()
}
