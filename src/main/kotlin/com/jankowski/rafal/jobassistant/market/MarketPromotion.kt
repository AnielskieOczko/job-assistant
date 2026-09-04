package com.jankowski.rafal.jobassistant.market

/**
 * Copies one corpus listing into the candidate's own offer list.
 *
 * Separate from [MarketOfferService], which is ingestion, and from [MarketInsights], which is
 * counting. This is the one operation that crosses out of the corpus, and `CLAUDE.md` has always
 * specified its shape: *"A `market_offer` is not a `JobOffer` and must not become one … Saving one
 * for real is an explicit copy."* The copy is what lives here.
 *
 * **One at a time, and only when a human asks.** There is no bulk form and no scheduled form, and
 * adding either would recreate the failure the two tables exist to prevent: thousands of `SAVED`
 * applications nobody chose, and a silent change to what `AggregateGapReport.analysedOffers`
 * counts. The scheduled variant is declined outright in `docs/roadmap.md`.
 */
interface MarketPromotion {

    /**
     * Promotes one corpus offer, or returns the offer already stored when its text has been seen
     * before - by an earlier promotion or by an ordinary paste, which deduplicate against each
     * other because both go through the same content hash.
     *
     * @throws NoSuchElementException if no such corpus offer exists.
     * @throws OfferNotPromotableException if the row carries no posting text to promote.
     */
    fun promote(marketOfferId: Long): PromotedOffer
}

/**
 * What a promotion produced.
 *
 * [deduplicated] carries the same meaning as on `PastedOffer` and is reported rather than hidden:
 * a second click landing on the offer you already have is a success, and a UI that could not tell
 * the two apart would say "promoted" about something it did not create.
 */
data class PromotedOffer(
    val offerId: Long,
    val marketOfferId: Long,
    val deduplicated: Boolean,
)

/**
 * Raised when a corpus row cannot become an offer, which today means exactly one thing: it carries
 * no posting text.
 *
 * Every row ingested before `V28` is in this state, because the description solid.jobs serves was
 * being discarded at parse time. A re-poll fixes any listing still live; one already delisted
 * cannot be fixed at all, and refusing is the honest answer. The alternative - composing an offer
 * out of the structured fields - would hand the extractor our own resolved skill list and produce
 * a `matchScore` that is the market dashboard's coverage number wearing a different name.
 */
class OfferNotPromotableException(val marketOfferId: Long, val reason: String) :
    IllegalStateException("Market offer $marketOfferId cannot be promoted: $reason")
