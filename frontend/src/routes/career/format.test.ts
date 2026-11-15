import { describe, expect, it } from 'vitest'
import { countReadyTopics, formatSeniority, nextProgressState, progressLabel } from './format'
import type { CareerPlan } from '@/api/types'

describe('career format helpers', () => {
  it('formats seniority levels for display', () => {
    expect(formatSeniority('INTERN')).toBe('Intern')
    expect(formatSeniority('JUNIOR')).toBe('Junior')
    expect(formatSeniority('REGULAR')).toBe('Regular')
    expect(formatSeniority('SENIOR')).toBe('Senior')
  })

  it('formats and advances skill progress states', () => {
    expect(progressLabel('NOT_STARTED')).toBe('Not started')
    expect(progressLabel('LEARNING')).toBe('Learning')
    expect(progressLabel('READY_TO_DECLARE')).toBe('Ready to declare')

    expect(nextProgressState('NOT_STARTED')).toBe('LEARNING')
    expect(nextProgressState('LEARNING')).toBe('READY_TO_DECLARE')
    expect(nextProgressState('READY_TO_DECLARE')).toBe('NOT_STARTED')
  })

  it('counts topics ready to declare', () => {
    const plan: CareerPlan = {
      id: 1,
      profileId: 1,
      profileRevision: 1,
      targetLevel: 'SENIOR',
      targetSkills: ['Java'],
      matchedOfferCount: 10,
      corpusOfferCount: 50,
      droppedTopicCount: 0,
      modelProfile: 'scripted',
      createdAt: '2026-11-15T10:00:00Z',
      topics: [
        {
          id: 10,
          skillId: 1,
          skillName: 'Kafka',
          priority: 1,
          targetLevel: 'SENIOR',
          why: 'Asked for',
          practiceProject: null,
          effortEstimate: '2 weeks',
          demandOffers: 8,
          demandRequiredOffers: 6,
          coverageStatus: 'MISSING',
          progressState: 'READY_TO_DECLARE',
        },
        {
          id: 11,
          skillId: 2,
          skillName: 'Docker',
          priority: 2,
          targetLevel: 'SENIOR',
          why: 'Containerization',
          practiceProject: null,
          effortEstimate: '1 week',
          demandOffers: 5,
          demandRequiredOffers: 3,
          coverageStatus: 'MISSING',
          progressState: 'LEARNING',
        },
      ],
    }

    expect(countReadyTopics(plan)).toBe(1)
  })
})
