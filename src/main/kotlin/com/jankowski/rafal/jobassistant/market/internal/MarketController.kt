package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.CorpusSummary
import com.jankowski.rafal.jobassistant.market.IngestionReport
import com.jankowski.rafal.jobassistant.market.IngestionSchedule
import com.jankowski.rafal.jobassistant.market.MarketOfferService
import com.jankowski.rafal.jobassistant.market.MarketPromotion
import com.jankowski.rafal.jobassistant.market.OfferNotPromotableException
import com.jankowski.rafal.jobassistant.market.PromotedOffer
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.support.CronExpression
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
@RequestMapping("/api/market")
internal class MarketController(
    private val market: MarketOfferService,
    private val promotion: MarketPromotion,
    private val properties: MarketProperties,
) {

    /** What the corpus holds, per source. The window every statistic drawn from it must declare. */
    @GetMapping("/corpus")
    fun corpus(): List<CorpusSummary> = market.corpusSummary()

    /**
     * Whether the corpus refreshes itself, and when it next will.
     *
     * Read from the same properties the scheduler is gated on rather than from a copy, so the page
     * cannot claim a schedule that is switched off. The next run is derived from the cron on every
     * request: caching it would let the answer outlive the run it describes.
     */
    @GetMapping("/ingestion")
    fun ingestion(): IngestionSchedule {
        val scheduled = properties.enabled
        return IngestionSchedule(
            scheduled = scheduled,
            cron = properties.cron.takeIf { scheduled },
            // Parsed defensively: a cron the scheduler rejected would have failed at startup, but a
            // dashboard is the wrong place to turn a configuration typo into a 500 on every load.
            nextPollAt = if (scheduled) nextRun(properties.cron) else null,
            lastPolledAt = market.corpusSummary().mapNotNull { it.lastSeenAt }.maxOrNull(),
        )
    }

    /**
     * Runs a poll now. Idempotent by construction -- offers already stored are refreshed, not
     * duplicated -- so triggering it twice costs two requests to the source and nothing else.
     */
    @PostMapping("/ingest")
    fun ingest(): IngestionReport = market.ingest()

    /**
     * Copies one corpus listing into the offer list. `201` for an offer that did not exist, `200`
     * when its text was already stored, matching what `POST /api/offers` does with a re-paste -
     * both are successes and the flag on the body is what tells them apart.
     *
     * One id in the path, and no batch form: see [MarketPromotion].
     */
    @PostMapping("/offers/{id}/promote")
    fun promote(@PathVariable id: Long): ResponseEntity<PromotedOffer> {
        val promoted = promotion.promote(id)
        val status = if (promoted.deduplicated) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(promoted)
    }

    /**
     * `409` rather than `404` or `500`: the listing exists and the request was well formed, and
     * what was refused is the copy. The detail names what to do about it, because for most rows
     * the answer is "re-poll" and for a delisted one there is no answer at all.
     */
    @ExceptionHandler(OfferNotPromotableException::class)
    fun handleNotPromotable(exception: OfferNotPromotableException): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.message!!).apply {
                title = "Offer cannot be promoted"
                setProperty("marketOfferId", exception.marketOfferId)
            }
        )

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMissing(exception: NoSuchElementException): Map<String, String?> =
        mapOf("error" to exception.message)

    private fun nextRun(cron: String) =
        runCatching { CronExpression.parse(cron).next(ZonedDateTime.now())?.toInstant() }.getOrNull()
}
