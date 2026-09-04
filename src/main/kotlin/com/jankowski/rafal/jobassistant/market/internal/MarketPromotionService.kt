package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.MarketPromotion
import com.jankowski.rafal.jobassistant.market.MarketSalary
import com.jankowski.rafal.jobassistant.market.OfferNotPromotableException
import com.jankowski.rafal.jobassistant.market.PromotedOffer
import com.jankowski.rafal.jobassistant.offer.OfferService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Hands one corpus listing to the offer module's paste path.
 *
 * This is the whole of the `market -> offer` edge, and it is one call. `market` may depend on
 * `offer` and not the other way round: `offer` depends on nothing today, and putting a scheduler
 * and an HTTP client into its transitive closure is the mistake ADR-0003 avoided for `catalog`.
 * See `docs/adr/0004-promotion-crosses-from-market-to-offer.md`.
 *
 * Ingestion still does not know this class exists, which is the property worth keeping: pulling
 * offers in and choosing to apply to one are different jobs, and only the second one is about the
 * candidate.
 */
@Service
internal class MarketPromotionService(
    private val repository: MarketOfferRepository,
    private val offers: OfferService,
) : MarketPromotion {

    @Transactional
    override fun promote(marketOfferId: Long): PromotedOffer {
        val listing = repository.findForPromotion(marketOfferId)
            ?: throw NoSuchElementException("No market offer $marketOfferId")

        val prose = listing.description?.let(HtmlPosting::toText)?.takeIf { it.isNotBlank() }
            ?: throw OfferNotPromotableException(
                marketOfferId,
                "the corpus holds no posting text for it. Offers ingested before the description " +
                    "was stored gain one on the next poll, unless the listing has been delisted.",
            )

        val promoted = offers.promoteFromMarket(
            rawText = offerText(listing, prose),
            sourceUrl = listing.url,
            marketOfferId = marketOfferId,
        )

        return PromotedOffer(
            offerId = promoted.offer.id,
            marketOfferId = marketOfferId,
            deduplicated = promoted.deduplicated,
        )
    }

    /**
     * The stored offer text: a short header of what the listing states, then the employer's prose.
     *
     * **The listed skills are deliberately left out.** They are the corpus's own structured field,
     * already resolved against the catalog, and feeding them back in would have the extractor read
     * our resolution and return it - making the resulting `matchScore` the market dashboard's
     * coverage number under a second name, which `CLAUDE.md` names as the reason `market` must not
     * depend on `analysis`. The prose is what an employer wrote and is what a pasted offer would
     * have carried, so a promoted offer scores comparably with one pasted by hand.
     */
    private fun offerText(listing: PromotableOffer, prose: String): String {
        val header = buildList {
            add(listing.title)
            listing.company?.takeIf { it.isNotBlank() }?.let { add(it) }
            workMode(listing)?.let { add(it) }
            listing.experienceLevel?.takeIf { it.isNotBlank() }?.let { add("Experience level: $it") }
            listing.contractTime?.takeIf { it.isNotBlank() }?.let { add("Contract: $it") }
            salaryLine(listing.salary)?.let { add(it) }
            listing.url?.takeIf { it.isNotBlank() }?.let { add("Source: $it (${listing.source})") }
        }

        return (header + listOf("", prose)).joinToString("\n")
    }

    private fun workMode(listing: PromotableOffer): String? {
        val mode = when {
            listing.isRemote -> "Remote"
            listing.isHybrid -> "Hybrid"
            else -> null
        }
        val where = listing.locations.filter { it.isNotBlank() }.joinToString(", ")

        return listOfNotNull(mode, where.takeIf { it.isNotBlank() })
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
    }

    /** Null rather than a partial band: half a salary range reads as a stated figure and is not one. */
    private fun salaryLine(salary: MarketSalary): String? {
        val from = salary.from ?: return null
        val to = salary.to ?: return null
        val currency = salary.currency.orEmpty()
        val period = salary.period?.let { " / $it" }.orEmpty()
        val employment = salary.employmentType?.let { " ($it)" }.orEmpty()

        return "Salary: $from-$to $currency$period$employment".replace("  ", " ")
    }
}
