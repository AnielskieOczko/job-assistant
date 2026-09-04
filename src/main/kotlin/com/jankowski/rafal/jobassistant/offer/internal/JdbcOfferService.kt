package com.jankowski.rafal.jobassistant.offer.internal

import com.jankowski.rafal.jobassistant.offer.Application
import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.JobOffer
import com.jankowski.rafal.jobassistant.offer.OfferOrigin
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.offer.OfferSummary
import com.jankowski.rafal.jobassistant.offer.PastedOffer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
internal class JdbcOfferService(
    private val offers: JobOfferRepository,
    private val applications: ApplicationRepository,
) : OfferService {

    @Transactional
    override fun paste(rawText: String, sourceUrl: String?): PastedOffer =
        store(rawText, sourceUrl, OfferOrigin.PASTED, marketOfferId = null)

    @Transactional
    override fun promoteFromMarket(rawText: String, sourceUrl: String?, marketOfferId: Long): PastedOffer =
        store(rawText, sourceUrl, OfferOrigin.MARKET, marketOfferId)

    /**
     * One path for both entry points, so a promoted offer is a pasted offer that knows where it
     * came from rather than a second kind of row. The deduplication is deliberately shared: it is
     * what makes promoting twice return the offer you already have instead of forking its history.
     */
    private fun store(
        rawText: String,
        sourceUrl: String?,
        origin: OfferOrigin,
        marketOfferId: Long?,
    ): PastedOffer {
        require(rawText.isNotBlank()) { "Offer text must not be blank" }

        val hash = OfferContentHash.of(rawText)
        offers.findByContentHash(hash)?.let { return PastedOffer(it.toDomain(), deduplicated = true) }

        val saved = offers.save(
            JobOfferRow(
                contentHash = hash,
                rawText = rawText,
                sourceUrl = sourceUrl,
                title = null,
                company = null,
                seniority = null,
                detectedLanguage = null,
                origin = origin.name,
                marketOfferId = marketOfferId,
            )
        )
        applications.save(
            ApplicationRow(
                jobOfferId = saved.id!!,
                status = ApplicationStatus.SAVED.name,
                statusChangedAt = stamp(),
                appliedOn = null,
                notes = null,
            )
        )
        return PastedOffer(saved.toDomain(), deduplicated = false)
    }

    @Transactional(readOnly = true)
    override fun findById(id: Long): JobOffer? = offers.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun list(): List<OfferSummary> {
        val applicationsByOffer = applications.findAllApplications().associateBy { it.jobOfferId }
        return offers.findAllNewestFirst().mapNotNull { offer ->
            applicationsByOffer[offer.id]?.let { OfferSummary(offer.toDomain(), it.toDomain()) }
        }
    }

    @Transactional(readOnly = true)
    override fun applicationFor(offerId: Long): Application? =
        applications.findByJobOfferId(offerId)?.toDomain()

    @Transactional
    override fun describe(
        offerId: Long,
        title: String?,
        company: String?,
        seniority: String?,
        detectedLanguage: String?,
    ): JobOffer {
        val row = offers.findById(offerId).orElseThrow { UnknownOfferException(offerId) }
        return offers.save(
            row.copy(
                title = title ?: row.title,
                company = company ?: row.company,
                seniority = seniority ?: row.seniority,
                detectedLanguage = detectedLanguage ?: row.detectedLanguage,
            )
        ).toDomain()
    }

    @Transactional
    override fun updateStatus(
        offerId: Long,
        status: ApplicationStatus,
        appliedOn: LocalDate?,
        notes: String?,
    ): Application {
        val row = applications.findByJobOfferId(offerId) ?: throw UnknownOfferException(offerId)
        return applications.save(
            row.copy(
                status = status.name,
                // Only stamp the change time when the status actually moved, so editing notes
                // does not make an old application look freshly touched.
                statusChangedAt = if (row.status == status.name) row.statusChangedAt else stamp(),
                appliedOn = appliedOn ?: row.appliedOn,
                notes = notes ?: row.notes,
            )
        ).toDomain()
    }

    @Transactional
    override fun markCvSent(offerId: Long, documentId: Long?): Application =
        recordSent(offerId) { it.copy(sentCvDocumentId = documentId) }

    @Transactional
    override fun markCoverLetterSent(offerId: Long, documentId: Long?): Application =
        recordSent(offerId) { it.copy(sentCoverLetterDocumentId = documentId) }

    /**
     * Null is a value here, not "leave it alone" - unmarking is how a mis-click is corrected, so
     * this cannot use the `?:` fallback that [updateStatus] applies to its optional fields.
     */
    private fun recordSent(offerId: Long, edit: (ApplicationRow) -> ApplicationRow): Application {
        val row = applications.findByJobOfferId(offerId) ?: throw UnknownOfferException(offerId)
        return applications.save(edit(row)).toDomain()
    }
}

internal class UnknownOfferException(offerId: Long) : NoSuchElementException("No job offer $offerId")

/**
 * Postgres `timestamptz` holds microseconds and rounds anything finer, so a nanosecond
 * `Instant.now()` comes back from a re-read as a different value than the one just written — the
 * object a write returns would not equal the row the database holds. Linux exposes this; macOS
 * hides it by handing out microsecond-resolution clocks, which is why CI found it and no laptop did.
 */
private fun stamp(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

private fun JobOfferRow.toDomain() = JobOffer(
    id = requireNotNull(id),
    contentHash = contentHash,
    rawText = rawText,
    sourceUrl = sourceUrl,
    title = title,
    company = company,
    seniority = seniority,
    detectedLanguage = detectedLanguage,
    createdAt = createdAt,
    origin = OfferOrigin.valueOf(origin),
    marketOfferId = marketOfferId,
)

private fun ApplicationRow.toDomain() = Application(
    id = requireNotNull(id),
    offerId = jobOfferId,
    status = ApplicationStatus.valueOf(status),
    statusChangedAt = statusChangedAt,
    appliedOn = appliedOn,
    notes = notes,
    sentCvDocumentId = sentCvDocumentId,
    sentCoverLetterDocumentId = sentCoverLetterDocumentId,
)
