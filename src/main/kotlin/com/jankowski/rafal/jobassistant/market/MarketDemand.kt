package com.jankowski.rafal.jobassistant.market

/**
 * What the corpus asks for, for callers that rank rather than ingest.
 *
 * Separate from [MarketOfferService] because it is a different job: that interface pulls offers in,
 * this one answers questions about the offers already held. Keeping them apart lets the review
 * queue depend on the measure without depending on the scheduler and the HTTP client behind it.
 *
 * Nothing here reaches a model, and nothing here compares the corpus against a profile. The
 * comparison belongs to whoever is asking, because a stored one goes stale the moment the profile
 * is edited.
 */
interface MarketDemand {

    /**
     * How many **in-scope** corpus offers ask for each term the catalog cannot place, keyed by the
     * term's normalised form.
     *
     * Keyed on the normalised form rather than the raw name because that is the identity
     * `unmatched_term` is unique on, so a caller can join to the review queue without having to
     * decide for itself that "Power Apps" and "power apps" are one thing.
     *
     * "In scope" means the offer also lists at least one skill from [scopeSkills]. Only unresolved
     * mentions appear: a term the catalog already places is not queue business.
     */
    fun inScopeUnresolvedDemand(): Map<String, Int>

    /**
     * The configured scope, as canonical skill names the catalog actually resolved.
     *
     * Returned so a caller can *say* what it ranked by. A number labelled "in-scope demand" with no
     * statement of the scope is the same unfooted claim as a rate with no denominator, and a
     * misconfigured name silently dropping out is exactly the case that needs to be visible.
     */
    fun scopeSkills(): List<String>
}
