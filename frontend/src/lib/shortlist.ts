import type { OfferScore, ShortlistEntry } from '@/api/types'

/** How the offer list is ordered. Both orders are total, so neither reshuffles between renders. */
export const OFFER_SORTS = ['match', 'newest'] as const
export type OfferSort = (typeof OFFER_SORTS)[number]

export const OFFER_SORT_LABELS: Record<OfferSort, string> = {
  match: 'Best match',
  newest: 'Newest first',
}

/**
 * Orders the shortlist, best match first.
 *
 * Mirrors the server's `ShortlistOrder` so the two cannot disagree, and exists on this side because
 * the sort control has to reorder rows the client already holds without a refetch.
 *
 * Two rules carry the weight. An unscored offer sorts **after** every scored one and never as a
 * zero: never measured and measured at zero are different facts. And the comparison falls through
 * to the offer id, so equally matched offers hold a fixed order — without that last step the list
 * is free to reshuffle on every render, which is the same trap `CoverageStatus.UNMET_FIRST`
 * documents on the backend.
 */
export function byMatch(a: ShortlistEntry, b: ShortlistEntry): number {
  const byScore = rank(b) - rank(a)
  return byScore !== 0 ? byScore : b.offer.id - a.offer.id
}

/** Newest first, with the same id tie-break: two offers pasted in the same second must not swap. */
export function byNewest(a: ShortlistEntry, b: ShortlistEntry): number {
  const byDate = b.offer.createdAt.localeCompare(a.offer.createdAt)
  return byDate !== 0 ? byDate : b.offer.id - a.offer.id
}

export function comparatorFor(sort: OfferSort): (a: ShortlistEntry, b: ShortlistEntry) => number {
  return sort === 'match' ? byMatch : byNewest
}

/**
 * `-1` sorts unscored entries below every real score without inventing one for them. Scores are
 * ratios in 0..1, so no measured offer can reach it, and the value never leaves this comparison.
 */
function rank(entry: ShortlistEntry): number {
  return entry.score === null ? -1 : entry.score.matchScore
}

/**
 * The score as a whole-percent label, or null when there is none.
 *
 * Null rather than `'0%'` or `'—'`, so the caller decides how "never measured" renders and cannot
 * accidentally print it as a measurement. Same rule as `sentDocumentsLabel`.
 */
export function matchScoreLabel(score: OfferScore | null): string | null {
  return score === null ? null : `${Math.round(score.matchScore * 100)}%`
}

/**
 * A sentence for the ranked list's denominator.
 *
 * A shortlist of ten offers over three analyses is a ranking of three, and a screen that shows only
 * the rows cannot say that. Returns null when every offer is scored — there is then no shortfall
 * worth a caveat, and a line stating the obvious is a line the reader learns to skip.
 */
export function scoredCoverageNote(scored: number, total: number): string | null {
  if (total === 0 || scored === total) return null
  if (scored === 0) return `None of these ${total} offers has been analysed yet, so none is scored.`
  return `${scored} of ${total} offers are scored; the rest have not been analysed against this profile.`
}
