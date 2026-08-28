import { Fragment } from 'react'
import { useQuery } from '@tanstack/react-query'
import { TrendingDown } from 'lucide-react'
import { getAggregateGaps } from '@/api/analyses'
import { keys } from '@/api/keys'
import type { AggregateGapEntry } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'

export function GapsPage() {
  const { profileId, isLoading: profileLoading } = useSelectedProfile()
  const gaps = useQuery({
    queryKey: keys.aggregate(profileId ?? -1),
    queryFn: () => getAggregateGaps(profileId!),
    enabled: profileId !== null,
  })

  if (profileLoading || (gaps.isPending && profileId !== null)) return <Skeleton className="h-64 w-full" />
  if (profileId === null) {
    return (
      <EmptyState
        icon={TrendingDown}
        title="No profile yet"
        description="Create a persona from the switcher in the sidebar to see cross-offer gaps."
      />
    )
  }
  if (gaps.isError) return <ApiErrorAlert error={gaps.error} />
  if (!gaps.data) return <Skeleton className="h-64 w-full" />

  /*
    Soft skills are reported but not scored, so they must not sit in the same ranked list as
    technical gaps — a "Communication" row above Kubernetes would suggest studying something no
    course fixes, and the match score already excludes it. Same table, separated and labelled,
    rather than hidden: the offers really did ask for these.
  */
  const byMustHaveGap = (a: AggregateGapEntry, b: AggregateGapEntry) =>
    b.mustHaveGapCount - a.mustHaveGapCount
  const technical = gaps.data.entries.filter((e) => e.category !== 'SOFT').sort(byMustHaveGap)
  const soft = gaps.data.entries.filter((e) => e.category === 'SOFT').sort(byMustHaveGap)
  const rows = [...technical, ...soft]

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
              {rows.map((entry, index) => {
                const ratio = entry.gapRatio ?? (
                  entry.demandCount === 0 ? 0 : entry.gapCount / entry.demandCount
                )
                const startsSoftSection = entry.category === 'SOFT' && index === technical.length
                return (
                  <Fragment key={entry.skillId}>
                    {startsSoftSection ? (
                      <TableRow className="hover:bg-transparent">
                        <TableCell colSpan={5} className="pt-6 text-xs text-muted-foreground">
                          Soft skills — asked for by these offers, but left out of the match score.
                          No catalog lookup can tell you whether you communicate well.
                        </TableCell>
                      </TableRow>
                    ) : null}
                  <TableRow>
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
                  </Fragment>
                )
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  )
}
