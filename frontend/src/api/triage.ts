import { query, request } from './http'
import type { TriageQueue, TriageRanking } from './types'

/**
 * The ranked, filtered review queue.
 *
 * Read-only. Approving and rejecting still go through the catalog endpoints, because the rule that
 * only a human decision may grow the catalog is enforced there and one write path is easier to keep
 * honest than two.
 */
export const fetchTriageQueue = (minOccurrences: number, ranking: TriageRanking, limit = 100) =>
  request<TriageQueue>(`/api/triage/queue${query({ minOccurrences, ranking, limit })}`)
