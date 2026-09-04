package com.jankowski.rafal.jobassistant.polish

/**
 * A rewrite offered to the candidate, and everything they need in order to judge it.
 *
 * **Nothing here has been stored.** The suggestion is a proposal; the profile still holds
 * [original] and will go on holding it until the candidate accepts through the ordinary CRUD
 * endpoint. That is what keeps "no model writes to the profile" literally true rather than
 * enforced by a prompt.
 *
 * [original] travels back with the suggestion so the two can be shown side by side without the
 * client having to trust that its own copy of the field is the one that was sent.
 */
data class PolishSuggestion(
    val field: PolishField,
    val original: String,
    val suggestion: String,
    /**
     * Catalog skills the suggestion names that the profile does not hold, if any.
     *
     * **Flagged, not refused** - deliberately weaker than the CV fabrication guard, which throws
     * the whole document away. The difference is who is about to read the text: a CV is going to an
     * employer, and this is going back to the candidate, who may reasonably answer by declaring the
     * skill. The same reading produces both; only the consequence differs.
     */
    val unheldSkills: List<String> = emptyList(),
    /** Which configured model profile answered, so an odd suggestion can be traced to a model. */
    val modelProfile: String = "",
)
