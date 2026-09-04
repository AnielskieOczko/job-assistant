import type { DemandRanking, DocumentType, SpendBucket, TriageRanking } from './types'

/** Query key factory. Keeping them here stops invalidation from drifting out of sync. */
export const keys = {
  offers: ['offers'] as const,
  offer: (id: number) => ['offer', id] as const,
  /*
    Deliberately nested under `offers` rather than under `analysis`, even though the scores come
    from analyses. The shortlist holds the same rows the offer list does, and five places already
    invalidate `keys.offers` after a paste, a promotion, a status change or a sent-document mark.
    A sibling key would need every one of them to remember a second one; a child key means none
    of them has to. The profile id is on the end because the scores are measured against one
    persona, so switching must refetch rather than show the previous one's numbers.
  */
  shortlist: (profileId: number | null) => ['offers', 'shortlist', profileId] as const,

  latestAnalysis: (offerId: number, profileId: number) => ['analysis', 'latest', offerId, profileId] as const,
  analysis: (id: number | null) => ['analysis', id] as const,
  aggregate: (profileId: number) => ['analysis', 'aggregate', profileId] as const,

  latestDocument: (offerId: number, type: DocumentType, profileId: number) =>
    ['document', offerId, type, profileId] as const,
  documentLibrary: (profileId: number | null) => ['document', 'library', profileId] as const,

  profiles: ['profiles'] as const,
  profile: (profileId: number) => ['profile', profileId] as const,

  skills: ['catalog', 'skills'] as const,
  unmatched: ['catalog', 'unmatched'] as const,

  triageQueue: (minOccurrences: number, ranking: TriageRanking, limit: number) =>
    ['triage', 'queue', minOccurrences, ranking, limit] as const,

  /*
    Scope and salary carry no profile id because neither compares anything against the profile —
    they describe the corpus. Demand and the offer page do, so switching persona must not show the
    previous one's coverage.
  */
  marketScope: ['market', 'scope'] as const,
  marketIngestion: ['market', 'ingestion'] as const,
  marketSalary: ['market', 'salary'] as const,
  marketDemand: (profileId: number | null, ranking: DemandRanking, limit: number) =>
    ['market', 'demand', profileId, ranking, limit] as const,
  marketOffers: (profileId: number | null, limit: number, offset: number) =>
    ['market', 'offers', profileId, limit, offset] as const,

  llmCalls: (limit: number) => ['llm', 'calls', limit] as const,
  llmCall: (id: number) => ['llm', 'call', id] as const,
  llmSpend: (days: number, bucket: SpendBucket) => ['llm', 'spend', days, bucket] as const,
  llmAccount: ['llm', 'account'] as const,
}
