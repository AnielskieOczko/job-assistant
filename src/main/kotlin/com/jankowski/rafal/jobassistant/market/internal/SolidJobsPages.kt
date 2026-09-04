package com.jankowski.rafal.jobassistant.market.internal

import tools.jackson.databind.json.JsonMapper

/**
 * Parses a solid.jobs page while keeping each offer's own JSON.
 *
 * Two passes over one tree rather than a straight `readValue`, because the corpus wants both
 * readings of the same bytes: the mapped fields the columns are written from, and the untouched
 * text `market_offer.payload` is supposed to hold. V14 asked for the second and got a
 * re-serialisation of the first, which silently dropped every field [SolidJobsOffer] does not
 * model - and the posting prose was one of them.
 *
 * A pure function over a string, so the fast tier can test it without a network or a container.
 */
internal object SolidJobsPages {

    fun parse(json: String, mapper: JsonMapper): SolidJobsPage {
        val root = mapper.readTree(json)
        val page = mapper.treeToValue(root, SolidJobsPage::class.java)
        val nodes = root["jobs"]?.takeIf { it.isArray } ?: return page

        // Positional, because both readings walk the same array in the same order. A mapped offer
        // with no node behind it cannot happen; guarding on the size keeps a malformed page from
        // throwing where it would otherwise just have fewer offers.
        return page.copy(
            jobs = page.jobs.mapIndexed { index, offer ->
                if (index < nodes.size()) offer.copy(raw = nodes[index].toString()) else offer
            }
        )
    }
}
