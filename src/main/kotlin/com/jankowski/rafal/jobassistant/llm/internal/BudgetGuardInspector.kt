package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.BudgetExceededException
import com.jankowski.rafal.jobassistant.llm.OutboundPromptInspector
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Refuses a call once a configured spending cap is reached.
 *
 * An [OutboundPromptInspector] rather than a check at each pipeline entry point, for the reason
 * that interface's own documentation gives: whoever cares registers a bean and throws. It is also
 * the one seam every model call passes through, so this cannot be bypassed by adding a fourth
 * caller who forgot - the same argument that puts the privacy invariant here.
 *
 * The prompt is ignored entirely; this inspector cares about the account, not the message. That is
 * a slightly odd fit for the interface's shape and a deliberate trade: a second, near-identical
 * veto mechanism would be a worse one.
 *
 * **Accurate to within one call, by construction.** A call's cost is only known once it has been
 * answered, so a job started just under the cap can finish just over it. Overshooting by one
 * call's worth is the correct behaviour to accept here - the alternative is estimating a price
 * before the fact, which is the pricing-table guesswork this design rejected everywhere else.
 *
 * Nothing is sent and nothing is audited when this throws: `InspectingChatModel` runs above the
 * listener pipeline. That is why the refusal has to name the numbers itself.
 */
@Component
internal class BudgetGuardInspector(
    private val spend: LlmSpendRepository,
    private val properties: LlmProperties,
) : OutboundPromptInspector {

    override fun inspect(renderedPrompt: String) {
        val limits = properties.budget
        if (limits.dailyUsd == null && limits.monthlyUsd == null) return

        val today = LocalDate.now(ZoneOffset.UTC)
        limits.dailyUsd?.let { refuseIfReached("daily", it) { spend.total(today, today).costUsd } }
        limits.monthlyUsd?.let {
            refuseIfReached("monthly", it) { spend.total(today.withDayOfMonth(1), today).costUsd }
        }
    }

    private inline fun refuseIfReached(period: String, limit: BigDecimal, spent: () -> BigDecimal) {
        val total = spent()
        if (total >= limit) throw BudgetExceededException(period, limit, total)
    }
}
