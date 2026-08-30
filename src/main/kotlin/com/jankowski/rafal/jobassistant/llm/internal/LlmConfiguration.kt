package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelAuditing
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.OutboundPromptInspector
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient
import java.time.Duration

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

    /**
     * Its own short-timeout [RestClient], separate from anything a model call uses.
     *
     * This is a second opinion on a dashboard, not part of any pipeline: waiting the model
     * profile's 120 seconds for it would be the wrong trade in every case, so it gets a few
     * seconds and is allowed to fail.
     */
    @Bean
    fun providerAccountClient(): ProviderAccountClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(ACCOUNT_TIMEOUT)
            setReadTimeout(ACCOUNT_TIMEOUT)
        }
        return OpenRouterAccountClient(RestClient.builder().requestFactory(requestFactory).build())
    }

    private companion object {
        val ACCOUNT_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
