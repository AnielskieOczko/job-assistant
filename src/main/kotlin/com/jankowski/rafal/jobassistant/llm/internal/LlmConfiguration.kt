package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelAuditing
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.OutboundPromptInspector
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties::class)
@EnableScheduling
internal class LlmConfiguration {

    @Bean
    fun chatModelRegistry(
        properties: LlmProperties,
        auditing: ChatModelAuditing,
    ): ChatModelRegistry = OpenAiCompatibleChatModelRegistry(properties, auditing)

    @Bean
    fun aiServiceFactory(
        models: ChatModelRegistry,
        inspectors: List<OutboundPromptInspector>,
    ): AiServiceFactory = DefaultAiServiceFactory(models, inspectors)
}
