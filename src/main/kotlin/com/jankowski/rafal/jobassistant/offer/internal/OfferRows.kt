package com.jankowski.rafal.jobassistant.offer.internal

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate

@Table("job_offer")
internal data class JobOfferRow(
    @Id val id: Long? = null,
    val contentHash: String,
    val rawText: String,
    val sourceUrl: String?,
    val title: String?,
    val company: String?,
    val seniority: String?,
    val detectedLanguage: String?,
    // Set here rather than relying on the column default: Spring Data JDBC writes every mapped
    // property, so a null would be sent explicitly and defeat `default now()`.
    val createdAt: Instant = Instant.now(),
    /** PASTED or MARKET. See V28 and [com.jankowski.rafal.jobassistant.offer.OfferOrigin]. */
    val origin: String = "PASTED",
    val marketOfferId: Long? = null,
)

@Table("application")
internal data class ApplicationRow(
    @Id val id: Long? = null,
    val jobOfferId: Long,
    val status: String,
    val statusChangedAt: Instant,
    val appliedOn: LocalDate?,
    val notes: String?,
    /** The documents that were actually sent for this application. See V27. */
    val sentCvDocumentId: Long? = null,
    val sentCoverLetterDocumentId: Long? = null,
)
