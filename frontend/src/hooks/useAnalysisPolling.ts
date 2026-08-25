import { useQuery } from '@tanstack/react-query'
import { getAnalysis } from '@/api/analyses'
import { keys } from '@/api/keys'
import { isTerminal } from '@/api/types'
import type { AnalysisReport } from '@/api/types'

const POLL_MS = 1500

/**
 * Polls one analysis until it reaches DONE or FAILED.
 *
 * There is no SSE or WebSocket endpoint - polling is the only mechanism the API offers. The stop
 * condition lives entirely in `refetchInterval` returning false, deliberately: an effect that
 * clears an interval gives the bug two places to hide. A run takes 30-90s, so a fixed 1.5s
 * against local Postgres is roughly 40 requests and needs no backoff.
 */
export function useAnalysisPolling(analysisId: number | null) {
  return useQuery({
    queryKey: keys.analysis(analysisId),
    queryFn: () => getAnalysis(analysisId!),
    enabled: analysisId !== null,
    refetchInterval: (query) => {
      const report = query.state.data as AnalysisReport | undefined
      // The POST returns before the row is necessarily readable; keep asking.
      if (!report) return POLL_MS
      return isTerminal(report.state) ? false : POLL_MS
    },
    refetchIntervalInBackground: false,
    staleTime: 0,
    gcTime: 5 * 60_000,
  })
}
