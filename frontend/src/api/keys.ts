import type { DocumentType } from './types'

/** Query key factory. Keeping them here stops invalidation from drifting out of sync. */
export const keys = {
  offers: ['offers'] as const,
  offer: (id: number) => ['offer', id] as const,

  latestAnalysis: (offerId: number) => ['analysis', 'latest', offerId] as const,
  analysis: (id: number | null) => ['analysis', id] as const,
  aggregate: ['analysis', 'aggregate'] as const,

  latestDocument: (offerId: number, type: DocumentType) => ['document', offerId, type] as const,

  profile: ['profile'] as const,

  skills: ['catalog', 'skills'] as const,
  unmatched: ['catalog', 'unmatched'] as const,

  llmCalls: (limit: number) => ['llm', 'calls', limit] as const,
  llmCall: (id: number) => ['llm', 'call', id] as const,
}
