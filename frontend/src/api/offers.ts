import { json, request, requestOrNull } from './http'
import type {
  Application,
  JobOffer,
  OfferSummary,
  PasteOfferRequest,
  PastedOffer,
  UpdateStatusRequest,
} from './types'

export const listOffers = () => request<OfferSummary[]>('/api/offers')

export const getOffer = (id: number) => requestOrNull<JobOffer>(`/api/offers/${id}`)

/**
 * 201 for a new offer, 200 with `deduplicated: true` when this text was already stored. Read the
 * flag on the body rather than the status code - both are successes.
 */
export const pasteOffer = (body: PasteOfferRequest) =>
  request<PastedOffer>('/api/offers', { method: 'POST', ...json(body) })

export const updateOfferStatus = (id: number, body: UpdateStatusRequest) =>
  request<Application>(`/api/offers/${id}/status`, { method: 'PUT', ...json(body) })
