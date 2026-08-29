import { useState } from 'react'
import type { ReactNode } from 'react'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import type { DemandEntry, DemandRanking, DemandReport } from '@/api/types'
import { CoverageStatusBadge } from '@/components/StatusBadges'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import {
  MARKET_LEVEL_LABELS, coveredBy, dominantLevel, employmentLabel, formatBand, percent,
} from './format'

type Column = 'skillName' | 'offers' | 'requiredOffers' | 'status'
type Sort = { column: Column; descending: boolean }

/**
 * Where a coverage verdict sits when the table is sorted by it.
 *
 * Spelled out rather than taken from the order the statuses happen to be declared in, for the
 * reason `MarketInsightsService.unmetRank` gives on the server side: reordering that enum for
 * readability would silently invert what this column means, with no test naming the connection.
 */
const UNMET_RANK: Record<DemandEntry['status'], number> = { MISSING: 0, PARTIAL: 1, MET: 2 }

const RANKING_LABELS: Record<DemandRanking, string> = {
  UNMET: 'Unmet demand',
  TOTAL: 'Total demand',
}

/**
 * The primary surface of the dashboard: what the scope asks for, and what of it you cover.
 *
 * Two orderings are in play and they are not the same thing, which is why the distinction is
 * printed above the table rather than left to be inferred:
 *
 *   - **[DemandReport.ranking]** decides which skills are on this page at all. It runs on the
 *     server, over every skill in scope, and the response is the top `limit` of them.
 *   - **The column sort** reorders the page in the browser. It cannot pull in a skill the ranking
 *     left off, so sorting by offer count shows the most-asked *of the unmet ones*, not the
 *     most-asked overall — switch the ranking for that.
 */
