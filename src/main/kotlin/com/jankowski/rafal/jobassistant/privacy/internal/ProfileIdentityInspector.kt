package com.jankowski.rafal.jobassistant.privacy.internal

import com.jankowski.rafal.jobassistant.llm.OutboundPromptInspector
import com.jankowski.rafal.jobassistant.privacy.SensitiveDataInPromptException
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.springframework.stereotype.Component

/**
 * The registered guard: refuses any prompt carrying a direct identifier from any profile.
 *
 * This is the enforcement half of a three-part arrangement. Prompt builders leave identifiers out,
 * offer text is scrubbed of them, and then this asserts that neither step was skipped. In normal
 * operation it never fires - if it does, a prompt builder has started including something it
 * should not, and the builder is the bug.
 */
@Component
internal class ProfileIdentityInspector(
    private val profiles: ProfileService,
) : OutboundPromptInspector {

    override fun inspect(renderedPrompt: String) {
        val violations = PromptPrivacyInvariant.violations(renderedPrompt, profiles.identities())
        if (violations.isNotEmpty()) throw SensitiveDataInPromptException(violations)
    }
}
