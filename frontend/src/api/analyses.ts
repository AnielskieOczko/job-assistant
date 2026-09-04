import { query, request, requestOrNull } from './http'
import type { AggregateGapReport, AnalysisReport, OfferShortlist, StartedAnalysis } from './types'

/** 202 Accepted. Can also 409 when the profile has no details yet, or 404 for an unknown offer. */
export const startAnalysis = (offerId: number, profileId: number) =>
  request<StartedAnalysis>(`/api/offers/${offerId}/analyses${query({ profileId })}`, { method: 'POST' })

export const getAnalysis = (id: number) => request<AnalysisReport>(`/api/analyses/${id}`)

/** 404 here means "never analysed against this profile", which is an empty state rather than an error. */
export const getLatestAnalysis = (offerId: number, profileId: number) =>
  requestOrNull<AnalysisReport>(`/api/offers/${offerId}/analyses/latest${query({ profileId })}`)

export const getAggregateGaps = (profileId: number) =>
  request<AggregateGapReport>(`/api/analyses/aggregate${query({ profileId })}`)

/**
 * Every saved offer with the score of its latest analysis against `profileId`.
 *
 * `profileId` is omitted rather than guessed while the profile list is still loading: the server
 * falls back to the default profile, and an install with no persona at all answers with every offer
 * unscored instead of an error.
 */
export const getShortlist = (profileId: number | null) =>
  request<OfferShortlist>(`/api/analyses/shortlist${query({ profileId })}`)
