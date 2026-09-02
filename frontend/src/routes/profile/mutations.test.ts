import { describe, expect, it } from 'vitest'
import { blankToNull, movedIds, swappedIds } from './mutations'

/**
 * The counterpart to `ProfileCrudHttpTest`'s "a partial reorder is a 409 rather than a silent
 * partial move". The backend refuses any reorder that does not name every id exactly once; these
 * two functions are what decides whether the request the candidate's click produces is refusable.
 * Every case therefore asserts the permutation property, not just the ordering.
 */

const items = (...ids: number[]) => ids.map((id) => ({ id }))

/** A reorder request must name every id of the collection exactly once, in some order. */
function expectPermutationOf(result: number[], source: { id: number }[]) {
  const ids = source.map((item) => item.id)
  expect(result).toHaveLength(ids.length)
  expect(new Set(result).size).toBe(result.length)
  expect([...result].sort()).toEqual([...ids].sort())
}

describe('movedIds', () => {
  const list = items(10, 20, 30, 40)

  it('moves an entry forward and shifts the ones it passes back', () => {
    const result = movedIds(list, 0, 2)
    expect(result).toEqual([20, 30, 10, 40])
    expectPermutationOf(result, list)
  })

  it('moves an entry backward and shifts the ones it passes forward', () => {
    const result = movedIds(list, 3, 1)
    expect(result).toEqual([10, 40, 20, 30])
    expectPermutationOf(result, list)
  })

  it('returns the order unchanged when an entry is moved to where it already is', () => {
    expect(movedIds(list, 2, 2)).toEqual([10, 20, 30, 40])
  })

  it('is a no-op past the end of the list, so a stale render cannot send a short list', () => {
    const result = movedIds(list, 0, 4)
    expect(result).toEqual([10, 20, 30, 40])
    expectPermutationOf(result, list)
  })

  it('is a no-op before the start of the list', () => {
    const result = movedIds(list, 2, -1)
    expect(result).toEqual([10, 20, 30, 40])
    expectPermutationOf(result, list)
  })

  it('leaves a single-entry collection alone', () => {
    expect(movedIds(items(7), 0, 0)).toEqual([7])
  })

  it('returns an empty list for an empty collection rather than throwing', () => {
    expect(movedIds([], 0, 0)).toEqual([])
  })
})

describe('swappedIds', () => {
  const list = items(10, 20, 30, 40, 50)

  it('swaps two adjacent entries', () => {
    const result = swappedIds(list, 20, 30)
    expect(result).toEqual([10, 30, 20, 40, 50])
    expectPermutationOf(result, list)
  })

  /**
   * The reason this function exists rather than `movedIds`. Skills are displayed grouped by
   * category, so the neighbour on screen may sit several places away in the flat list — and the
   * request still has to carry the whole flat list.
   */
  it('swaps neighbours in a displayed group that are not adjacent in the flat list', () => {
    const result = swappedIds(list, 10, 50)
    expect(result).toEqual([50, 20, 30, 40, 10])
    expectPermutationOf(result, list)
  })

  it('leaves the order unchanged when one id is not in the collection', () => {
    const result = swappedIds(list, 20, 999)
    expect(result).toEqual([10, 20, 30, 40, 50])
    expectPermutationOf(result, list)
  })

  it('leaves the order unchanged when neither id is in the collection', () => {
    const result = swappedIds(list, 998, 999)
    expect(result).toEqual([10, 20, 30, 40, 50])
    expectPermutationOf(result, list)
  })

  it('leaves the order unchanged when an id is swapped with itself', () => {
    const result = swappedIds(list, 30, 30)
    expect(result).toEqual([10, 20, 30, 40, 50])
    expectPermutationOf(result, list)
  })
})

describe('blankToNull', () => {
  it('reads an empty input as no value rather than as the empty string', () => {
    expect(blankToNull('')).toBeNull()
  })

  it('reads a whitespace-only input as no value', () => {
    expect(blankToNull('   \t\n ')).toBeNull()
  })

  it('trims the surrounding whitespace off a real value', () => {
    expect(blankToNull('  https://example.com  ')).toBe('https://example.com')
  })

  it('passes a normal value through unchanged', () => {
    expect(blankToNull('Kotlin')).toBe('Kotlin')
  })
})
