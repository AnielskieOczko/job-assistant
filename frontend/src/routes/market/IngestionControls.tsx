import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { fetchIngestionSchedule, runIngestion } from '@/api/market'
import { keys } from '@/api/keys'
import type { IngestionReport, IngestionSchedule } from '@/api/types'
import { Button } from '@/components/ui/button'
import { formatDateTime } from '@/lib/format'
import { percent, plural } from './format'

/**
 * How this corpus gets its data, and the one control that changes it.
 *
 * Everything else on this page is aggregation over rows already stored. This is the only thing in
 * the application that reaches solid.jobs, so it is a button rather than something a page load
 * does: a screen that silently polled a third party on every visit would be spending someone
 * else's rate limit to render a number that had not moved.
 *
 * The schedule sentence is not decoration. Numbers from a corpus that refreshes itself nightly and
 * numbers from one that has not been polled since March are indistinguishable on the page, and the
 * reader has no way to tell which they are looking at unless it says so.
 */
export function IngestionControls() {
  const queryClient = useQueryClient()
  const schedule = useQuery({ queryKey: keys.marketIngestion, queryFn: fetchIngestionSchedule })

  const ingest = useMutation({
    mutationFn: runIngestion,
    onSuccess: (report) => {
      /*
        A failed run still answers 200 carrying its error, in the same shape a successful one does —
        the poll is a batch, not a request whose status code can describe it. Reading only the HTTP
        status here would report a failure as a success.
      */
      if (report.error) {
        toast.error(`Poll failed: ${report.error}`)
      } else {
        toast.success(summarise(report))
      }
      // The corpus moved, so every number drawn from it did too. Ingestion also writes unplaced
      // terms into the review queue under market_occurrences, and the triage ranking is ordered by
      // in-scope demand — both are stale the moment this returns.
      queryClient.invalidateQueries({ queryKey: ['market'] })
      queryClient.invalidateQueries({ queryKey: keys.unmatched })
      queryClient.invalidateQueries({ queryKey: ['triage'] })
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Poll failed'),
  })

  return (
    <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 rounded-md border px-4 py-3">
      <p className="min-w-0 flex-1 basis-80 text-xs leading-relaxed text-muted-foreground">
        {schedule.isPending ? 'Checking how this corpus is polled…' : null}
        {schedule.isError ? 'Could not read the polling schedule.' : null}
        {schedule.data ? <ScheduleSentence schedule={schedule.data} /> : null}
      </p>

      <Button
        variant="outline"
        size="sm"
        className="shrink-0"
        disabled={ingest.isPending}
        onClick={() => ingest.mutate()}
      >
        <RefreshCw className={ingest.isPending ? 'animate-spin' : undefined} />
        {ingest.isPending ? 'Polling solid.jobs…' : 'Poll now'}
      </Button>
    </div>
  )
}

/*
  Two different sentences because the two states leave the reader with different questions. When a
  schedule is running, what they need is when it next will; when it is not, what they need is how
  stale this is and that only they can change it. The scope line directly above already dates the
  last poll, so the scheduled branch does not repeat it.
*/
function ScheduleSentence({ schedule }: { schedule: IngestionSchedule }) {
  // The cron is printed beside the time it was interpreted into, not instead of it. The time is
  // what a reader wants; the cron is what the scheduler actually runs, and showing only the
  // interpretation would put a claim on the page that nothing on the page could be checked against.
  return schedule.scheduled ? (
    <>
      <span className="font-medium text-foreground">Polled automatically.</span> Next run{' '}
      {formatDateTime(schedule.nextPollAt)}
      {schedule.cron ? ` (cron ${schedule.cron})` : null}. Polling by hand refreshes what is already
      held rather than duplicating it.
    </>
  ) : (
    <>
      <span className="font-medium text-foreground">Automatic polling is off.</span> This corpus
      changes only when you poll it here, so every number on this page is as old as the last run —{' '}
      {schedule.lastPolledAt
        ? formatDateTime(schedule.lastPolledAt)
        : 'nothing has been ingested yet'}
      .
    </>
  )
}

/**
 * What one run did, in counts.
 *
 * Reports offers seen *and* how many were new, and the resolved mentions *with* their denominator —
 * a re-poll of an unchanged board legitimately inserts nothing, and "1,493 offers" alone cannot be
 * told apart from a first ingest. The resolution rate is appended only when the server computed one:
 * it is null below its sample floor, because 1 of 1 resolved reads identically to 90 of 90.
 */
function summarise(report: IngestionReport): string {
  const rate =
    report.skillResolutionRate === null ? '' : ` (${percent(report.skillResolutionRate)})`

  return (
    `${plural(report.offersSeen, 'offer')} seen, ${report.offersInserted.toLocaleString()} new · ` +
    `${report.skillsResolved.toLocaleString()} of ${report.skillMentions.toLocaleString()} skill ` +
    `mentions resolved${rate} · ${plural(report.distinctUnresolvedTerms, 'term')} for review`
  )
}
