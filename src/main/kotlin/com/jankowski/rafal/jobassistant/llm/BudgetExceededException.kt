package com.jankowski.rafal.jobassistant.llm

import java.math.BigDecimal

/**
 * Thrown instead of making a model call, because a configured spending cap has been reached.
 *
 * The message is the whole user-facing artifact: the call is refused before the listener pipeline
 * runs, so there is no audit row to look at afterwards and nothing else will say what happened. It
 * therefore names the period, the cap and the running total rather than saying "budget exceeded" -
 * a refusal you cannot act on is indistinguishable from a bug.
 *
 * Reaches the reader the same way `SensitiveDataInPromptException` does: `AnalysisRunner` turns it
 * into a `FAILED` run carrying this message, which is persisted and served over HTTP.
 */
class BudgetExceededException(
    val period: String,
    val limitUsd: BigDecimal,
    val spentUsd: BigDecimal,
) : RuntimeException(
    "Refused: the $period model-spend cap of $limitUsd is reached ($spentUsd spent). " +
        "Raise job-assistant.llm.budget.${period.lowercase()}-usd or wait for the period to roll over."
)
