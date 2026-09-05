package com.jankowski.rafal.jobassistant.privacy

/**
 * How one category of profile data is kept from - or sent to - a model provider.
 *
 * A two-state shield would be a lie: `location` and the portrait are never sent but nothing
 * refuses them, which is a different guarantee from a name a guard actively blocks. Collapsing
 * [ENFORCED] and [OMITTED] into one "protected" state would claim an enforcement the second one
 * does not have.
 */
enum class PrivacyState { ENFORCED, OMITTED, SENT }

/**
 * One row of the manifest. [name] is a stable key the frontend uses to find the entry for a
 * profile field it renders - never a value, only ever describing the field itself, for the same
 * reason `SensitiveDataInPromptException` reports field names and never values.
 */
data class PrivacyField(
    val name: String,
    val label: String,
    val state: PrivacyState,
    val mechanism: String,
)

/**
 * The whole answer to "what does this application do with my data", served by
 * `GET /api/privacy/manifest`.
 *
 * Static and profile-independent: the mechanism a field is subject to does not vary by persona,
 * so an absent profile still yields the full manifest rather than an error - the same rule
 * `SkillCoverage.EMPTY` states for a demand table.
 */
data class PrivacyManifest(
    val fields: List<PrivacyField>,
    val offerScrubbing: String,
)
