/** Formatting helpers shared across screens. All API timestamps are ISO-8601 strings. */

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

const RELATIVE = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31_536_000_000],
  ['month', 2_592_000_000],
  ['day', 86_400_000],
  ['hour', 3_600_000],
  ['minute', 60_000],
]

export function formatRelative(iso: string | null | undefined): string {
  if (!iso) return '—'
  const delta = new Date(iso).getTime() - Date.now()
  for (const [unit, ms] of UNITS) {
    if (Math.abs(delta) >= ms) return RELATIVE.format(Math.round(delta / ms), unit)
  }
  return 'just now'
}

/** `2021-03-01` + null -> "Mar 2021 — Present". Matches how the CV template reads. */
export function formatPeriod(startedOn: string | null, endedOn: string | null): string {
  const month = (iso: string) =>
    new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short' })
  if (!startedOn) return endedOn ? month(endedOn) : '—'
  return `${month(startedOn)} — ${endedOn ? month(endedOn) : 'Present'}`
}

/**
 * `issuedOn` + `expiresOn` for a credential. Unlike `formatPeriod`, a missing `expiresOn` is not
 * rendered as "Present" - most credentials never expire, so silence reads truer than borrowing the
 * employment-history idiom.
 */
export function formatCredentialPeriod(issuedOn: string | null, expiresOn: string | null): string {
  const month = (iso: string) =>
    new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short' })
  if (!issuedOn && !expiresOn) return ''
  if (!expiresOn) return `Issued ${month(issuedOn!)}`
  if (!issuedOn) return `Expires ${month(expiresOn)}`
  return `Issued ${month(issuedOn)} · Expires ${month(expiresOn)}`
}

export function formatDuration(ms: number | null): string {
  if (ms === null) return '—'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

/**
 * A single call's price, which is routinely a fraction of a cent.
 *
 * Rounded to cents a real call reads as `$0.00`, which is indistinguishable from free and from
 * unpriced — so small values keep enough decimals to stay a number you can compare. Null is `—`,
 * never `$0.00`: a provider that reported no price did not report a price of zero.
 */
export function formatCallCost(usd: number | null | undefined): string {
  if (usd === null || usd === undefined) return '—'
  if (usd === 0) return '$0'
  if (usd < 0.01) return `$${usd.toPrecision(3)}`
  return `$${usd.toFixed(4)}`
}

/** A total. Big enough to be worth cents, small enough that the first one will not be. */
export function formatSpend(usd: number | null | undefined): string {
  if (usd === null || usd === undefined) return '—'
  if (usd > 0 && usd < 0.01) return `$${usd.toPrecision(2)}`
  return `$${usd.toFixed(2)}`
}
