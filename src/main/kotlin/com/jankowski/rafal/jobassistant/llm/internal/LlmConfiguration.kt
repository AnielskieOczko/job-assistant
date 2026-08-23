package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelAuditing
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties::class)
internal class LlmConfiguration {

    @Bean
    fun chatModelRegistry(
        properties: LlmProperties,
        auditing: ChatModelAuditing,
    ): ChatModelRegistry = OpenAiCompatibleChatModelRegistry(properties, auditing)

    @Bean
    fun aiServiceFactory(models: ChatModelRegistry): AiServiceFactory = DefaultAiServiceFactory(models)
}
