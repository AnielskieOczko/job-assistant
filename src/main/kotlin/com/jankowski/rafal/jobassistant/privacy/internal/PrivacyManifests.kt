package com.jankowski.rafal.jobassistant.privacy.internal

import com.jankowski.rafal.jobassistant.privacy.PrivacyField
import com.jankowski.rafal.jobassistant.privacy.PrivacyManifest
import com.jankowski.rafal.jobassistant.privacy.PrivacyState

/**
 * The manifest content, held in Kotlin rather than a migration or a config file because it
 * describes code, not data: a row here is true exactly as long as the mechanism it names still
 * exists, and the two drift together because both live in this module.
 *
 * **The `ENFORCED` names must equal what `PromptPrivacyInvariant.violations` can return** -
 * `fullName`, `email`, `phone`, `links`. `PrivacyManifestTest` asserts that dynamically, by
 * running `violations` against a synthetic prompt rather than by re-reading this list, so a check
 * `PromptPrivacyInvariant` stops performing is caught here rather than by a reader trusting a
 * badge.
 */
internal object PrivacyManifests {

    val MANIFEST = PrivacyManifest(
        fields = listOf(
            PrivacyField(
                name = "fullName",
                label = "Full name",
                state = PrivacyState.ENFORCED,
                mechanism = "PromptPrivacyInvariant refuses to send any outgoing prompt that contains it.",
            ),
            PrivacyField(
                name = "email",
                label = "Email",
                state = PrivacyState.ENFORCED,
                mechanism = "PromptPrivacyInvariant refuses to send any outgoing prompt that contains it.",
            ),
            PrivacyField(
                name = "phone",
                label = "Phone",
                state = PrivacyState.ENFORCED,
                mechanism = "PromptPrivacyInvariant refuses to send any outgoing prompt that contains it.",
            ),
            PrivacyField(
                name = "links",
                label = "Links & project URLs",
                state = PrivacyState.ENFORCED,
                mechanism = "PromptPrivacyInvariant refuses to send any outgoing prompt that contains one of these.",
            ),
            PrivacyField(
                name = "location",
                label = "Location",
                state = PrivacyState.OMITTED,
                mechanism = "No prompt builder reads this field, so it is never assembled into a request in " +
                    "the first place. Not policed by a guard: most locations are just a country name, and a " +
                    "hard refusal on \"Poland\" would break every Polish job offer.",
            ),
            PrivacyField(
                name = "portrait",
                label = "Photo",
                state = PrivacyState.OMITTED,
                mechanism = "No prompt builder reads it. The candidate profile a prompt is built from carries " +
                    "only whether a photo exists, never the image itself.",
            ),
            PrivacyField(
                name = "consentClause",
                label = "Consent clause",
                state = PrivacyState.OMITTED,
                mechanism = "Rendered straight into a generated document from what is stored; no prompt builder reads it.",
            ),
            PrivacyField(
                name = "employers",
                label = "Employers",
                state = PrivacyState.SENT,
                mechanism = "Sent when tailoring a CV, cover letter or gap report - naming what you did without " +
                    "naming where would make the result worthless.",
            ),
            PrivacyField(
                name = "schools",
                label = "Schools",
                state = PrivacyState.SENT,
                mechanism = "Sent when tailoring a CV or cover letter, for the same reason employers are.",
            ),
            PrivacyField(
                name = "dates",
                label = "Employment & education dates",
                state = PrivacyState.SENT,
                mechanism = "Sent alongside the employer or school they belong to.",
            ),
            PrivacyField(
                name = "bulletText",
                label = "Experience bullet text",
                state = PrivacyState.SENT,
                mechanism = "Sent when tailoring a CV or cover letter - it is the evidence being tailored.",
            ),
            PrivacyField(
                name = "skills",
                label = "Skill names",
                state = PrivacyState.SENT,
                mechanism = "Sent to extraction and tailoring so your match against an offer can be computed.",
            ),
        ),
        offerScrubbing = "A pasted job offer is scrubbed of any recruiter email address or phone number " +
            "before its text reaches a model - a third party's data the extractor has no use for.",
    )
}
