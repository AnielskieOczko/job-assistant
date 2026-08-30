import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { getLlmSpend, getProviderAccount } from '@/api/llm'
import { keys } from '@/api/keys'
import {
  SPEND_BUCKETS, type BudgetStatus, type ProviderAccount, type SpendBucket, type SpendReport,
} from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { PageHeader } from '@/components/PageHeader'
import { StatTile } from '@/components/StatTile'
import { Button } from '@/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime, formatSpend } from '@/lib/format'
import { SpendBreakdown } from './SpendBreakdown'
import { SpendChart } from './SpendChart'
import { coverage } from './format'

const WINDOWS = [30, 90, 365]

export function LlmSpendPage() {
  const [days, setDays] = useState(30)
  const [bucket, setBucket] = useState<SpendBucket>('DAY')

  const spend = useQuery({
    queryKey: keys.llmSpend(days, bucket),
    queryFn: () => getLlmSpend(days, bucket),
    refetchInterval: 30_000,
  })

  // Its own query, not part of the report: an outbound call to a third party must not be able to
  // keep this page from rendering. It is allowed to be missing, and says why when it is.
  const account = useQuery({
    queryKey: keys.llmAccount,
    queryFn: getProviderAccount,
    refetchInterval: 5 * 60_000,
  })

  return (
    <>
      <PageHeader
        title="Spend"
        description="What the models have cost, accumulated from every call as it happened. These totals live in their own table, so they survive the thirty-day purge that ages prompts out of the call log."
        actions={
          <div className="flex items-center gap-2">
            <Select value={String(days)} onValueChange={(value) => setDays(Number(value))}>
              <SelectTrigger className="w-32"><SelectValue /></SelectTrigger>
              <SelectContent>
                {WINDOWS.map((window) => (
                  <SelectItem key={window} value={String(window)}>Last {window} days</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={bucket} onValueChange={(value) => setBucket(value as SpendBucket)}>
              <SelectTrigger className="w-28"><SelectValue /></SelectTrigger>
              <SelectContent>
                {SPEND_BUCKETS.map((option) => (
                  <SelectItem key={option} value={option}>
                    {option.charAt(0) + option.slice(1).toLowerCase()}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="outline" onClick={() => spend.refetch()}>
              <RefreshCw className={spend.isFetching ? 'animate-spin' : ''} /> Refresh
            </Button>
          </div>
        }
      />

      {spend.isError ? <ApiErrorAlert error={spend.error} /> : null}
      {spend.data ? (
        <Dashboard report={spend.data} account={account.data} days={days} />
      ) : (
        <Skeleton className="h-96 w-full" />
      )}
    </>
  )
}

function Dashboard({ report, account, days }: {
  report: SpendReport
  account: ProviderAccount | undefined
  days: number
}) {
  const { summary, series, windowTotal, byTask, byModel, byProfile } = report
  const { budget } = summary

  return (
    <div className="space-y-8">
      {/*
        The hero: the one number this page leads with, at display size and in proportional figures
        (tabular-nums is for columns, where digits have to line up). Its caption carries the
        coverage, because a total over calls that reported no price is a floor, not a total.
      */}
      <section className="rounded-lg border p-6">
        <p className="text-xs font-medium text-muted-foreground">Last 30 days</p>
        <p className="mt-1 text-5xl font-semibold tracking-tight">
          {formatSpend(summary.last30Days.costUsd)}
        </p>
        <p className="mt-2 text-sm text-muted-foreground">
          {coverage(summary.last30Days.pricedCalls, summary.last30Days.calls)}.
        </p>

        {/*
          Two figures side by side, and the gap between them is the feature. Ours is an undercount
          by construction — nothing from before cost capture existed, and nothing spent on the same
          key by anything else — so a single number would be wrong in a direction nobody could see.
        */}
        <p className="mt-3 border-t pt-3 text-xs leading-relaxed text-muted-foreground">
          Recorded here, all time: {formatSpend(summary.lifetime.costUsd)}
          {summary.recordedSince ? `, since ${summary.recordedSince}` : null}.
          {account?.available ? (
            <>
              {' '}The provider reports {formatSpend(account.usageUsd)} on the key{' '}
              {account.modelProfile} uses
              {account.limitRemainingUsd !== null
                ? `, with ${formatSpend(account.limitRemainingUsd)} of credit left`
                : null}
              {account.checkedAt ? ` (read ${formatDateTime(account.checkedAt)})` : null}. A gap is
              expected rather than alarming: that key may be shared, and spend from before this
              application recorded any was never captured.
            </>
          ) : (
            // The backend's reason is already a whole sentence, and pairing it with a lead-in of
            // our own produced two sentences saying the same thing.
            <> {account?.unavailableReason ?? 'No provider figure to compare against.'}</>
          )}
        </p>
      </section>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          label="Today"
          value={formatSpend(summary.today.costUsd)}
          caption={`${summary.today.calls.toLocaleString()} calls so far, counted in UTC`}
        />
        <StatTile
          label="Last 7 days"
          value={formatSpend(summary.last7Days.costUsd)}
          caption={coverage(summary.last7Days.pricedCalls, summary.last7Days.calls)}
        />
        <StatTile
          label="All time"
          value={formatSpend(summary.lifetime.costUsd)}
          caption={
            summary.recordedSince
              ? `since ${summary.recordedSince}, when recording began`
              : 'nothing recorded yet'
          }
        />
        {/*
          A cap that is set renders as spent-against-limit; a cap that is not renders as the fact
          that there is none, in the words-instead-of-a-number treatment. A blank tile would read
          as zero spent against an unknown limit, which is the opposite of the truth.
        */}
        <StatTile
          label="Budget"
          belowFloor={budget.monthlyLimitUsd === null && budget.dailyLimitUsd === null}
          value={budgetValue(budget)}
          caption={budgetCaption(budget)}
        />
      </div>

      <SpendChart
        series={series}
        pricedCalls={windowTotal.pricedCalls}
        calls={windowTotal.calls}
      />

      {/*
        Failed calls are their own line rather than a footnote on a chart that only counts what
        succeeded. A failure is charged nothing, so it never shows up in a column — but it is still
        the work having to be done twice.
      */}
      {windowTotal.failedCalls > 0 ? (
        <p className="text-xs text-muted-foreground">
          {windowTotal.failedCalls.toLocaleString()} of {windowTotal.calls.toLocaleString()} calls
          in this window failed. A failed call is charged nothing, but whatever provoked it still
          has to be run again.
        </p>
      ) : null}

      <SpendBreakdown
        title="By task"
        caption={`What each part of the pipeline costs, over the last ${days} days.`}
        groups={byTask}
        windowTotal={windowTotal}
      />
      <SpendBreakdown
        title="By model"
        caption="The same window, split by the model that answered."
        groups={byModel}
        windowTotal={windowTotal}
      />
      <SpendBreakdown
        title="By profile"
        caption="And by the configured profile the call was routed through."
        groups={byProfile}
        windowTotal={windowTotal}
      />
    </div>
  )
}

/** Whichever cap is set, spent against it. Monthly wins when both are, being the coarser promise. */
function budgetValue(budget: BudgetStatus): string {
  if (budget.monthlyLimitUsd !== null) {
    return `${formatSpend(budget.monthlySpentUsd)} of ${formatSpend(budget.monthlyLimitUsd)}`
  }
  if (budget.dailyLimitUsd !== null) {
    return `${formatSpend(budget.dailySpentUsd)} of ${formatSpend(budget.dailyLimitUsd)}`
  }
  return 'No cap set'
}

function budgetCaption(budget: BudgetStatus): string {
  if (budget.exhausted) return 'Reached — further calls are refused until the period rolls over.'
  if (budget.monthlyLimitUsd !== null || budget.dailyLimitUsd !== null) {
    return 'Calls are refused once this is reached.'
  }
  return 'Set job-assistant.llm.budget.monthly-usd to cap spending.'
}
