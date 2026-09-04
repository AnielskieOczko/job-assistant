import { json, query, request } from './http'
import type { PolishField, PolishSuggestion } from './types'

/**
 * Asks a model to rewrite one field of the profile.
 *
 * A POST that stores nothing. It spends tokens, so it must be a thing someone chose to do rather
 * than something a form re-render can cause — and the write it might lead to is an ordinary profile
 * `PUT` sent afterwards by the accept, which is what keeps "no model writes to the profile" true.
 *
 * Failure modes worth branching on: 422 with `sensitiveFields` when the field's own text carries an
 * identifier (a description quoting the project's URL), 422 when the model answered with nothing,
 * 400 for blank or oversized text.
 */
export const polishField = (profileId: number, field: PolishField, text: string) =>
  request<PolishSuggestion>(
    `/api/profiles/${profileId}/polish${query({ field })}`,
    { method: 'POST', ...json({ text }) },
  )
