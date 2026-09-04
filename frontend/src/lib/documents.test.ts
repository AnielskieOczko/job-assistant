import { describe, expect, it } from 'vitest'
import type { DocumentLibraryEntry, GeneratedDocument } from '@/api/types'
import { cvEntries, documentsById, reusedFromLabel } from './documents'

const document = (overrides: Partial<GeneratedDocument> = {}): GeneratedDocument => ({
  id: 1,
  offerId: 1,
  profileId: 1,
  analysisId: 1,
  type: 'CV',
  language: 'English',
  html: '<html/>',
  createdAt: '2026-09-03T10:00:00Z',
  profileRevision: 1,
  droppedBulletCount: 0,
  droppedSkillCount: 0,
  consentClauseLanguage: null,
  sourceDocumentId: null,
  ...overrides,
})

const entry = (overrides: Partial<GeneratedDocument> = {}, offerTitle = 'Kotlin Engineer'): DocumentLibraryEntry => ({
  document: document(overrides),
  offerTitle,
  offerCompany: 'Acme',
})

describe('cvEntries', () => {
  it('keeps only CVs, dropping cover letters', () => {
    const rows = [entry({ id: 1, type: 'CV' }), entry({ id: 2, type: 'COVER_LETTER' })]
    expect(cvEntries(rows).map((row) => row.document.id)).toEqual([1])
  })
})

describe('documentsById', () => {
  it('keys entries by their document id', () => {
    const rows = [entry({ id: 1 }), entry({ id: 2 })]
    const byId = documentsById(rows)
    expect(byId.get(1)?.document.id).toBe(1)
    expect(byId.get(2)?.document.id).toBe(2)
  })
})

describe('reusedFromLabel', () => {
  it('returns null for a document that was freshly tailored', () => {
    const row = entry({ id: 1, sourceDocumentId: null })
    expect(reusedFromLabel(row, documentsById([row]))).toBeNull()
  })

  it('names the source offer when it is in the same list', () => {
    const source = entry({ id: 1 }, 'Original Offer')
    const reused = entry({ id: 2, sourceDocumentId: 1 }, 'Second Offer')
    const rows = [source, reused]
    expect(reusedFromLabel(reused, documentsById(rows))).toBe('Reused from Original Offer')
  })

  it('falls back to naming the id when the source is not resolvable', () => {
    const reused = entry({ id: 2, sourceDocumentId: 999 })
    expect(reusedFromLabel(reused, documentsById([reused]))).toBe('Reused from document #999')
  })
})
