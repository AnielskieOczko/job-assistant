package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.DemandRanking
import com.jankowski.rafal.jobassistant.market.DemandReport
import com.jankowski.rafal.jobassistant.market.MarketInsights
import com.jankowski.rafal.jobassistant.market.MarketOfferPage
import com.jankowski.rafal.jobassistant.market.MarketScopeReport
import com.jankowski.rafal.jobassistant.market.SalaryReport
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The read side of the corpus: what this job hunt's slice of the market asks for and pays.
 *
 * Read-only and model-free. Ingestion stays on [MarketController], which is the only place that
 * reaches a third party; nothing on this controller can spend a token or write a row, so a page
 * load costs a few aggregate queries and nothing else.
 *
 * `profileId` is optional throughout and falls back to the default profile, so every URL here is
 * deep-linkable -- the same convention `AnalysisController` uses for the aggregate gap report.
 */
@RestController
@RequestMapping("/api/market")
@Validated
internal class MarketInsightsController(private val insights: MarketInsights) {

    /** The scope line: the population every other number on the dashboard is measured over. */
    @GetMapping("/scope")
    fun scope(): MarketScopeReport = insights.scope()

    /** Salary bands per comparable slice, with the coverage that makes them readable. */
    @GetMapping("/salary")
    fun salary(): SalaryReport = insights.salary()

    /** The demand table, ranked by unmet demand unless asked otherwise. */
    @GetMapping("/demand")
    fun demand(
        @RequestParam(required = false) profileId: Long?,
        @RequestParam(defaultValue = "UNMET") ranking: DemandRanking,
        @RequestParam(defaultValue = "${MarketInsights.DEFAULT_LIMIT}") @Positive limit: Int,
    ): DemandReport = insights.demand(profileId, ranking, limit)

    /** The offers behind the numbers. In-scope by default: the dashboard's numbers are too. */
    @GetMapping("/offers")
    fun offers(
        @RequestParam(required = false) profileId: Long?,
        @RequestParam(defaultValue = "true") inScopeOnly: Boolean,
        @RequestParam(defaultValue = "${MarketInsights.DEFAULT_LIMIT}") @Positive limit: Int,
        @RequestParam(defaultValue = "0") @PositiveOrZero offset: Int,
    ): MarketOfferPage = insights.offers(profileId, inScopeOnly, limit, offset)
}
