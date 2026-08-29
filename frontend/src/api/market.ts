import { query, request } from './http'
import type {
  CorpusSummary,
  DemandRanking,
  DemandReport,
  IngestionReport,
  IngestionSchedule,
  MarketOfferPage,
  MarketScopeReport,
  SalaryReport,
} from './types'

/**
 * The ingested market corpus, read-only.
 *
 * Every read here is aggregation over rows already stored: no model is involved anywhere in this
 * module, and no GET below can spend a token or write a row.
 *
 * `runIngestion` is the exception and the only one — it reaches solid.jobs and writes to the
 * corpus, so it is a POST that happens when someone asks for it, never on a page load.
 */

/** The scope line. Fetch it first: nothing else on the page means anything without it. */
export const fetchMarketScope = () => request<MarketScopeReport>('/api/market/scope')

/** Salary bands per comparable slice, each carrying its own sample size. */
export const fetchMarketSalary = () => request<SalaryReport>('/api/market/salary')

/**
 * The demand table, led by skills the profile does not cover.
 *
 * `UNMET` is the default for a reason: a table headed by a skill you already hold is true and
 * answers nothing. `TOTAL` ignores coverage and just reports what the scope asks for.
 */
export const fetchMarketDemand = (
  ranking: DemandRanking = 'UNMET',
  profileId?: number,
  limit = 100,
) => request<DemandReport>(`/api/market/demand${query({ ranking, profileId, limit })}`)

/** The offers behind the numbers, so a row can be opened rather than trusted. */
export const fetchMarketOffers = (
  { inScopeOnly = true, profileId, limit = 100, offset = 0 }: {
    inScopeOnly?: boolean
    profileId?: number
    limit?: number
    offset?: number
  } = {},
) => request<MarketOfferPage>(`/api/market/offers${query({ inScopeOnly, profileId, limit, offset })}`)

/** What the corpus holds per source, and the window it was collected over. */
export const fetchCorpusSummary = () => request<CorpusSummary[]>('/api/market/corpus')

/** Whether anything polls the corpus unattended, and when it next will. */
export const fetchIngestionSchedule = () => request<IngestionSchedule>('/api/market/ingestion')

/**
 * Polls the source now.
 *
 * The one call in this file that leaves the machine. Idempotent by construction — an offer already
 * held is refreshed rather than duplicated — so a second press costs another request to the source
 * and nothing else.
 */
export const runIngestion = () =>
  request<IngestionReport>('/api/market/ingest', { method: 'POST' })
