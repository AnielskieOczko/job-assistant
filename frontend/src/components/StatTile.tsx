import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/**
 * One figure in the KPI row, and the denominator it was measured over.
 *
 * [caption] is required rather than optional on purpose. A median over eleven offers and a median
 * over four hundred render identically unless the count travels with the number, and the whole
 * reason for ingesting a corpus was to stop guessing. The spend dashboard needs the same
 * discipline for a different reason - a total over calls that reported no price is a floor - which
 * is why this lives in `components/` rather than in either page.
 *
 * A tile whose statistic did not clear its honesty floor still renders — carrying the count and
 * the words instead of the number ([belowFloor]). It is never greyed out: a greyed-out tile reads
 * as still loading, which is a different claim from "too few offers to say".
 */
export function StatTile({
  label,
  value,
  caption,
  belowFloor = false,
}: {
  label: string
  value: ReactNode
  caption: ReactNode
  belowFloor?: boolean
}) {
  return (
    <div className="rounded-lg border p-4">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p
        className={cn(
          'mt-1.5 font-semibold tracking-tight',
          // Proportional figures: tabular-nums gives every digit the width of a zero, which reads
          // loose at display sizes. It is reserved for the columns of the demand table.
          belowFloor ? 'text-base leading-snug text-foreground' : 'text-xl',
        )}
      >
        {value}
      </p>
      <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">{caption}</p>
    </div>
  )
}
