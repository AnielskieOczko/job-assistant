import { useQuery } from '@tanstack/react-query'
import { TrendingDown } from 'lucide-react'
import { getAggregateGaps } from '@/api/analyses'
import { keys } from '@/api/keys'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

export function GapsPage() {
  const gaps = useQuery({ queryKey: keys.aggregate, queryFn: getAggregateGaps })

  if (gaps.isPending) return <Skeleton className="h-64 w-full" />
  if (gaps.isError) return <ApiErrorAlert error={gaps.error} />

  const rows = [...gaps.data.entries].sort((a, b) => b.mustHaveGapCount - a.mustHaveGapCount)

  return (
    <>
      <PageHeader
        title="Cross-offer gaps"
        description={`Across ${gaps.data.analysedOffers} analysed offer${
          gaps.data.analysedOffers === 1 ? '' : 's'
        }, counting each once by its most recent completed analysis. A single offer only tells you about one job — this is the number that should drive what you learn next.`}
      />

      {gaps.data.analysedOffers === 0 ? (
        <EmptyState
          icon={TrendingDown}
          title="Nothing analysed yet"
          description="Analyse a few offers and the skills they keep asking for will show up here."
        />
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Skill</TableHead>
                <TableHead className="w-28 text-right">Demanded</TableHead>
                <TableHead className="w-28 text-right">Gap</TableHead>
                <TableHead className="w-32 text-right">Must-have gap</TableHead>
                <TableHead className="w-48">Gap ratio</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((entry) => {
                const ratio = entry.gapRatio ?? (
                  entry.demandCount === 0 ? 0 : entry.gapCount / entry.demandCount
                )
                return (
                  <TableRow key={entry.skillId}>
                    <TableCell className="font-medium">{entry.skillName}</TableCell>
                    <TableCell className="text-right text-muted-foreground">
                      {entry.demandCount}
                    </TableCell>
                    <TableCell className="text-right text-muted-foreground">
                      {entry.gapCount}
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {entry.mustHaveGapCount}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                          <div
                            className="h-full rounded-full bg-red-500/70"
                            style={{ width: `${Math.round(ratio * 100)}%` }}
                          />
                        </div>
                        <span className="w-10 text-right text-xs text-muted-foreground">
                          {Math.round(ratio * 100)}%
                        </span>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  )
}
