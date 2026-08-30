package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmSpendInsights
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
internal class LlmSpendController(private val spend: LlmSpendInsights) {

    @GetMapping
    fun report(
        @RequestParam(defaultValue = "30") days: Int,
        @RequestParam(defaultValue = "DAY") bucket: SpendBucket,
    ): SpendReport = spend.report(days, bucket)
}
