import { query, request } from './http'
import type { LlmCall, LlmCallDetail } from './types'

export const listLlmCalls = (limit = 50) =>
  request<LlmCall[]>(`/api/llm/calls${query({ limit })}`)

/** Full prompt and raw response - the path for finding out why a generated artifact was bad. */
export const getLlmCall = (id: number) => request<LlmCallDetail>(`/api/llm/calls/${id}`)
