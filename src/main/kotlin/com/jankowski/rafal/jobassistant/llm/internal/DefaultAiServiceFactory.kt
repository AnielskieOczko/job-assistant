package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.llm.OutboundPromptInspector
import dev.langchain4j.service.AiServices

internal class DefaultAiServiceFactory(
    private val models: ChatModelRegistry,
    private val inspectors: List<OutboundPromptInspector>,
) : AiServiceFactory {

    /**
     * The privacy wrapper goes on here rather than inside the registry so that it survives a
     * substituted [ChatModelRegistry] - tests swap the whole registry for scripted models, and a
     * guard those tests bypassed would prove nothing.
     */
    override fun <T : Any> create(serviceType: Class<T>, task: LlmTask): T =
        AiServices.builder(serviceType)
            .chatModel(InspectingChatModel(models.forTask(task), inspectors))
            .outputGuardrails(JsonOutputGuardrail())
            .build()
}
