package com.jankowski.rafal.jobassistant.market.internal

import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

/**
 * Reads the solid.jobs public API.
 *
 * No key: the API is keyless by design, and its published rules are a 300 requests/minute limit,
 * an hourly cache, and a `campaign` parameter identifying the caller. All three are respected here
 * -- the poll is daily and the whole IT division is three requests, so the limit is nowhere near.
 */
internal class HttpSolidJobsClient(
    private val restClient: RestClient,
    private val properties: SolidJobsProperties,
) : SolidJobsClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetchPage(division: String, pageIndex: Int, pageSize: Int): SolidJobsPage {
        log.debug("Fetching solid.jobs division={} page={} size={}", division, pageIndex, pageSize)
        return restClient.get()
            .uri { builder ->
                builder.path("/public-api/offers/{division}")
                    .queryParam("campaign", properties.campaign)
                    .queryParam("pageIndex", pageIndex)
                    .queryParam("pageSize", pageSize)
                    .build(division)
            }
            .retrieve()
            .body(SolidJobsPage::class.java)
            ?: SolidJobsPage()
    }
}
