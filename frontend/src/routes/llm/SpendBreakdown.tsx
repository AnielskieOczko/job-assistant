import type { SpendGroup, SpendTotal } from '@/api/types'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { formatCallCost, formatSpend } from '@/lib/format'
import { share } from './format'

/**
 * Where the money went.
 *
 * A table rather than more charts: five or six named groups with six measures each is exactly the
 * case the chart form stops helping — every column would need its own axis, and the reader's real
 * question ("which task is expensive per call") is a comparison between two numbers on one row.
 *
 * Every rate carries its raw counts. An average cost per call over one call and over ninety render
 * identically otherwise, and only one of them means anything.
 */
export function SpendBreakdown({ title, caption, groups, windowTotal }: {
  title: string
  caption: string
  groups: SpendGroup[]
  windowTotal: SpendTotal
}) {
  if (groups.length === 0) return null

  return (
    <section>
      <h2 className="text-sm font-medium">{title}</h2>
      <p className="mt-1 mb-3 text-xs text-muted-foreground">{caption}</p>
      <div className="overflow-x-auto rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead className="w-24 text-right">Calls</TableHead>
              <TableHead className="w-32 text-right">Tokens in/out</TableHead>
              <TableHead className="w-24 text-right">Cached</TableHead>
              <TableHead className="w-28 text-right">Avg / call</TableHead>
              <TableHead className="w-28 text-right">Total</TableHead>
              <TableHead className="w-20 text-right">Share</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {groups.map(({ key, total }) => (
              <TableRow key={key}>
                <TableCell className="font-mono text-xs">{key}</TableCell>
                <TableCell className="text-right text-xs tabular-nums">
                  {total.calls.toLocaleString()}
                  {/* A group whose calls were not all priced has a total that is a floor, and the
                      place to say so is beside the count it is a floor over. */}
                  {total.pricedCalls < total.calls ? (
                    <span className="text-muted-foreground">
                      {' '}({total.pricedCalls.toLocaleString()} priced)
                    </span>
                  ) : null}
                </TableCell>
                <TableCell className="text-right text-xs tabular-nums text-muted-foreground">
                  {total.inputTokens.toLocaleString()} / {total.outputTokens.toLocaleString()}
                </TableCell>
                <TableCell className="text-right text-xs tabular-nums text-muted-foreground">
                  {share(total.cachedInputTokens, total.inputTokens) ?? '—'}
                </TableCell>
                <TableCell className="text-right text-xs tabular-nums text-muted-foreground">
                  {/* Divided by the priced calls, not by all of them: averaging a partial sum over
                      a full count understates the price of a call that actually cost something. */}
                  {total.pricedCalls > 0
                    ? formatCallCost(total.costUsd / total.pricedCalls)
                    : '—'}
                </TableCell>
                <TableCell className="text-right text-xs font-medium tabular-nums">
                  {formatSpend(total.costUsd)}
                </TableCell>
                <TableCell className="text-right text-xs tabular-nums text-muted-foreground">
                  {share(total.costUsd, windowTotal.costUsd) ?? '—'}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  )
}
