package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.MarketOfferService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Runs the poll on a schedule.
 *
 * Daily, because the corpus is a market sample rather than a feed the candidate is watching, and
 * because the whole IT division is three requests against a 300/minute limit -- there is nothing to
 * gain from polling harder and a published rate limit to respect.
 *
 * Gated by a property so the scheduled run can be switched off without removing the feature: a
 * manual ingest through the controller works either way, and integration tests boot the whole
 * application without ever wanting to reach a third party.
 */
@Component
@ConditionalOnProperty(prefix = "job-assistant.market", name = ["enabled"], havingValue = "true", matchIfMissing = true)
internal class MarketIngestionScheduler(private val market: MarketOfferService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${job-assistant.market.cron:0 20 4 * * *}")
    fun poll() {
        val report = market.ingest()
        if (report.error != null) {
            log.error("Scheduled market ingestion finished with an error: {}", report.error)
        } else {
            log.info(
                "Scheduled market ingestion: {} offers seen ({} new), {} of {} skill mentions resolved",
                report.offersSeen, report.offersInserted, report.skillsResolved, report.skillMentions,
            )
        }
    }
}
