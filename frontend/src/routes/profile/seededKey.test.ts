import { describe, expect, it } from 'vitest'
import { decideSeed } from './seededKey'

describe('decideSeed', () => {
  it('seeds on first open with a row', () => {
    expect(decideSeed(1, null)).toBe('seed')
  })

  it('seeds again when switching to a different row while open', () => {
    expect(decideSeed(2, 1)).toBe('seed')
  })

  it('does nothing while already seeded for the current row', () => {
    expect(decideSeed(1, 1)).toBe('none')
  })

  it('resets when the dialog closes after being seeded', () => {
    expect(decideSeed(null, 1)).toBe('reset')
  })

  it('does nothing while closed and already reset', () => {
    expect(decideSeed(null, null)).toBe('none')
  })

  /**
   * The actual bug this function exists to prevent: without the reset above, reopening the same
   * row would read as "already seeded" and skip repopulating, leaving a discarded edit on screen
   * as though it had been saved.
   */
  it('reseeds the same row after a close-then-reopen cycle', () => {
    let seeded: number | null = null
    expect(decideSeed(1, seeded)).toBe('seed')
    seeded = 1
    expect(decideSeed(null, seeded)).toBe('reset')
    seeded = null
    expect(decideSeed(1, seeded)).toBe('seed')
  })

  it("treats the 'new' sentinel as an ordinary key, not a special case", () => {
    expect(decideSeed('new', null)).toBe('seed')
    expect(decideSeed('new', 'new')).toBe('none')
    expect(decideSeed(null, 'new')).toBe('reset')
  })
})
