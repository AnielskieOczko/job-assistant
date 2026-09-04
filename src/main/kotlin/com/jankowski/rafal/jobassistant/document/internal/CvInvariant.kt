package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillMentions

/**
 * The fabrication guard.
 *
 * Scans finished document text for the name of any catalog skill the candidate does not hold. A
 * hit means the model put a technology on a CV with nothing behind it, and the document is thrown
 * away rather than shown to the user.
 *
 * The reading itself is [SkillMentions], shared with the profile-polish surface. What is *not*
 * shared is the consequence, and that is the whole distinction: here a mention is a hard refusal,
 * because the next reader of this text is an employer. A suggestion the candidate has not accepted
 * yet has no such reader, so it is flagged instead. Softening this side to match the other would
 * put an unbacked claim on a CV.
 */
internal object CvInvariant {

    /**
     * @return the display names of skills mentioned in [text] that are not in [heldSkillIds].
     */
    fun violations(text: String, catalog: List<CanonicalSkill>, heldSkillIds: Set<Long>): List<String> =
        SkillMentions.unheld(text, catalog, heldSkillIds)
}
