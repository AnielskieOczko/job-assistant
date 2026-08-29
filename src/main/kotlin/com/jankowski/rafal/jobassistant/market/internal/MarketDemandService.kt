package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import com.jankowski.rafal.jobassistant.market.MarketDemand
import org.springframework.stereotype.Service

/**
 * Measures demand inside the configured scope.
 *
 * The scope itself is resolved by [MarketScopeResolver], shared with the dashboard, so the review
 * queue and the market page can never come to mean different things by "in scope".
 */
@Service
internal class MarketDemandService(
    private val repository: MarketOfferRepository,
    private val scope: MarketScopeResolver,
) : MarketDemand {

    override fun inScopeUnresolvedDemand(): Map<String, Int> {
        val resolved = scope.resolve()
        // No scope resolves to no measure, not to the whole corpus. Silently widening to everything
        // would report a market-wide number under a label that says "in scope".
        if (resolved.isEmpty) return emptyMap()

        val counts = mutableMapOf<String, Int>()
        repository.unresolvedSkillMentionsInOffersWith(resolved.ids).forEach { (name, mentions) ->
            val key = SkillNormalizer.normalize(name)
            if (key.isEmpty()) return@forEach
            counts[key] = (counts[key] ?: 0) + mentions
        }
        return counts
    }

    override fun scopeSkills(): List<String> = scope.resolve().names
}
