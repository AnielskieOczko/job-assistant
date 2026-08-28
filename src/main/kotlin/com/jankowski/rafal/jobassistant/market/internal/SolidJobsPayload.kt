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
