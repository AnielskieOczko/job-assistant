package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.profile.CandidateProfile

/**
 * Explains, in one line, why a requirement came out MET or PARTIAL.
 *
 * This is what makes the report transparent rather than a verdict: the candidate can see which
 * bullet or declared skill produced each green light, and for a PARTIAL, which adjacent skill
 * they can honestly name in a cover letter.
 */
internal class EvidenceDescriber(
    private val profile: CandidateProfile,
    private val coverage: SkillCoverage,
    private val catalog: SkillCatalog,
) {

    fun describe(skillId: Long, status: RequirementStatus): String? {
        val coveringId = coverage.coveringSkillFor(skillId) ?: return null
        val coveringName = catalog.findById(coveringId)?.name ?: return null
        val bullet = profile.bulletsEvidencing(coveringId).firstOrNull()?.text

        return when (status) {
            RequirementStatus.MET -> when {
                coveringId == skillId && bullet != null -> "Evidenced by: $bullet"
                coveringId == skillId -> "Declared skill: $coveringName"
                bullet != null -> "Covered by $coveringName - $bullet"
                else -> "Covered by $coveringName"
            }

            RequirementStatus.PARTIAL -> when (bullet) {
                null -> "Adjacent skill held: $coveringName"
                else -> "Adjacent skill held: $coveringName - $bullet"
            }

            else -> null
        }
    }
}
