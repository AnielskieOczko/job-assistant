import type { SpendBucket } from '@/api/types'

/**
 * A bucket's label, at the resolution the bucket actually has.
 *
 * A monthly bucket labelled with a day of the month invites reading it as that day's spend, which
 * is the same class of mistake as a figure without its denominator — so the label never claims
 * more precision than the bucket carries.
 */
export function bucketLabel(isoDate: string, bucket: SpendBucket): string {
  const date = new Date(`${isoDate}T00:00:00Z`)
  if (bucket === 'MONTH') {
    return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', timeZone: 'UTC' })
  }
  const day = date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
  return bucket === 'WEEK' ? `Week of ${day}` : day
}

/** `9 of 11` rather than `82%`: at these sample sizes the fraction is the honest form. */
export function coverage(priced: number, total: number): string {
  if (total === 0) return 'no calls yet'
  if (priced === total) return `all ${total.toLocaleString()} calls reported a price`
  return `${priced.toLocaleString()} of ${total.toLocaleString()} calls reported a price`
}

/** Share of a total, or null when there is no denominator to divide by. */
export function share(part: number, whole: number): string | null {
  if (whole <= 0) return null
  return `${Math.round((part / whole) * 100)}%`
}
