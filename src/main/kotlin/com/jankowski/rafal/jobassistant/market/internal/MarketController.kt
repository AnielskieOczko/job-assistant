package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.CorpusSummary
import com.jankowski.rafal.jobassistant.market.IngestionReport
import com.jankowski.rafal.jobassistant.market.IngestionSchedule
import com.jankowski.rafal.jobassistant.market.MarketOfferService
import org.springframework.scheduling.support.CronExpression
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
@RequestMapping("/api/market")
internal class MarketController(
    private val market: MarketOfferService,
    private val properties: MarketProperties,
) {

    /** What the corpus holds, per source. The window every statistic drawn from it must declare. */
    @GetMapping("/corpus")
    fun corpus(): List<CorpusSummary> = market.corpusSummary()

    /**
     * Whether the corpus refreshes itself, and when it next will.
     *
     * Read from the same properties the scheduler is gated on rather than from a copy, so the page
     * cannot claim a schedule that is switched off. The next run is derived from the cron on every
     * request: caching it would let the answer outlive the run it describes.
     */
    @GetMapping("/ingestion")
    fun ingestion(): IngestionSchedule {
        val scheduled = properties.enabled
        return IngestionSchedule(
            scheduled = scheduled,
            cron = properties.cron.takeIf { scheduled },
            // Parsed defensively: a cron the scheduler rejected would have failed at startup, but a
            // dashboard is the wrong place to turn a configuration typo into a 500 on every load.
            nextPollAt = if (scheduled) nextRun(properties.cron) else null,
            lastPolledAt = market.corpusSummary().mapNotNull { it.lastSeenAt }.maxOrNull(),
        )
    }

    /**
     * Runs a poll now. Idempotent by construction -- offers already stored are refreshed, not
     * duplicated -- so triggering it twice costs two requests to the source and nothing else.
     */
    @PostMapping("/ingest")
    fun ingest(): IngestionReport = market.ingest()

    private fun nextRun(cron: String) =
        runCatching { CronExpression.parse(cron).next(ZonedDateTime.now())?.toInstant() }.getOrNull()
}