export function DemandTable({
  report,
  ranking,
  onRankingChange,
  dominantEmploymentType,
}: {
  report: DemandReport
  ranking: DemandRanking
  onRankingChange: (ranking: DemandRanking) => void
  /** The contract type the per-row bands were measured over, so a missing band can say which. */
  dominantEmploymentType: string | null
}) {
  const [sort, setSort] = useState<Sort | null>(null)
  const rows = sort ? [...report.entries].sort(comparator(sort)) : report.entries
  const truncated = report.skillsInScope > report.entries.length

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex items-center gap-2">
          <Label htmlFor="demand-ranking" className="text-sm font-normal text-muted-foreground">
            Rank by
          </Label>
          <Select value={ranking} onValueChange={(value) => onRankingChange(value as DemandRanking)}>
            <SelectTrigger id="demand-ranking" className="w-44" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="UNMET">{RANKING_LABELS.UNMET}</SelectItem>
              <SelectItem value="TOTAL">{RANKING_LABELS.TOTAL}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <p className="text-xs text-muted-foreground">
          {truncated
            ? `Showing ${report.entries.length} of ${report.skillsInScope.toLocaleString()} skills the scope asks for, taken by ${RANKING_LABELS[ranking].toLowerCase()}.`
            : `All ${report.skillsInScope.toLocaleString()} skills the scope asks for.`}{' '}
          {report.unmetSkillsInScope.toLocaleString()} of them your profile does not cover at all.
          {sort ? ' Sorting reorders this page; it does not change which skills are on it.' : null}
        </p>
      </div>

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <SortableHead column="skillName" sort={sort} setSort={setSort}>Skill</SortableHead>
              <SortableHead column="offers" sort={sort} setSort={setSort} numeric>
                Offers asking
              </SortableHead>
              <SortableHead column="requiredOffers" sort={sort} setSort={setSort} numeric>
                Required
              </SortableHead>
              <SortableHead column="status" sort={sort} setSort={setSort}>Your status</SortableHead>
              <TableHead>Asked at</TableHead>
              <TableHead>These offers pay</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((entry) => (
              <DemandRow
                key={entry.skillId}
                entry={entry}
                offersInScope={report.scope.offersInScope}
                dominantEmploymentType={dominantEmploymentType}
              />
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

function DemandRow({
  entry,
  offersInScope,
  dominantEmploymentType,
}: {
  entry: DemandEntry
  offersInScope: number
  dominantEmploymentType: string | null
}) {
  const level = dominantLevel(entry.levelMix)
  const band = entry.salary ? formatBand(entry.salary) : null

  return (
    <TableRow>
      <TableCell className="font-medium">{entry.skillName}</TableCell>

      <TableCell className="text-right tabular-nums">
        <span className="font-medium">{entry.offers.toLocaleString()}</span>{' '}
        <span className="text-xs text-muted-foreground">
          of {offersInScope.toLocaleString()}
        </span>
      </TableCell>

      <TableCell className="text-right tabular-nums text-muted-foreground">
        {entry.requiredOffers.toLocaleString()}
      </TableCell>

      <TableCell>
        <CoverageStatusBadge status={entry.status} coveredBy={coveredBy(entry)} />
      </TableCell>

      <TableCell className="text-sm">
        {level ? (
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="cursor-default">
                {MARKET_LEVEL_LABELS[level.level]}{' '}
                <span className="text-xs text-muted-foreground">
                  in {percent(level.share)} of {level.total}
                </span>
              </span>
            </TooltipTrigger>
            <TooltipContent className="space-y-0.5">
              {(Object.entries(entry.levelMix) as [keyof typeof MARKET_LEVEL_LABELS, number][])
                .sort((a, b) => b[1] - a[1])
                .map(([name, count]) => (
                  <p key={name}>
                    {MARKET_LEVEL_LABELS[name]}: {count}
                  </p>
                ))}
            </TooltipContent>
          </Tooltip>
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </TableCell>

      {/*
        A null band is "too few offers to say", never "these pay nothing", so the cell says which
        and names the floor it failed. A blank here would read as a number that failed to load.
      */}
      <TableCell className="text-sm">
        {band ? (
          <>
            {band}{' '}
            <span className="text-xs text-muted-foreground">
              {employmentLabel(entry.salary?.employmentType)}, n={entry.salary?.offers}
            </span>
          </>
        ) : (
          <span className="text-xs text-muted-foreground">
            Under 5 {dominantEmploymentType ? `${dominantEmploymentType} ` : ''}offers — too few
            for a band
          </span>
        )}
      </TableCell>
    </TableRow>
  )
}

function SortableHead({
  column,
  sort,
  setSort,
  numeric = false,
  children,
}: {
  column: Column
  sort: Sort | null
  setSort: (sort: Sort) => void
  numeric?: boolean
  children: ReactNode
}) {
  const active = sort?.column === column
  const Icon = active ? (sort.descending ? ArrowDown : ArrowUp) : ArrowUpDown

  return (
    <TableHead className={numeric ? 'text-right' : undefined}>
      <Button
        variant="ghost"
        size="sm"
        className="-mx-2 h-7 px-2 font-medium text-muted-foreground data-[active=true]:text-foreground"
        data-active={active}
        onClick={() => setSort({ column, descending: active ? !sort.descending : defaultDescending(column) })}
      >
        {children}
        <Icon className="size-3" />
      </Button>
    </TableHead>
  )
}

/** Counts read high-to-low; names and coverage read best-first, which is A→Z and MISSING first. */
const defaultDescending = (column: Column) => column === 'offers' || column === 'requiredOffers'

function comparator(sort: Sort) {
  const direction = sort.descending ? -1 : 1
  return (a: DemandEntry, b: DemandEntry) => {
    const delta =
      sort.column === 'skillName'
        ? a.skillName.localeCompare(b.skillName)
        : sort.column === 'status'
          ? UNMET_RANK[a.status] - UNMET_RANK[b.status]
          : a[sort.column] - b[sort.column]
    // Without a total order two rows with equal counts can swap between renders, which reads as
    // the table flickering for no reason. The name is the tiebreak on every column.
    return delta !== 0 ? delta * direction : a.skillName.localeCompare(b.skillName)
  }
}
