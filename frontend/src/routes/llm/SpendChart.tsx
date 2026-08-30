import type { SpendSeries } from '@/api/types'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { formatSpend } from '@/lib/format'
import { bucketLabel } from './format'

/** Columns thinner than this stop being readable as marks and start being a texture. */
const MIN_COLUMN_PX = 3

/**
 * The one chart on this page: what was spent, per period.
 *
 * **One series, so one ink and no legend.** Every column measures the same thing, so giving them
 * separate hues would encode an identity the date axis already carries. The period with the
 * highest spend is drawn in the foreground ink and the rest recede — the reader's eye lands on the
 * expensive day because the expensive day is the subject. Identity is never colour alone: every
 * column carries its date and its figure in the tooltip, and the breakdown tables below repeat
 * everything in text.
 *
 * **Empty periods are drawn, not skipped.** A quiet fortnight renders as a fortnight of baseline,
 * because dropping it would compress the axis and make occasional spending look continuous.
 *
 * The column height is *money spent*, never money owed or money forecast. Where a period's calls
 * went unpriced the caption says so rather than the bar quietly under-reporting.
 */
export function SpendChart({ series, pricedCalls, calls }: {
  series: SpendSeries
  pricedCalls: number
  calls: number
}) {
  const points = series.points
  // Two columns is a sentence with extra ink. Below that the KPI tiles already say it.
  if (points.length < 3) return null

  const max = Math.max(...points.map((point) => point.total.costUsd))
  const peak = points.findIndex((point) => point.total.costUsd === max)
  const spentNothing = max === 0

  return (
    <figure className="rounded-lg border p-5">
      <figcaption className="mb-1 text-sm font-medium">
        Spend per {series.bucket.toLowerCase()}
      </figcaption>
      <p className="mb-5 text-xs text-muted-foreground">
        {bucketLabel(series.from, series.bucket)} to {bucketLabel(series.to, series.bucket)}.
        {spentNothing
          ? ' Nothing in this window reported a price.'
          : ` The tallest column is ${formatSpend(max)}.`}
        {pricedCalls < calls ? (
          <> Based on {pricedCalls.toLocaleString()} of {calls.toLocaleString()} calls that
            reported one, so these columns are a floor.</>
        ) : null}
      </p>

      {/*
        Columns are square at the baseline and 4px rounded at the cap, capped at 24px wide so a
        short window leaves air rather than fusing into a block. The 2px gap between neighbours is
        surface, not a stroke: separating marks with ink would add weight that is not data.
      */}
      {/*
        `justify-between` because the 24px cap leaves slack in a short window, and slack collected
        into one gutter on the right reads as a chart that stops early. Spread, the leftover becomes
        air between marks - which is what the cap was for.
      */}
      <div
        className="flex h-40 items-end justify-between gap-[2px]"
        role="img"
        aria-label="Spend per period"
      >
        {points.map((point, index) => (
          <Column
            key={point.periodStart}
            label={bucketLabel(point.periodStart, series.bucket)}
            costUsd={point.total.costUsd}
            calls={point.total.calls}
            pricedCalls={point.total.pricedCalls}
            heightPercent={max === 0 ? 0 : (point.total.costUsd / max) * 100}
            emphasised={index === peak && !spentNothing}
          />
        ))}
      </div>

      {/*
        Three anchors rather than a tick per column: thirty rotated dates would be more ink than
        the marks they label, and the exact date of any one column is a hover away. There is
        deliberately no value axis - the caption names the tallest column and the tables below carry
        every figure in text, so nothing here is reachable only by measuring a bar against a line.
      */}
      <div className="mt-2 flex justify-between border-t pt-2 text-[11px] text-muted-foreground">
        <span>{bucketLabel(points[0].periodStart, series.bucket)}</span>
        {points.length > 6 ? (
          <span>{bucketLabel(points[Math.floor(points.length / 2)].periodStart, series.bucket)}</span>
        ) : null}
        <span>{bucketLabel(points[points.length - 1].periodStart, series.bucket)}</span>
      </div>
    </figure>
  )
}

function Column({
  label, costUsd, calls, pricedCalls, heightPercent, emphasised,
}: {
  label: string
  costUsd: number
  calls: number
  pricedCalls: number
  heightPercent: number
  emphasised: boolean
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {/*
          The hit target is the whole column slot, full height, so a near-zero day is still
          hoverable — a 1px mark you cannot point at is a value you cannot read.
        */}
        <div
          className="flex h-full max-w-6 min-w-0 flex-1 cursor-default flex-col justify-end
                     rounded-sm hover:bg-muted/40"
          style={{ minWidth: MIN_COLUMN_PX }}
        >
          <span
            className={cn(
              'w-full rounded-t-[4px]',
              emphasised ? 'bg-foreground/80' : 'bg-muted-foreground/30',
              calls === 0 && 'bg-muted-foreground/15',
            )}
            // A day with calls but a rounding-error cost still gets a visible sliver; a day with
            // no calls at all gets a baseline tick, so "quiet" and "cheap" do not look identical.
            style={{ height: `${calls === 0 ? 1 : Math.max(heightPercent, 2)}%` }}
          />
        </div>
      </TooltipTrigger>
      <TooltipContent className="space-y-1">
        <p className="font-medium">{label}</p>
        <p>{formatSpend(costUsd)} over {calls.toLocaleString()} {calls === 1 ? 'call' : 'calls'}.</p>
        {pricedCalls < calls ? (
          <p className="text-muted-foreground">
            {pricedCalls.toLocaleString()} of them reported a price.
          </p>
        ) : null}
      </TooltipContent>
    </Tooltip>
  )
}
