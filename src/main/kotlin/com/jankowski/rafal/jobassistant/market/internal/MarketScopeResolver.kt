package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Turns the configured scope names into catalog identity, for every reader of the corpus.
 *
 * One resolver rather than one per consumer. The review queue ranks by in-scope demand and the
 * dashboard reports against the same scope, and two copies of "which skills mark an offer as this
 * job hunt's" would be two notions of relevance under one name -- the trap this module already
 * avoided by refusing to reuse `matchScore`.
 *
 * Resolved on every call rather than cached at startup. It is a handful of lookups against a table
 * small enough to sit in memory, and a scope cached past a skill rename would silently start
 * ranking by something other than what it claims to.
 */
@Component
internal class MarketScopeResolver(
    private val catalog: SkillCatalog,
    private val properties: MarketProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun resolve(): ResolvedScope {
        val resolved = mutableListOf<CanonicalSkill>()
        val unresolved = mutableListOf<String>()

        properties.scope.skills.forEach { name ->
            val skill = catalog.resolve(name)
            if (skill == null) {
                // Logged *and* returned. A hard failure would take the whole page down over a typo
                // in configuration, but a silent drop narrows every number on it -- so the drop
                // travels with the answer and the caller can render what it lost.
                log.warn("Market scope skill '{}' does not resolve to a catalog entry; ignoring it", name)
                unresolved += name
            } else {
                resolved += skill
            }
        }

        return ResolvedScope(resolved, unresolved)
    }
}

/**
 * The scope as the catalog could actually place it.
 *
 * [unresolvedNames] is part of the answer, not diagnostics: "in-scope demand" measured against a
 * scope that quietly lost half its skills is a number with a misleading label, which is the same
 * failure as a rate with no denominator.
 */
internal data class ResolvedScope(
    val skills: List<CanonicalSkill>,
    val unresolvedNames: List<String>,
) {
    val ids: List<Long> get() = skills.map { it.id }
    val names: List<String> get() = skills.map { it.name }

    /** No scope resolves to no measure, never to the whole corpus. */
    val isEmpty: Boolean get() = skills.isEmpty()
}
