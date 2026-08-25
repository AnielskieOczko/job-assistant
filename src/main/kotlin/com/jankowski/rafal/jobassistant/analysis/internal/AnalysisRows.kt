package com.jankowski.rafal.jobassistant.analysis.internal

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("analysis")
internal data class AnalysisRow(
    @Id val id: Long? = null,
    val jobOfferId: Long,
    val profileId: Long,
    val state: String,
    val error: String? = null,
    val modelProfile: String? = null,
    val matchScore: BigDecimal? = null,
    val summaryMd: String? = null,
    val createdAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    /** Profile revision this run was computed from - null for runs that predate the counter. */
    val profileRevision: Long? = null,
)

/**
 * Children are separate aggregates rather than an owned collection: the analysis row is updated
 * repeatedly as the job advances, and an owned collection would be rewritten (and briefly
 * deleted) on every state transition.
 */
@Table("offer_requirement")
internal data class OfferRequirementRow(
    @Id val id: Long? = null,
    val analysisId: Long,
    val rawText: String,
    val canonicalSkillId: Long?,
    val importance: String,
    val status: String,
    val evidence: String?,
    val rationale: String?,
    val displayOrder: Int,
)

@Table("language_requirement")
internal data class LanguageRequirementRow(
    @Id val id: Long? = null,
    val analysisId: Long,
    val language: String,
    val requiredLevel: String,
    val heldLevel: String?,
    val status: String,
)

@Table("learning_plan_item")
internal data class LearningPlanItemRow(
    @Id val id: Long? = null,
    val analysisId: Long,
    val canonicalSkillId: Long?,
    val skillName: String,
    val why: String,
    val practiceProject: String?,
    val effortEstimate: String?,
    val priority: Int,
)
