import { query, request } from './http'
import type { LlmCall, LlmCallDetail, SpendBucket, SpendReport } from './types'

export const listLlmCalls = (limit = 50) =>
  request<LlmCall[]>(`/api/llm/calls${query({ limit })}`)

/** Full prompt and raw response - the path for finding out why a generated artifact was bad. */
export const getLlmCall = (id: number) => request<LlmCallDetail>(`/api/llm/calls/${id}`)

/**
 * The whole spend dashboard in one read.
 *
 * One request rather than three because a summary, its series and its breakdowns are one reading —
 * fetching them separately would let a share render against a denominator from a second later.
 */
export const getLlmSpend = (days: number, bucket: SpendBucket) =>
  request<SpendReport>(`/api/llm/spend${query({ days, bucket })}`)
