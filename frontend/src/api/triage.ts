import { query, request } from './http'
import type { SuggestionRun, TriageQueue, TriageRanking } from './types'

/**
 * The ranked, filtered review queue.
 *
 * Read-only. Approving and rejecting still go through the catalog endpoints, because the rule that
 * only a human decision may grow the catalog is enforced there and one write path is easier to keep
 * honest than two.
 */
export const fetchTriageQueue = (minOccurrences: number, ranking: TriageRanking, limit = 100) =>
  request<TriageQueue>(`/api/triage/queue${query({ minOccurrences, ranking, limit })}`)

/**
 * Asks a model to read the terms string similarity could not place.
 *
 * A POST because it spends tokens and stores rows: it has to be something you chose to do. Loading
 * the queue never calls a model — it shows whatever this has already stored.
 */
export const suggestTriage = (minOccurrences: number, ranking: TriageRanking, limit = 25) =>
  request<SuggestionRun>(
    `/api/triage/suggest${query({ minOccurrences, ranking, limit })}`,
    { method: 'POST' },
  )
