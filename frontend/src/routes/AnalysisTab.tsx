import { useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { AlertTriangle, Play, ScrollText, Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { getLatestAnalysis, startAnalysis } from '@/api/analyses'
import { ApiError } from '@/api/http'
import { keys } from '@/api/keys'
import { isTerminal, mustHaves, niceToHaves } from '@/api/types'
import type { AnalysisReport } from '@/api/types'
import { AnalysisStateStepper } from '@/components/AnalysisStateStepper'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { LanguageFindings } from '@/components/LanguageFindings'
import { LearningPlan } from '@/components/LearningPlan'
import { Markdown } from '@/components/Markdown'
import { MatchScore } from '@/components/MatchScore'
import { RequirementList, RequirementSummaryStrip } from '@/components/RequirementList'
import { useAnalysisPolling } from '@/hooks/useAnalysisPolling'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/lib/format'
import { useOfferId } from '@/hooks/useOfferId'

export function AnalysisTab() {
  const offerId = useOfferId()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  // 404 here is "never analysed", an empty state rather than an error.
  const latest = useQuery({
    queryKey: keys.latestAnalysis(offerId),
    queryFn: () => getLatestAnalysis(offerId),
  })

  // Keeping the id in the URL is what lets a refresh mid-run pick the same job back up.
  const pinned = searchParams.get('id')
  const analysisId = pinned ? Number(pinned) : (latest.data?.id ?? null)
  const polling = useAnalysisPolling(analysisId)
  const report = polling.data ?? (pinned ? undefined : latest.data ?? undefined)

  const start = useMutation({
    mutationFn: () => startAnalysis(offerId),
    onSuccess: ({ analysisId: id }) => {
      setSearchParams({ id: String(id) }, { replace: true })
      queryClient.invalidateQueries({ queryKey: keys.latestAnalysis(offerId) })
    },
  })

  useTerminalTransition(report, offerId)

  const running = Boolean(report && !isTerminal(report.state))
  const busy = start.isPending || running

  if (latest.isPending) return <Skeleton className="h-64 w-full" />

  const startError = start.error instanceof ApiError ? start.error : null

  return (
    <div className="space-y-6">
      {startError?.status === 409 ? (
        <Alert>
          <AlertTriangle />
          <AlertTitle>No profile imported yet</AlertTitle>
          <AlertDescription>
            <p>An analysis compares the offer against your profile, so there has to be one.</p>
            <Button asChild size="sm" className="mt-2">
              <Link to="/profile">Import a profile</Link>
            </Button>
          </AlertDescription>
        </Alert>
      ) : start.isError ? (
        <ApiErrorAlert error={start.error} />
      ) : null}

      {!report ? (
        <EmptyState
          icon={Sparkles}
          title="Not analysed yet"
          description="Extract the offer's requirements, compare them against your profile, and get a transparent gap report."
          action={
            <Button onClick={() => start.mutate()} disabled={busy}>
              <Play /> {start.isPending ? 'Starting…' : 'Run analysis'}
            </Button>
          }
        />
      ) : running ? (
        <AnalysisStateStepper state={report.state} />
      ) : report.state === 'FAILED' ? (
        <FailedReport report={report} onRetry={() => start.mutate()} retrying={busy} />
      ) : (
        <DoneReport report={report} onRerun={() => start.mutate()} busy={busy} />
      )}
    </div>
  )
}

/**
 * Reaching DONE changes things this screen does not own: the server flips the application status
 * to ANALYZED inside start(), and extraction may have queued new unmatched terms.
 */
function useTerminalTransition(report: AnalysisReport | undefined, offerId: number) {
  const queryClient = useQueryClient()
  const settled = useRef<number | null>(null)
  const watched = useRef<number | null>(null)

  useEffect(() => {
    if (!report) return

    // Only a job we actually watched running counts as a transition. Opening the tab on an
    // analysis that finished hours ago must not announce itself as if it just completed.
    if (!isTerminal(report.state)) {
      watched.current = report.id
      return
    }
    if (watched.current !== report.id) return
    if (settled.current === report.id) return
    settled.current = report.id

    if (report.state === 'DONE') {
      toast.success('Analysis complete')
      queryClient.invalidateQueries({ queryKey: keys.offers })
      queryClient.invalidateQueries({ queryKey: keys.offer(offerId) })
      queryClient.invalidateQueries({ queryKey: keys.latestAnalysis(offerId) })
      queryClient.invalidateQueries({ queryKey: keys.unmatched })
      queryClient.invalidateQueries({ queryKey: keys.aggregate })
    } else {
      toast.error('Analysis failed')
      queryClient.invalidateQueries({ queryKey: ['llm'] })
    }
  }, [report, offerId, queryClient])
}

function FailedReport({
  report,
  onRetry,
  retrying,
}: {
  report: AnalysisReport
  onRetry: () => void
  retrying: boolean
}) {
  return (
    <Alert variant="destructive">
      <AlertTriangle />
      <AlertTitle>Analysis failed</AlertTitle>
      <AlertDescription className="space-y-3">
        <pre className="max-h-64 w-full overflow-auto whitespace-pre-wrap rounded-md border border-current/20 bg-background/50 p-3 font-mono text-xs">
          {report.error ?? 'No error message was recorded.'}
        </pre>
        <div className="flex gap-2">
          <Button size="sm" onClick={onRetry} disabled={retrying}>
            <Play /> Retry
          </Button>
          <Button asChild size="sm" variant="outline">
            <Link to="/llm">
              <ScrollText /> Most recent model calls
            </Link>
          </Button>
        </div>
      </AlertDescription>
    </Alert>
  )
}

function DoneReport({
  report,
  onRerun,
  busy,
}: {
  report: AnalysisReport
  onRerun: () => void
  busy: boolean
}) {
  const must = mustHaves(report)
  const nice = niceToHaves(report)

  return (
    <>
      <div className="flex items-start justify-between gap-4">
        <Card className="flex-1">
          <CardContent className="pt-6">
            <MatchScore report={report} />
          </CardContent>
        </Card>
        <div className="flex flex-col items-end gap-2">
          <Button onClick={onRerun} disabled={busy} variant="outline">
            <Play /> Run new analysis
          </Button>
          <p className="text-xs text-muted-foreground">
            Completed {formatDateTime(report.completedAt)}
          </p>
        </div>
      </div>

      {report.summaryMarkdown ? (
        <Card>
          <CardHeader><CardTitle className="text-base">Summary</CardTitle></CardHeader>
          <CardContent><Markdown>{report.summaryMarkdown}</Markdown></CardContent>
        </Card>
      ) : null}

      <section className="space-y-3">
        <div>
          <h2 className="font-heading text-lg font-semibold tracking-tight">Must-haves</h2>
          <RequirementSummaryStrip items={must} />
        </div>
        <RequirementList items={must} />
      </section>

      <section className="space-y-3">
        <div>
          <h2 className="font-heading text-lg font-semibold tracking-tight">Nice-to-haves</h2>
          <RequirementSummaryStrip items={nice} />
        </div>
        <RequirementList items={nice} />
      </section>

      <section className="space-y-3">
        <h2 className="font-heading text-lg font-semibold tracking-tight">Languages</h2>
        <LanguageFindings items={report.languageRequirements} />
      </section>

      <section className="space-y-3">
        <h2 className="font-heading text-lg font-semibold tracking-tight">Learning plan</h2>
        <LearningPlan items={report.learningPlan} />
      </section>
    </>
  )
}
