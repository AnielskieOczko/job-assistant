import { describe, expect, it } from 'vitest'
import { bucketLabel, coverage, share } from './format'

describe('bucketLabel', () => {
  /**
   * The rule the module states: a label never claims more precision than its bucket carries. A
   * monthly bucket labelled with a day of the month invites reading it as that day's spend.
   */
  it('omits the day of the month from a monthly bucket', () => {
    const label = bucketLabel('2026-03-15', 'MONTH')
    expect(label).toContain('2026')
    expect(label).not.toContain('15')
  })

  it('names the day a weekly bucket starts on, and says so', () => {
    const label = bucketLabel('2026-03-15', 'WEEK')
    expect(label).toMatch(/^Week of /)
    expect(label).toContain('15')
  })

  it('names the day for a daily bucket, without the week prefix', () => {
    const label = bucketLabel('2026-03-15', 'DAY')
    expect(label).toContain('15')
    expect(label).not.toContain('Week of')
  })

  /**
   * The dates are calendar days from a SQL rollup, not instants. Read in a zone behind UTC, the
   * first of a month would fall back into the previous one and mislabel the whole bucket.
   */
  it('reads the date as a calendar day, so the first of a month is not the previous month', () => {
    expect(bucketLabel('2026-01-01', 'MONTH')).toContain('2026')
    expect(bucketLabel('2026-01-01', 'DAY')).toContain('1')
  })
})

describe('coverage', () => {
  it('says there is nothing to report rather than dividing by zero', () => {
    expect(coverage(0, 0)).toBe('no calls yet')
  })

  it('says so plainly when every call reported a price', () => {
    expect(coverage(11, 11)).toBe('all 11 calls reported a price')
  })

  /** Never a rate without its denominator: `9 of 11` and `9 of 900` are different facts. */
  it('names the denominator when some calls went unpriced', () => {
    expect(coverage(9, 11)).toBe('9 of 11 calls reported a price')
  })

  it('reports zero priced calls as a fraction rather than as no calls at all', () => {
    expect(coverage(0, 4)).toBe('0 of 4 calls reported a price')
  })
})

describe('share', () => {
  it('returns null rather than a percentage when there is no denominator', () => {
    expect(share(0, 0)).toBeNull()
    expect(share(3, -1)).toBeNull()
  })

  it('rounds a share to whole percent', () => {
    expect(share(1, 3)).toBe('33%')
    expect(share(2, 3)).toBe('67%')
  })

  it('renders a whole and an empty share without decimals', () => {
    expect(share(4, 4)).toBe('100%')
    expect(share(0, 4)).toBe('0%')
  })
})
