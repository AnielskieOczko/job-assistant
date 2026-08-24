package com.jankowski.rafal.jobassistant.offer

import java.time.Instant
import java.time.LocalDate

data class JobOffer(
    val id: Long,
    val contentHash: String,
    val rawText: String,
    val sourceUrl: String? = null,
    val title: String? = null,
    val company: String? = null,
    val seniority: String? = null,
    val detectedLanguage: String? = null,
    val createdAt: Instant,
) {
    /** A short label for listings, before extraction has supplied a real title. */
    val displayTitle: String
        get() = title ?: rawText.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "Untitled offer"
}

enum class ApplicationStatus { SAVED, ANALYZED, APPLIED, INTERVIEWING, REJECTED, OFFER }

data class Application(
    val id: Long,
    val offerId: Long,
    val status: ApplicationStatus,
    val statusChangedAt: Instant,
    val appliedOn: LocalDate? = null,
    val notes: String? = null,
)

data class OfferSummary(val offer: JobOffer, val application: Application)

/**
 * Result of pasting offer text. [deduplicated] is true when the text matched an offer already
 * stored, in which case [offer] is the existing one and nothing new was created.
 */
data class PastedOffer(val offer: JobOffer, val deduplicated: Boolean)
