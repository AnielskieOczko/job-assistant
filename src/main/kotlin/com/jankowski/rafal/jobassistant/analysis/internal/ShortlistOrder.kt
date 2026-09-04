package com.jankowski.rafal.jobassistant.analysis.internal

import com.jankowski.rafal.jobassistant.analysis.ShortlistEntry

/**
 * The order a shortlist is served in: best match first, unscored last, newest first within a tie.
 *
 * A **total** order, deliberately. Comparing on the score alone leaves two equally matched offers
 * free to swap between requests - the same trap `CoverageStatus.UNMET_FIRST` documents for the
 * market dashboard, where a page boundary then shows one row twice and the other never. The offer
 * id is the final tie-break because it is unique and stable; `createdAt` is neither.
 *
 * Unscored offers sort after every scored one rather than as a zero. An offer that was never
 * analysed has no score, and ranking it alongside one measured at 0% would say something about it
 * that nothing has measured.
 *
 * Pure and separate from the service so the ordering can be asserted without a database.
 */
internal object ShortlistOrder : Comparator<ShortlistEntry> {

    override fun compare(a: ShortlistEntry, b: ShortlistEntry): Int {
        val byScore = rank(b).compareTo(rank(a))
        return if (byScore != 0) byScore else b.offer.id.compareTo(a.offer.id)
    }

    /**
     * Sorts unscored entries below every real score without pretending they hold one.
     *
     * `matchScore` is a ratio in 0.0..1.0, so -1.0 is unreachable by any measured offer. The value
     * exists only inside this comparison and is never reported: [ShortlistEntry.score] stays null.
     */
    private fun rank(entry: ShortlistEntry): Double = entry.score?.matchScore ?: -1.0
}
