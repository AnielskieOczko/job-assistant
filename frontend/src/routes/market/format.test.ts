import { describe, expect, it } from 'vitest'
import type { SalaryBand, SalaryGroup } from '@/api/types'
import {
  bandLabel,
  byOffers,
  coveredBy,
  dominantLevel,
  employmentLabel,
  formatAmount,
  formatBand,
  percent,
  plural,
} from './format'

const band = (overrides: Partial<SalaryBand> = {}): SalaryBand => ({
  offers: 12,
  medianFrom: '18000',
  medianTo: '24000',
  currency: 'PLN',
  period: 'Month',
  employmentType: 'B2B',
  ...overrides,
})

describe('formatAmount', () => {
  it('returns null for an absent amount rather than formatting nothing', () => {
    expect(formatAmount(null)).toBeNull()
    expect(formatAmount(undefined)).toBeNull()
  })

  /** The guard that stops a malformed `BigDecimal` string rendering as the literal `NaN`. */
  it('returns null rather than NaN when the amount does not parse', () => {
    expect(formatAmount('not a number')).toBeNull()
    expect(formatAmount('')).not.toBe('NaN')
  })

  it('formats an amount without fraction digits, grouped for the ambient locale', () => {
    expect(formatAmount('18000')).toMatch(/^18\D?000$/)
    expect(formatAmount('18000.75')).toMatch(/^18\D?001$/)
  })

  it('formats a reported zero rather than dropping it', () => {
    expect(formatAmount('0')).toBe('0')
  })
})

describe('formatBand', () => {
  it('renders the stated floor and ceiling with the currency and the period', () => {
    const rendered = formatBand(band())
    expect(rendered).toContain('PLN')
    expect(rendered).toContain('/month')
    expect(rendered).toMatch(/18\D?000–24\D?000/)
  })

  /**
   * The failure this function exists to prevent: half a band read as a point estimate. It returns
   * null rather than a single figure, and never a midpoint between the two.
   */
  it('returns null when only one end of the band is known', () => {
    expect(formatBand(band({ medianTo: null }))).toBeNull()
    expect(formatBand(band({ medianFrom: null }))).toBeNull()
  })

  it('returns null when neither end is known', () => {
    expect(formatBand(band({ medianFrom: null, medianTo: null }))).toBeNull()
  })

  it('leaves no stray separator when the currency or the period is unstated', () => {
    const rendered = formatBand(band({ currency: null, period: null }))
    expect(rendered).toMatch(/^18\D?000–24\D?000$/)
  })
})

describe('employmentLabel', () => {
  it('names an unstated contract type rather than leaving a gap', () => {
    expect(employmentLabel(null)).toBe('Unstated')
    expect(employmentLabel(undefined)).toBe('Unstated')
  })

  it('prints the contract type as the source states it', () => {
    expect(employmentLabel('B2B')).toBe('B2B')
  })
})

describe('bandLabel', () => {
  it('appends the contract type the band was measured over', () => {
    expect(bandLabel(band())).toMatch(/ B2B$/)
  })

  it('is null when the band itself is not renderable', () => {
    expect(bandLabel(band({ medianTo: null }))).toBeNull()
  })

  it('says the contract type is unstated rather than rendering a trailing space', () => {
    expect(bandLabel(band({ employmentType: null }))).toMatch(/ Unstated$/)
  })
})

describe('byOffers', () => {
  const group = (employmentType: string, offers: number) =>
    ({ employmentType, offers }) as SalaryGroup

  it('puts the contract type the scope actually uses first', () => {
    const sorted = [group('UoP', 40), group('B2B', 120), group('UZ', 3)].sort(byOffers)
    expect(sorted.map((g) => g.employmentType)).toEqual(['B2B', 'UoP', 'UZ'])
  })
})

describe('dominantLevel', () => {
  it('returns null for an empty mix rather than a level nobody asked for', () => {
    expect(dominantLevel({})).toBeNull()
  })

  it('returns null when the mix sums to zero', () => {
    expect(dominantLevel({ BASIC: 0, EXPERT: 0 })).toBeNull()
  })

  /**
   * The denominator is the sum of the mix, not a count from another query — so the share is
   * always wrong against the mix rather than silently right against something else.
   */
  it('reports the most-asked level with the denominator it was measured over', () => {
    expect(dominantLevel({ BASIC: 2, ADVANCED: 7, EXPERT: 1 })).toEqual({
      level: 'ADVANCED',
      count: 7,
      total: 10,
      share: 0.7,
    })
  })

  it('reports a sole level as the whole of the mix', () => {
    expect(dominantLevel({ NICE_TO_HAVE: 5 })).toEqual({
      level: 'NICE_TO_HAVE',
      count: 5,
      total: 5,
      share: 1,
    })
  })
})

describe('percent', () => {
  it('rounds a ratio to whole percent', () => {
    expect(percent(0.7)).toBe('70%')
    expect(percent(0.335)).toBe('34%')
  })

  it('renders the ends of the range without decimals', () => {
    expect(percent(0)).toBe('0%')
    expect(percent(1)).toBe('100%')
  })
})

describe('plural', () => {
  it('uses the singular for exactly one', () => {
    expect(plural(1, 'offer')).toBe('1 offer')
  })

  it('uses the plural for zero and for many', () => {
    expect(plural(0, 'offer')).toBe('0 offers')
    expect(plural(2, 'offer')).toBe('2 offers')
  })

  it('takes an irregular plural rather than appending an s', () => {
    expect(plural(3, 'company', 'companies')).toBe('3 companies')
  })
})

describe('coveredBy', () => {
  /** A directly-held skill is covered by itself; printing "Java, via Java" adds no information. */
  it('is null when a skill covers itself', () => {
    expect(coveredBy({ skillName: 'Java', coveredBySkillName: 'Java' })).toBeNull()
  })

  it('is null when nothing covers the skill', () => {
    expect(coveredBy({ skillName: 'Kubernetes', coveredBySkillName: null })).toBeNull()
  })

  /** The sentence that stops an amber badge being a mystery: "Partial — via Docker". */
  it('names the other skill that earned the verdict', () => {
    expect(coveredBy({ skillName: 'Kubernetes', coveredBySkillName: 'Docker' })).toBe('Docker')
  })
})
