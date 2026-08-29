import type { DemandEntry } from '@/api/types'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { CoverageStatusBadge } from '@/components/StatusBadges'
import { cn } from '@/lib/utils'
import {
  MARKET_LEVEL_LABELS, bandLabel, coveredBy, dominantLevel, percent, plural,
} from './format'

/** Rows the chart shows. Past this the table is the better surface and it sits right below. */
const BARS = 8

/** Track share the largest bar occupies. The rest is where its value label sits. */
const LONGEST_BAR = 92

/**
 * The one chart on this page.
 *
 * **Emphasis, not categorical.** Every bar is the same measure — offers asking for a skill — so
 * giving each skill its own hue would encode identity that the row label already carries, and
 * would bury the thing the chart exists to show. Instead one hue: skills the profile does not
 * cover are drawn in the accent, the covered ones recede to gray. The reader's eye lands on the
 * gaps because the gaps are the subject.
 *
 * There is no legend box, because there is no second series to tell apart — the caption names what
 * the emphasis means, and every bar carries its status as a labelled badge in the tooltip and in
 * the table below. Identity is never colour alone.
 *
 * The bar length is an **observed** count: "this many offers ask for it and you do not have it".
 * It is never "this many offers you would win". See issue #47, decision 1, for the measurement
 * that kept the counterfactual out of v1.
 */
export function UnmetDemandChart({
  entries,
  offersInScope,
}: {
  entries: DemandEntry[]
  offersInScope: number
}) {
  const rows = entries.slice(0, BARS)
  // A chart of one or two bars is a table with extra ink. Below that threshold the demand table
  // already says everything this would.
  if (rows.length < 3) return null

  const max = Math.max(...rows.map((entry) => entry.offers))

  return (
    <figure className="rounded-lg border p-5">
      <figcaption className="mb-1 text-sm font-medium">
        Most-asked skills, gaps first
      </figcaption>
      <p className="mb-5 text-xs text-muted-foreground">
        In-scope offers asking for each skill, out of {offersInScope.toLocaleString()}. Solid bars
        are skills your profile does not cover; grey ones it does.
      </p>

      <div className="flex flex-col gap-2.5">
        {rows.map((entry) => (
          <Bar key={entry.skillId} entry={entry} max={max} offersInScope={offersInScope} />
        ))}
      </div>
    </figure>
  )
}

function Bar({
  entry,
  max,
  offersInScope,
}: {
  entry: DemandEntry
  max: number
  offersInScope: number
}) {
  // Scaled to LONGEST_BAR rather than to the full track, so the value can ride its own bar's tip
  // without the longest one pushing its label off the edge. Every bar is scaled by the same
  // factor, so the comparison the chart exists for is untouched — there is no axis to read against.
  const width = max === 0 ? 0 : (entry.offers / max) * LONGEST_BAR
  const isGap = entry.status !== 'MET'
  const level = dominantLevel(entry.levelMix)
  const salary = entry.salary ? bandLabel(entry.salary) : null

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className="flex items-center gap-3 rounded-sm py-0.5 text-left hover:bg-muted/40">
          <span className="w-32 shrink-0 truncate text-xs text-muted-foreground">
            {entry.skillName}
          </span>
          {/*
            20px thick, square at the baseline and 4px rounded at the data end, with the value
            beside the tip rather than parked in a far-right column — a value that drifts away from
            its own mark stops reading as that mark's value. Text stays in ink tokens throughout;
            the bar beside it is what carries the emphasis.
          */}
          <span className="flex min-w-0 flex-1 items-center gap-2">
            <span
              className={cn(
                'h-5 shrink-0 rounded-r-[4px]',
                isGap ? 'bg-foreground/80' : 'bg-muted-foreground/25',
              )}
              style={{ width: `${Math.max(width, 1)}%` }}
            />
            <span
              className={cn(
                'text-xs tabular-nums',
                isGap ? 'font-medium text-foreground' : 'text-muted-foreground',
              )}
            >
              {entry.offers.toLocaleString()}
            </span>
          </span>
        </div>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs space-y-1.5">
        <p className="font-medium">{entry.skillName}</p>
        <p>
          {plural(entry.offers, 'offer')} of {offersInScope.toLocaleString()} in scope ask for it;{' '}
          {entry.requiredOffers.toLocaleString()} as a requirement rather than a nice-to-have.
        </p>
        <div>
          <CoverageStatusBadge status={entry.status} coveredBy={coveredBy(entry)} />
        </div>
        {level ? (
          <p>
            Asked at {MARKET_LEVEL_LABELS[level.level]} in {percent(level.share)} of them (
            {level.count} of {level.total}).
          </p>
        ) : null}
        {salary ? <p>Those offers pay {salary}.</p> : null}
      </TooltipContent>
    </Tooltip>
  )
}
