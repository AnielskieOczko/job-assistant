package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.CorpusSummary
import com.jankowski.rafal.jobassistant.market.IngestionReport
import com.jankowski.rafal.jobassistant.market.MarketOfferService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/market")
internal class MarketController(private val market: MarketOfferService) {

    /** What the corpus holds, per source. The window every statistic drawn from it must declare. */
    @GetMapping("/corpus")
    fun corpus(): List<CorpusSummary> = market.corpusSummary()

    /**
     * Runs a poll now. Idempotent by construction -- offers already stored are refreshed, not
     * duplicated -- so triggering it twice costs two requests to the source and nothing else.
     */
    @PostMapping("/ingest")
    fun ingest(): IngestionReport = market.ingest()
}
