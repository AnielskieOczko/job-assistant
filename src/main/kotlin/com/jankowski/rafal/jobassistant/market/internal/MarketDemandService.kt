package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import com.jankowski.rafal.jobassistant.market.MarketDemand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Measures demand inside the configured scope.
 *
 * The scope is resolved through the catalog on every call rather than cached at startup. It is a
 * handful of lookups against an in-memory-sized table, and a scope that went stale after a skill
 * was renamed would silently start ranking by something other than what it claims.
 */
@Service
internal class MarketDemandService(
    private val repository: MarketOfferRepository,
    private val catalog: SkillCatalog,
    private val properties: MarketProperties,
) : MarketDemand {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun inScopeUnresolvedDemand(): Map<String, Int> {
        val scopeIds = resolvedScope().map { it.id }
        // No scope resolves to no measure, not to the whole corpus. Silently widening to everything
        // would report a market-wide number under a label that says "in scope".
        if (scopeIds.isEmpty()) return emptyMap()

        val counts = mutableMapOf<String, Int>()
        repository.unresolvedSkillMentionsInOffersWith(scopeIds).forEach { (name, mentions) ->
            val key = SkillNormalizer.normalize(name)
            if (key.isEmpty()) return@forEach
            counts[key] = (counts[key] ?: 0) + mentions
        }
        return counts
    }

    override fun scopeSkills(): List<String> = resolvedScope().map { it.name }

    /**
     * Configured names that the catalog actually resolves.
     *
     * A name that does not resolve is dropped with a warning rather than failing the request: the
     * queue is still worth serving with a narrower scope, and a hard failure would take the whole
     * review page down over a typo in configuration. The warning is what makes the drop visible.
     */
    private fun resolvedScope() = properties.scope.skills.mapNotNull { name ->
        catalog.resolve(name).also {
            if (it == null) log.warn("Market scope skill '{}' does not resolve to a catalog entry; ignoring it", name)
        }
    }
}
