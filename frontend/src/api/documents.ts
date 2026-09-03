import { query, request, requestOrNull } from './http'
import type { Application, DocumentType, GeneratedDocument } from './types'

/**
 * Query params only, no body. Throws ApiError 422 with `fabricatedClaims` when the model tried to
 * claim a skill absent from the profile - nothing is stored in that case - or 409 when the offer
 * has no completed analysis of this profile to tailor against.
 */
export const generateDocument = (offerId: number, profileId: number, type: DocumentType, language: string) =>
  request<GeneratedDocument>(
    `/api/offers/${offerId}/documents${query({ profileId, type, language })}`,
    { method: 'POST' },
  )

export const getLatestDocument = (offerId: number, type: DocumentType, profileId: number) =>
  requestOrNull<GeneratedDocument>(
    `/api/offers/${offerId}/documents/latest${query({ type, profileId })}`,
  )

/** Served as text/html, byte-for-byte the markup Chromium turns into the PDF. */
export const documentHtmlUrl = (documentId: number) => `/api/documents/${documentId}/html`

/**
 * Rendered on demand through headless Chromium and never stored, so the first call after a fresh
 * clone downloads the browser and can take minutes. Always navigate to this, never fetch it.
 */
export const documentPdfUrl = (documentId: number) => `/api/documents/${documentId}/pdf`

/**
 * Records this document as the one actually sent for the offer. Answers with the *application*,
 * not the document: the link is a fact about the application, so `keys.offers` is what to
 * invalidate. Re-marking replaces whatever was recorded for that type.
 */
export const markDocumentSent = (offerId: number, documentId: number) =>
  request<Application>(`/api/offers/${offerId}/documents/${documentId}/sent`, { method: 'PUT' })

/** Clears the record for a type, so a mis-click is not permanent. */
export const unmarkDocumentSent = (offerId: number, type: DocumentType) =>
  request<Application>(`/api/offers/${offerId}/documents/sent${query({ type })}`, { method: 'DELETE' })
