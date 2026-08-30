package com.jankowski.rafal.jobassistant.llm

import java.math.BigDecimal
import java.time.Instant

/**
 * What the provider says the configured key has spent, as opposed to what this application
 * recorded.
 *
 * The two are meant to be shown side by side, and **the gap is the point**. This application's own
 * total is an undercount by construction: it holds nothing from before cost capture existed, and
 * nothing spent on the same key by anything else. A single number would hide both.
 *
 * Kept out of [SpendReport] deliberately. This one costs an outbound HTTP call, and a dashboard
 * that cannot render until a third party answers is a dashboard that goes down when they do.
 */
data class ProviderAccount(
    /** The configured model profile this describes, so it is clear whose spend is being quoted. */
    val modelProfile: String?,
    /** Everything spent on this key, in the provider's billing unit. */
    val usageUsd: BigDecimal?,
    val usageTodayUsd: BigDecimal?,
    val usageMonthUsd: BigDecimal?,
    /** The key's own credit limit, when it has one. Null means unlimited, not zero. */
    val limitUsd: BigDecimal?,
    val limitRemainingUsd: BigDecimal?,
    val checkedAt: Instant?,
    /**
     * Why there is no figure, when there is none.
     *
     * Present rather than an error status: no configured provider reporting account spend is the
     * ordinary case for a local model, and it must read as "not applicable" rather than "broken".
     */
    val unavailableReason: String?,
) {
    val available: Boolean get() = usageUsd != null

    companion object {
        fun unavailable(reason: String) = ProviderAccount(
            modelProfile = null, usageUsd = null, usageTodayUsd = null, usageMonthUsd = null,
            limitUsd = null, limitRemainingUsd = null, checkedAt = null, unavailableReason = reason,
        )
    }
}

/** Answers "what does the provider itself say this key has spent". */
interface ProviderAccountLookup {
    fun account(): ProviderAccount
}
