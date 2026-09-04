package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.OfferScore
import com.jankowski.rafal.jobassistant.analysis.ScoringRule
import com.jankowski.rafal.jobassistant.analysis.ShortlistEntry
import com.jankowski.rafal.jobassistant.offer.Application
import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.JobOffer
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The ranking rule, asserted without a database. Fast tier: it is a comparator over data classes.
 */
internal class ShortlistOrderTest {

    @Test
    fun `ranks by score, best first`() {
        val ranked = listOf(entry(1, 0.25), entry(2, 0.90), entry(3, 0.50)).sortedWith(ShortlistOrder)

        assertEquals(listOf(2L, 3L, 1L), ranked.map { it.offer.id })
    }

    @Test
    fun `an unscored offer sorts last and never as a zero`() {
        // 0.0 is a measured result and outranks "never measured"; the two must not collapse.
        val ranked = listOf(entry(1, null), entry(2, 0.0), entry(3, null), entry(4, 0.4))
            .sortedWith(ShortlistOrder)

        assertEquals(listOf(4L, 2L, 3L, 1L), ranked.map { it.offer.id })
    }

    /**
     * The property the tie-break exists for. Without it, equal scores keep whatever order the input
     * happened to have, and two requests over the same rows can disagree.
     */
    @Test
    fun `equal scores order the same way whatever order they arrive in`() {
        val entries = listOf(entry(1, 0.5), entry(2, 0.5), entry(3, 0.5), entry(4, 0.5))

        val fromEveryPermutation = permutations(entries).map { permuted ->
            permuted.sortedWith(ShortlistOrder).map { it.offer.id }
        }.toSet()

        assertEquals(setOf(listOf(4L, 3L, 2L, 1L)), fromEveryPermutation)
    }

    @Test
    fun `unscored offers are also totally ordered among themselves`() {
        val ranked = listOf(entry(2, null), entry(5, null), entry(1, null)).sortedWith(ShortlistOrder)

        assertEquals(listOf(5L, 2L, 1L), ranked.map { it.offer.id })
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) listOf(items)
        else items.flatMap { head ->
            permutations(items - head).map { tail -> listOf(head) + tail }
        }

    private fun entry(offerId: Long, score: Double?) = ShortlistEntry(
        offer = JobOffer(
            id = offerId,
            contentHash = "hash-$offerId",
            rawText = "Offer $offerId",
            createdAt = Instant.EPOCH,
        ),
        application = Application(
            id = offerId,
            offerId = offerId,
            status = ApplicationStatus.SAVED,
            statusChangedAt = Instant.EPOCH,
        ),
        score = score?.let {
            OfferScore(
                analysisId = offerId,
                matchScore = it,
                scoringRule = ScoringRule.V2_SOFT_EXCLUDED,
                completedAt = Instant.EPOCH,
            )
        },
    )
}
