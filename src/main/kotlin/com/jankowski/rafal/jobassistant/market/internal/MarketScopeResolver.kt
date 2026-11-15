package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.ProfileTargetRole
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Turns the active scope into catalog identity, for every reader of the corpus.
 *
 * When the profile has a [com.jankowski.rafal.jobassistant.profile.TargetRole] with at least one
 * skill, that target is the scope; otherwise it falls back to `job-assistant.market.scope.skills`
 * so a fresh install or a profile without a target still renders the configured market slice.
 *
 * Resolved on every call rather than cached at startup. It is a handful of lookups against a table
 * small enough to sit in memory, and a scope cached past a skill rename or target edit would
 * silently start ranking by something other than what it claims to.
 */
@Component
internal class MarketScopeResolver(
    private val catalog: SkillCatalog,
    private val properties: MarketProperties,
    private val targetRoles: ProfileTargetRole,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun resolve(profileId: Long? = null): ResolvedScope {
        val target = targetRoles.of(profileId)
        if (target != null && target.skills.isNotEmpty()) {
            val skills = catalog.findAllById(target.skills.map { it.id })
                .sortedBy { it.name }
            return ResolvedScope(skills, unresolvedNames = emptyList())
        }

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
