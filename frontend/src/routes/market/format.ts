import type { MarketSkillLevel, SalaryBand, SalaryGroup } from '@/api/types'

/**
 * Formatting for the market dashboard.
 *
 * Salary arrives as a `BigDecimal` serialised to a string. It is parsed here **only to format it**
 * — nothing on this screen does arithmetic on money, because every statistic was already computed
 * in SQL. If a number ever needs deriving from these, derive it server-side rather than reaching
 * for `Number()` twice.
 */

const AMOUNT = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })

export function formatAmount(raw: string | null | undefined): string | null {
  if (raw === null || raw === undefined) return null
  const value = Number(raw)
  return Number.isFinite(value) ? AMOUNT.format(value) : null
}

/** `Month` -> `/month`. The source states one period; it is still printed, never assumed. */
const formatPeriod = (period: string | null) => (period ? `/${period.toLowerCase()}` : '')

/**
 * The band, never a midpoint.
 *
 * A midpoint between a stated floor and a stated ceiling is a number no employer offered, and
 * `salary_from` is bucketed hard enough by the source that the midpoint would mostly be noise.
 * Returns null rather than a partial band: half a band read as a point estimate is the failure
 * this function exists to prevent.
 */
export function formatBand(band: {
  medianFrom: string | null
  medianTo: string | null
  currency: string | null
  period: string | null
}): string | null {
  const from = formatAmount(band.medianFrom)
  const to = formatAmount(band.medianTo)
  if (!from || !to) return null
  return `${from}–${to} ${band.currency ?? ''}${formatPeriod(band.period)}`.trim()
}

/** How the source names a contract type. Printed as given — regrouping it would need saying. */
export const employmentLabel = (type: string | null | undefined) => type ?? 'Unstated'

export function bandLabel(band: SalaryBand): string | null {
  const formatted = formatBand(band)
  return formatted ? `${formatted} ${employmentLabel(band.employmentType)}` : null
}

/** Largest group first: the default tile is whichever contract type the scope actually uses. */
export const byOffers = (a: SalaryGroup, b: SalaryGroup) => b.offers - a.offers

export const MARKET_LEVEL_LABELS: Record<MarketSkillLevel, string> = {
  BASIC: 'Basic',
  ADVANCED: 'Advanced',
  EXPERT: 'Expert',
  NICE_TO_HAVE: 'Nice to have',
  UNKNOWN: 'Unstated',
}

/**
 * The most-asked level and its share, with the denominator it was measured over.
 *
 * The denominator is the **sum of the mix**, not `DemandEntry.offers`: they should agree, and if
 * they ever stop agreeing the share must be wrong against the mix rather than silently right
 * against a number from a different query.
 */
export function dominantLevel(levelMix: Partial<Record<MarketSkillLevel, number>>) {
  const entries = Object.entries(levelMix) as [MarketSkillLevel, number][]
  const total = entries.reduce((sum, [, count]) => sum + count, 0)
  if (total === 0) return null

  const [level, count] = entries.reduce((best, entry) => (entry[1] > best[1] ? entry : best))
  return { level, count, total, share: count / total }
}

export const percent = (ratio: number) => `${Math.round(ratio * 100)}%`

export const plural = (count: number, one: string, many = `${one}s`) =>
  `${count.toLocaleString()} ${count === 1 ? one : many}`

/**
 * The held skill worth naming beside a coverage verdict, or null.
 *
 * A directly-held skill is covered by itself, so the server answers "Java, via Java". Printing
 * that adds a word and no information, and it drowns the case the provenance exists for: "Partial
 * — via Docker" beside Kubernetes is the sentence that stops an amber badge being a mystery.
 */
export const coveredBy = (entry: {
  skillName: string
  coveredBySkillName: string | null
}): string | null =>
  entry.coveredBySkillName && entry.coveredBySkillName !== entry.skillName
    ? entry.coveredBySkillName
    : null
