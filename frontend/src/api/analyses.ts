import { request, requestOrNull } from './http'
import type { AggregateGapReport, AnalysisReport, StartedAnalysis } from './types'

/** 202 Accepted. Can also 409 when no profile has been imported, or 404 for an unknown offer. */
export const startAnalysis = (offerId: number) =>
  request<StartedAnalysis>(`/api/offers/${offerId}/analyses`, { method: 'POST' })

export const getAnalysis = (id: number) => request<AnalysisReport>(`/api/analyses/${id}`)

/** 404 here means "never analysed", which is an empty state rather than an error. */
export const getLatestAnalysis = (offerId: number) =>
  requestOrNull<AnalysisReport>(`/api/offers/${offerId}/analyses/latest`)

export const getAggregateGaps = () => request<AggregateGapReport>('/api/analyses/aggregate')
