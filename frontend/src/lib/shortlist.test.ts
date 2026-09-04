import { describe, expect, it } from 'vitest'
import type { ShortlistEntry } from '@/api/types'
import { byMatch, byNewest, matchScoreLabel, scoredCoverageNote } from './shortlist'

function entry(id: number, score: number | null, createdAt = '2026-01-01T00:00:00Z'): ShortlistEntry {
  return {
    offer: {
      id,
      contentHash: `h${id}`,
      rawText: `Offer ${id}`,
      sourceUrl: null,
      title: `Offer ${id}`,
      company: null,
      seniority: null,
      detectedLanguage: null,
      createdAt,
      origin: 'PASTED',
      marketOfferId: null,
    },
    application: {
      id,
      offerId: id,
      status: 'SAVED',
      statusChangedAt: createdAt,
      appliedOn: null,
      notes: null,
      sentCvDocumentId: null,
      sentCoverLetterDocumentId: null,
    },
    score:
      score === null
        ? null
        : { analysisId: id, matchScore: score, scoringRule: 'V2_SOFT_EXCLUDED', completedAt: createdAt },
  }
}

const ids = (entries: ShortlistEntry[]) => entries.map((e) => e.offer.id)

describe('byMatch', () => {
  it('ranks the best match first', () => {
    const sorted = [entry(1, 0.25), entry(2, 0.9), entry(3, 0.5)].sort(byMatch)

    expect(ids(sorted)).toEqual([2, 3, 1])
  })

  it('sorts an unscored offer last, and never as a zero', () => {
    const sorted = [entry(1, null), entry(2, 0), entry(3, 0.4)].sort(byMatch)

    // A measured 0% outranks "never measured": the two are different facts.
    expect(ids(sorted)).toEqual([3, 2, 1])
  })

  it('gives equally scored offers the same order whatever order they arrive in', () => {
    const tied = [entry(1, 0.5), entry(2, 0.5), entry(3, 0.5), entry(4, 0.5)]

    for (const permuted of permutations(tied)) {
      expect(ids([...permuted].sort(byMatch))).toEqual([4, 3, 2, 1])
    }
  })
})

describe('byNewest', () => {
  it('sorts newest first', () => {
    const sorted = [
      entry(1, null, '2026-01-01T00:00:00Z'),
      entry(2, null, '2026-03-01T00:00:00Z'),
      entry(3, null, '2026-02-01T00:00:00Z'),
    ].sort(byNewest)

    expect(ids(sorted)).toEqual([2, 3, 1])
  })

  it('breaks a tie on the id so offers pasted at the same moment cannot swap', () => {
    const sameInstant = [entry(1, null), entry(3, null), entry(2, null)]

    for (const permuted of permutations(sameInstant)) {
      expect(ids([...permuted].sort(byNewest))).toEqual([3, 2, 1])
    }
  })
})

describe('matchScoreLabel', () => {
  it('renders a whole percent', () => {
    expect(matchScoreLabel(entry(1, 0.666).score)).toBe('67%')
  })

  it('is null for an unscored offer rather than a zero', () => {
    expect(matchScoreLabel(entry(1, null).score)).toBeNull()
  })

  it('renders a measured zero as a zero', () => {
    expect(matchScoreLabel(entry(1, 0).score)).toBe('0%')
  })
})

describe('scoredCoverageNote', () => {
  it('names the shortfall when only some offers are scored', () => {
    expect(scoredCoverageNote(3, 10)).toContain('3 of 10')
  })

  it('says so when nothing is scored at all', () => {
    expect(scoredCoverageNote(0, 4)).toContain('None')
  })

  it('is silent when every offer is scored', () => {
    expect(scoredCoverageNote(4, 4)).toBeNull()
  })

  it('is silent for an empty list', () => {
    expect(scoredCoverageNote(0, 0)).toBeNull()
  })
})

function permutations<T>(items: T[]): T[][] {
  if (items.length <= 1) return [items]
  return items.flatMap((head, index) =>
    permutations([...items.slice(0, index), ...items.slice(index + 1)]).map((tail) => [head, ...tail]),
  )
}
