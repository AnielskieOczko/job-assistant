import type { DocumentType, TriageRanking } from './types'

/** Query key factory. Keeping them here stops invalidation from drifting out of sync. */
export const keys = {
  offers: ['offers'] as const,
  offer: (id: number) => ['offer', id] as const,

  latestAnalysis: (offerId: number, profileId: number) => ['analysis', 'latest', offerId, profileId] as const,
  analysis: (id: number | null) => ['analysis', id] as const,
  aggregate: (profileId: number) => ['analysis', 'aggregate', profileId] as const,

  latestDocument: (offerId: number, type: DocumentType, profileId: number) =>
    ['document', offerId, type, profileId] as const,

  profiles: ['profiles'] as const,
  profile: (profileId: number) => ['profile', profileId] as const,

  skills: ['catalog', 'skills'] as const,
  unmatched: ['catalog', 'unmatched'] as const,

  triageQueue: (minOccurrences: number, ranking: TriageRanking, limit: number) =>
    ['triage', 'queue', minOccurrences, ranking, limit] as const,

  llmCalls: (limit: number) => ['llm', 'calls', limit] as const,
  llmCall: (id: number) => ['llm', 'call', id] as const,
}
