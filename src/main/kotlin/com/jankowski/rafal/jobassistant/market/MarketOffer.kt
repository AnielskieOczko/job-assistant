package com.jankowski.rafal.jobassistant.market

import java.math.BigDecimal
import java.time.Instant

/**
 * One offer in the ingested market corpus.
 *
 * This is not a [com.jankowski.rafal.jobassistant.offer.JobOffer] and must not become one. A
 * JobOffer is something the candidate might apply to, carrying a lifecycle, analyses and generated
 * documents; this is a row in a sample. Saving one for real is an explicit copy, not a promotion in
 * place.
 */
data class MarketOffer(
    val id: Long,
    /** The board this came from. Every statistic must name its source, so the source travels. */
    val source: String,
    val offerKey: String,
    val title: String,
    val company: String?,
    val division: String?,
    val category: String?,
    val subCategory: String?,
    val url: String?,
    /**
     * The posting prose as the source published it, HTML and all. Null for every row ingested
     * before V28 and for any source that does not serve one; such a row cannot be promoted, because
     * a promoted offer carries the employer's words or it is not the offer.
     */
    val description: String?,
    val experienceLevel: String?,
    val contractTime: String?,
    val isRemote: Boolean,
    val isHybrid: Boolean,
    val locations: List<String>,
    val salary: MarketSalary?,
    val validFrom: Instant?,
    val validTo: Instant?,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val skills: List<MarketOfferSkill>,
)

/**
 * A salary as the source states it, already normalised. Nothing here is inferred from prose, which
 * is the whole reason this corpus is worth ingesting: [employmentType] carries the B2B-versus-
 * employment distinction that Polish offers usually bury in free text, when they state it at all.
 */
data class MarketSalary(
    val from: BigDecimal?,
    val to: BigDecimal?,
    val currency: String?,
    val period: String?,
    val employmentType: String?,
)

data class MarketOfferSkill(
    val name: String,
    val level: MarketSkillLevel,
    /** Null when the catalog could not place the name; the term is queued for review instead. */
    val canonicalSkillId: Long?,
)

/**
 * The level a market offer asks a skill at.
 *
 * [NICE_TO_HAVE] is the odd one out and worth naming: the source carries its only importance signal
 * on this field rather than a separate one, and it is rare -- 128 of 3,746 skill mentions in a
 * 500-offer sample. That is why the market-side coverage measure does not weight by importance and
 * is deliberately a different number from the analysis module's match score.
 *
 * [UNKNOWN] exists because this is a third party's enum: a value we have not seen must not fail an
 * ingestion run, and losing the row would be a worse outcome than recording an unmapped level.
 */
enum class MarketSkillLevel {
    BASIC, ADVANCED, EXPERT, NICE_TO_HAVE, UNKNOWN;

    /** Whether the offer treats the skill as required at all. */
    val isRequired: Boolean get() = this != NICE_TO_HAVE

    companion object {
        fun parse(raw: String?): MarketSkillLevel = when (raw?.trim()?.lowercase()) {
            "basic" -> BASIC
            "advanced" -> ADVANCED
            "expert" -> EXPERT
            "nicetohave", "nice_to_have" -> NICE_TO_HAVE
            else -> UNKNOWN
        }
    }
}
