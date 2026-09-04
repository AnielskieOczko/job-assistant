package com.jankowski.rafal.jobassistant.market.internal

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableConfigurationProperties(MarketProperties::class)
internal class MarketConfiguration {

    /**
     * Its own [RestClient] rather than a shared builder, so a timeout tuned for a third party's
     * paging endpoint cannot leak into anything else the application talks to.
     */
    @Bean
    internal fun solidJobsClient(properties: MarketProperties, jsonMapper: JsonMapper): SolidJobsClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.solidJobs.timeout)
            setReadTimeout(properties.solidJobs.timeout)
        }
        val restClient = RestClient.builder()
            .baseUrl(properties.solidJobs.baseUrl)
            .requestFactory(requestFactory)
            .build()
        return HttpSolidJobsClient(restClient, properties.solidJobs, jsonMapper)
    }
}
