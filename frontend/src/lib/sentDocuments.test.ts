import { describe, expect, it } from 'vitest'
import type { Application } from '@/api/types'
import { sentDocumentsLabel } from './sentDocuments'

const application = (
  sentCvDocumentId: number | null,
  sentCoverLetterDocumentId: number | null,
): Application => ({
  id: 1,
  offerId: 1,
  status: 'APPLIED',
  statusChangedAt: '2026-09-03T10:00:00Z',
  appliedOn: '2026-09-03',
  notes: null,
  sentCvDocumentId,
  sentCoverLetterDocumentId,
})

describe('sentDocumentsLabel', () => {
  it('names both documents when both were sent', () => {
    expect(sentDocumentsLabel(application(1, 2))).toBe('CV + letter')
  })

  it('names whichever one was sent on its own', () => {
    expect(sentDocumentsLabel(application(1, null))).toBe('CV')
    expect(sentDocumentsLabel(application(null, 2))).toBe('Letter')
  })

  /* Nothing recorded is not "nothing sent", so the caller renders it rather than this. */
  it('returns null when nothing was recorded', () => {
    expect(sentDocumentsLabel(application(null, null))).toBeNull()
  })
})
