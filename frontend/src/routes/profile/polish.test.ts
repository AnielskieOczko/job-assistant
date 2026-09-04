import { describe, expect, it } from 'vitest'
import type { PolishSuggestion } from '@/api/types'
import { acceptedText, draftFor, flaggedIn, markTerms } from './polish'

const suggestion = (over: Partial<PolishSuggestion> = {}): PolishSuggestion => ({
  field: 'PROJECT_DESCRIPTION',
  original: 'a tool i built',
  suggestion: 'A tool that tailors a CV to an offer.',
  unheldSkills: [],
  modelProfile: 'openrouter',
  ...over,
})

describe('acceptedText', () => {
  it('is the trimmed draft when it changes something', () => {
    expect(acceptedText('a tool i built', '  A tool I built.  ')).toBe('A tool I built.')
  })

  it('refuses a blank draft, because deleting a field is not polishing it', () => {
    expect(acceptedText('a tool i built', '')).toBeNull()
    expect(acceptedText('a tool i built', '   ')).toBeNull()
  })

  it('refuses a draft equal to the original, because a write with no change still bumps the revision', () => {
    expect(acceptedText('a tool i built', 'a tool i built')).toBeNull()
    expect(acceptedText('  a tool i built  ', 'a tool i built')).toBeNull()
  })
})

describe('draftFor', () => {
  it('starts from the model text, which the candidate may then edit', () => {
    expect(draftFor(suggestion())).toBe('A tool that tailors a CV to an offer.')
  })

  it('leaves a suggestion that named an unheld skill fully editable rather than stripping it', () => {
    const flagged = suggestion({ suggestion: 'Deployed on Kubernetes.', unheldSkills: ['Kubernetes'] })

    expect(draftFor(flagged)).toBe('Deployed on Kubernetes.')
  })
})

describe('markTerms', () => {
  it('returns the text as one unflagged run when nothing was flagged', () => {
    expect(markTerms('A tool that tailors a CV.', [])).toEqual([
      { text: 'A tool that tailors a CV.', flagged: false },
    ])
  })

  it('marks a flagged term and leaves the rest alone', () => {
    expect(markTerms('Deployed on Kubernetes daily.', ['Kubernetes'])).toEqual([
      { text: 'Deployed on ', flagged: false },
      { text: 'Kubernetes', flagged: true },
      { text: ' daily.', flagged: false },
    ])
  })

  it('reassembles to exactly the input text', () => {
    const text = 'Kubernetes, Apache Kafka and Kubernetes again.'
    const joined = markTerms(text, ['Kubernetes', 'Apache Kafka'])
      .map((segment) => segment.text)
      .join('')

    expect(joined).toBe(text)
  })

  it('prefers the longer term, so a multi-word name is not cut in half', () => {
    const flagged = markTerms('Events over Apache Kafka.', ['Kafka', 'Apache Kafka'])
      .filter((segment) => segment.flagged)
      .map((segment) => segment.text)

    expect(flagged).toEqual(['Apache Kafka'])
  })

  it('matches whole words only, so kafkaesque prose is not marked', () => {
    expect(markTerms('The process felt kafkaesque.', ['Kafka'])).toEqual([
      { text: 'The process felt kafkaesque.', flagged: false },
    ])
  })

  it('ignores case, the way the server-side reading does', () => {
    const flagged = markTerms('ran KUBERNETES clusters', ['Kubernetes']).filter((s) => s.flagged)

    expect(flagged).toEqual([{ text: 'KUBERNETES', flagged: true }])
  })

  it('leaves a term it cannot locate unmarked rather than guessing', () => {
    expect(markTerms('Ran it on K8s.', ['Kubernetes'])).toEqual([
      { text: 'Ran it on K8s.', flagged: false },
    ])
  })

  it('handles a term carrying regex punctuation', () => {
    const flagged = markTerms('Wrote it in C++ once.', ['C++']).filter((s) => s.flagged)

    expect(flagged).toEqual([{ text: 'C++', flagged: true }])
  })
})

describe('flaggedIn', () => {
  it('keeps the terms the text still names', () => {
    expect(flaggedIn('Ran Kubernetes and Terraform.', ['Kubernetes', 'Terraform'])).toEqual([
      'Kubernetes',
      'Terraform',
    ])
  })

  it('drops a term the candidate has edited away', () => {
    expect(flaggedIn('Ran the deployment myself.', ['Kubernetes'])).toEqual([])
  })

  it('never reports a term the server did not flag', () => {
    expect(flaggedIn('Ran Kubernetes and Terraform.', ['Terraform'])).toEqual(['Terraform'])
  })
})
