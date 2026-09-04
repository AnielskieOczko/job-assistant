package com.jankowski.rafal.jobassistant.market.internal

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/**
 * The solid.jobs response, as it actually arrives.
 *
 * Every property has a default and [JsonIgnoreProperties] is lenient on purpose: this is a third
 * party's schema, and a field they add or stop sending must not fail a run. The verbatim JSON is
 * stored alongside the mapped columns, so anything not modelled here is still recoverable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SolidJobsPage(
    val jobs: List<SolidJobsOffer> = emptyList(),
    val pageIndex: Int = 0,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SolidJobsOffer(
    val jobOfferKey: String = "",
    val title: String = "",
    val company: String? = null,
    val division: String? = null,
    val category: String? = null,
    val subCategory: String? = null,
    val url: String? = null,
    /**
     * The full HTML posting. Modelled because promotion needs it: an offer copied into the
     * candidate's own list has to carry the employer's words, and a gap report built from anything
     * else would be scoring our own skill resolution under the name of a match score.
     *
     * The field is what made this source viable over Adzuna and NoFluffJobs, both of which serve a
     * teaser (`docs/research/10-offer-ingestion-sources.md`), and it went unmodelled until #79.
     */
    val description: String? = null,
    val experienceLevel: String? = null,
    val contractTime: String? = null,
    val isRemote: Boolean = false,
    val isHybrid: Boolean = false,
    val locations: List<String> = emptyList(),
    val salary: SolidJobsSalary? = null,
    val skills: List<SolidJobsSkill> = emptyList(),
    /** Validity as stated by the source; present on every offer in a 500-offer sample. */
    val validFrom: String? = null,
    val validTo: String? = null,
    val updatedAt: String? = null,
    /**
     * This offer's own JSON exactly as it arrived, filled in by [SolidJobsPages] rather than by
     * Jackson, and stored as `market_offer.payload`.
     *
     * V14 promised the payload was the whole response verbatim, on the reasoning that offers get
     * delisted and a field not stored now is not re-fetchable later. It was not: ingestion stored a
     * re-serialisation of *this* class, so every field nobody had thought to model - `description`,
     * `languages`, `benefits` - was dropped before it reached the column meant to preserve it. That
     * cost the corpus its posting prose, which is what #79 needed. Keeping the source text is what
     * makes the promise true.
     */
    val raw: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SolidJobsSalary(
    val from: BigDecimal? = null,
    val to: BigDecimal? = null,
    val currency: String? = null,
    val period: String? = null,
    /** B2B, UoP, UZ, UoD or Staż -- five values, not the two a "B2B vs employment" split implies. */
    val employmentType: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SolidJobsSkill(
    val name: String = "",
    val level: String? = null,
)
