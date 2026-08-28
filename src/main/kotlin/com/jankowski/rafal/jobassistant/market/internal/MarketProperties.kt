package com.jankowski.rafal.jobassistant.market.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Ingestion is configured, not coded.
 *
 * [enabled] gates the scheduled poll only; a manual run through the controller works either way, so
 * turning the schedule off does not take the feature away.
 */
@ConfigurationProperties(prefix = "job-assistant.market")
data class MarketProperties(
    val enabled: Boolean = true,
    /** Cron for the scheduled poll. Daily is ample: the whole IT division is three requests. */
    val cron: String = "0 20 4 * * *",
    val solidJobs: SolidJobsProperties = SolidJobsProperties(),
)

data class SolidJobsProperties(
    val baseUrl: String = "https://solid.jobs",
    /**
     * The source's docs ask callers to identify themselves with a campaign parameter, and its FAQ
     * invites integrations. Sending it is the cost of being a welcome client rather than traffic.
     */
    val campaign: String = "job-assistant",
    /** Divisions to poll. IT is the only one this application has any use for. */
    val divisions: List<String> = listOf("IT"),
    /** 500 is the largest page the API served in testing; the IT division fits in three. */
    val pageSize: Int = 500,
    /** A guard against an unbounded loop if the API ever reports a page count it cannot serve. */
    val maxPages: Int = 20,
    val timeout: Duration = Duration.ofSeconds(60),
)
