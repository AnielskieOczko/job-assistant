import { describe, expect, it } from 'vitest'
import {
  formatCallCost,
  formatCredentialPeriod,
  formatDate,
  formatDateTime,
  formatDuration,
  formatPeriod,
  formatRelative,
  formatSpend,
} from './format'

/**
 * These formatters render through `Intl` with the ambient locale, so the exact wording of a month
 * is the runtime's business and not this suite's. What is asserted here is the part the module
 * decides: which inputs mean "absent", how absence renders, and what each string claims.
 */

describe('formatDateTime', () => {
  it('renders an absent timestamp as a dash rather than an epoch or a blank cell', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
    expect(formatDateTime('')).toBe('—')
  })

  it('carries a clock time, which is what distinguishes it from formatDate', () => {
    const rendered = formatDateTime('2026-03-15T09:41:00Z')
    expect(rendered).toContain('2026')
    expect(rendered).toMatch(/\d{2}:\d{2}/)
  })
})

describe('formatDate', () => {
  it('renders an absent date as a dash', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })

  it('carries the calendar day and no clock time', () => {
    const rendered = formatDate('2026-03-15T09:41:00Z')
    expect(rendered).toContain('2026')
    expect(rendered).toContain('15')
    expect(rendered).not.toMatch(/\d{2}:\d{2}/)
  })
})

describe('formatRelative', () => {
  it('renders an absent timestamp as a dash', () => {
    expect(formatRelative(null)).toBe('—')
    expect(formatRelative(undefined)).toBe('—')
  })

  it('says "just now" under a minute rather than rounding to zero of some unit', () => {
    expect(formatRelative(new Date(Date.now() - 5_000).toISOString())).toBe('just now')
  })

  it('reaches for a coarser unit once one is available', () => {
    const twoHoursAgo = new Date(Date.now() - 2 * 3_600_000).toISOString()
    expect(formatRelative(twoHoursAgo)).not.toBe('just now')
  })
})

describe('formatPeriod', () => {
  it('reads a missing end date as an ongoing role', () => {
    expect(formatPeriod('2021-03-01', null)).toContain('Present')
  })

  it('does not say "Present" for a role that ended', () => {
    const rendered = formatPeriod('2021-03-01', '2023-06-01')
    expect(rendered).not.toContain('Present')
    expect(rendered).toContain('2021')
    expect(rendered).toContain('2023')
  })

  it('renders a single month when only the end date is known', () => {
    const rendered = formatPeriod(null, '2023-06-01')
    expect(rendered).toContain('2023')
    expect(rendered).not.toContain('—')
  })

  it('renders a dash when neither date is known', () => {
    expect(formatPeriod(null, null)).toBe('—')
  })
})

describe('formatCredentialPeriod', () => {
  /**
   * The rule that makes this a separate function: most credentials never expire, so a missing
   * `expiresOn` must not borrow the employment idiom and read as "Present".
   */
  it('does not say "Present" for a credential with no expiry', () => {
    const rendered = formatCredentialPeriod('2024-01-15', null)
    expect(rendered).not.toContain('Present')
    expect(rendered).toContain('Issued')
    expect(rendered).toContain('2024')
  })

  it('names both dates when the credential expires', () => {
    const rendered = formatCredentialPeriod('2024-01-15', '2027-01-15')
    expect(rendered).toContain('Issued')
    expect(rendered).toContain('Expires')
  })

  it('names only the expiry when the issue date is unknown', () => {
    const rendered = formatCredentialPeriod(null, '2027-01-15')
    expect(rendered).toContain('Expires')
    expect(rendered).not.toContain('Issued')
  })

  /** Empty rather than a dash: a credential with no dates should render nothing at all. */
  it('renders nothing when neither date is known', () => {
    expect(formatCredentialPeriod(null, null)).toBe('')
  })
})

describe('formatDuration', () => {
  it('renders an unmeasured duration as a dash rather than as zero', () => {
    expect(formatDuration(null)).toBe('—')
  })

  it('renders a measured zero as zero, which is not the same as unmeasured', () => {
    expect(formatDuration(0)).toBe('0 ms')
  })

  it('stays in milliseconds below a second', () => {
    expect(formatDuration(999)).toBe('999 ms')
  })

  it('switches to seconds at a second, to one decimal', () => {
    expect(formatDuration(1000)).toBe('1.0 s')
    expect(formatDuration(1540)).toBe('1.5 s')
  })
})

describe('formatCallCost', () => {
  it('renders an unpriced call as a dash, never as free', () => {
    expect(formatCallCost(null)).toBe('—')
    expect(formatCallCost(undefined)).toBe('—')
  })

  it('renders a reported price of zero as zero, distinguishably from unpriced', () => {
    expect(formatCallCost(0)).toBe('$0')
  })

  /** A real call routinely costs a fraction of a cent; rounded to cents it would read as free. */
  it('keeps a fraction of a cent legible instead of rounding it to $0.00', () => {
    expect(formatCallCost(0.0000123)).toBe('$0.0000123')
    expect(formatCallCost(0.0000123)).not.toBe('$0.00')
  })

  it('rounds a cent or more to four decimals', () => {
    expect(formatCallCost(0.05)).toBe('$0.0500')
    expect(formatCallCost(1.23456789)).toBe('$1.2346')
  })
})

describe('formatSpend', () => {
  it('renders an absent total as a dash', () => {
    expect(formatSpend(null)).toBe('—')
    expect(formatSpend(undefined)).toBe('—')
  })

  it('renders a total of zero as $0.00 — nothing spent is a measurement', () => {
    expect(formatSpend(0)).toBe('$0.00')
  })

  it('keeps a sub-cent total from rounding away to nothing', () => {
    expect(formatSpend(0.004)).toBe('$0.0040')
  })

  it('renders an ordinary total to cents', () => {
    expect(formatSpend(12.5)).toBe('$12.50')
    expect(formatSpend(0.01)).toBe('$0.01')
  })
})
