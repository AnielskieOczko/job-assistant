import { describe, expect, it } from 'vitest'
import { characterBudgetHint, formatCharCount, sectionLabel, sectionNeedsSubject } from './format'

describe('linkedin format helpers', () => {
  it('maps each LinkedIn section to a readable label', () => {
    expect(sectionLabel('HEADLINE')).toBe('Headline')
    expect(sectionLabel('ABOUT')).toBe('About summary')
    expect(sectionLabel('EXPERIENCE_ENTRY')).toBe('Experience entry')
    expect(sectionLabel('PROJECT_ENTRY')).toBe('Project entry')
    expect(sectionLabel('SKILL_OR_CREDENTIAL_POST')).toBe('Skill or credential post')
  })

  it('identifies sections that target an experience or project subject', () => {
    expect(sectionNeedsSubject('EXPERIENCE_ENTRY')).toBe(true)
    expect(sectionNeedsSubject('PROJECT_ENTRY')).toBe(true)
    expect(sectionNeedsSubject('HEADLINE')).toBe(false)
    expect(sectionNeedsSubject('ABOUT')).toBe(false)
    expect(sectionNeedsSubject('SKILL_OR_CREDENTIAL_POST')).toBe(false)
  })

  it('provides LinkedIn character limits and formats counts', () => {
    expect(characterBudgetHint('HEADLINE')).toBe(220)
    expect(characterBudgetHint('ABOUT')).toBe(2600)
    expect(formatCharCount('Hello', 'HEADLINE')).toBe('5 / 220 chars')
  })
})
