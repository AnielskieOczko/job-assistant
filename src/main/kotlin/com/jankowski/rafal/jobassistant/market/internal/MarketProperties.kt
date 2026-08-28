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
    val scope: MarketScopeProperties = MarketScopeProperties(),
)

/**
 * Which slice of the corpus counts as "this job hunt".
 *
 * The whole IT division is ingested, and it is thick with QA, BA and PM roles whose vocabulary a
 * backend candidate will never review. Scope names the skills that mark an offer as one worth
 * learning from, so demand can be measured inside it rather than across a market this candidate is
 * not in.
 *
 * Configured once here rather than per feature: the review queue ranks by it now and the market
 * dashboard will report against it later, and two notions of "relevant" would be two numbers with
 * one name -- the trap this module already avoided by not reusing `matchScore`.
 *
 * Canonical catalog names, not aliases and not free text. A name the catalog cannot resolve is
 * logged and ignored rather than silently narrowing the scope to nothing.
 */
data class MarketScopeProperties(
    val skills: List<String> = listOf("Java", "Kotlin", "Spring", "Spring Boot"),
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
