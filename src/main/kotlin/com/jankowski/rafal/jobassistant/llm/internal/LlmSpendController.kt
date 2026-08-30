package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmSpendInsights
import com.jankowski.rafal.jobassistant.llm.ProviderAccount
import com.jankowski.rafal.jobassistant.llm.ProviderAccountLookup
import com.jankowski.rafal.jobassistant.llm.SpendBucket
import com.jankowski.rafal.jobassistant.llm.SpendReport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * What the models have cost.
 *
 * Deliberately separate from `/api/llm/calls`: that one serves rows that age out after thirty
 * days, this one serves totals that must not. One endpoint returns the whole dashboard because a
 * summary, its series and its breakdowns are one reading and splitting them would let a page
 * render a share against a denominator fetched a second later.
 */
@RestController
@RequestMapping("/api/llm/spend")
internal class LlmSpendController(
    private val spend: LlmSpendInsights,
    private val accounts: ProviderAccountLookup,
) {

    @GetMapping
    fun report(
        @RequestParam(defaultValue = "30") days: Int,
        @RequestParam(defaultValue = "DAY") bucket: SpendBucket,
    ): SpendReport = spend.report(days, bucket)

    /**
     * What the provider says the key has spent.
     *
     * Its own endpoint rather than a field on the report, because it is an outbound call to a third
     * party and the dashboard must still render when they are down. A failure comes back as an
     * unavailable account with a reason, never as a 5xx - there is no error here for the reader to
     * act on, only a second opinion that could not be obtained.
     */
    @GetMapping("/account")
    fun account(): ProviderAccount = accounts.account()
}
