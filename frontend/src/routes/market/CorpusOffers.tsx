import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ExternalLink } from 'lucide-react'
import { fetchMarketOffers } from '@/api/market'
import { keys } from '@/api/keys'
import type { MarketOfferSummary } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { formatBand } from './format'

const PAGE = 50

/**
 * The offers behind the numbers, so a row can be opened rather than trusted.
 *
 * These are **market offers, not job offers**. A `job_offer` is something you might apply to and
 * carries an application status, a profile revision, analyses and generated documents; a row here
 * is one observation in a sample. Nothing on this screen copies one into the other — saving an
 * offer for real is a deliberate action and is not part of this dashboard.
 */
export function CorpusOffers({ profileId }: { profileId: number | null }) {
  const [offset, setOffset] = useState(0)
  const offers = useQuery({
    queryKey: keys.marketOffers(profileId, PAGE, offset),
    queryFn: () => fetchMarketOffers({ profileId: profileId ?? undefined, limit: PAGE, offset }),
  })

  if (offers.isPending) return <Skeleton className="h-96 w-full" />
  if (offers.isError) return <ApiErrorAlert error={offers.error} />

  const { entries, total } = offers.data
  if (total === 0) {
    return (
      <EmptyState
        title="No in-scope offers in the corpus"
        description="Either nothing has been ingested yet, or no ingested offer asks for a scope skill."
      />
    )
  }

  const last = Math.min(offset + entries.length, total)

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-4">
        <p className="text-xs text-muted-foreground">
          Showing {(offset + 1).toLocaleString()}–{last.toLocaleString()} of{' '}
          {total.toLocaleString()} currently-valid in-scope offers. Coverage counts held or
          reachable skills, the same expansion the demand table uses.
        </p>
        <div className="flex shrink-0 gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={offset === 0}
            onClick={() => setOffset(Math.max(offset - PAGE, 0))}
          >
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={last >= total}
            onClick={() => setOffset(offset + PAGE)}
          >
            Next
          </Button>
        </div>
      </div>

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Offer</TableHead>
              <TableHead>Level</TableHead>
              <TableHead>Where</TableHead>
              <TableHead>Salary</TableHead>
              <TableHead>Your coverage</TableHead>
              <TableHead className="text-right">Valid to</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.map((offer) => (
              <OfferRow key={offer.id} offer={offer} />
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

function OfferRow({ offer }: { offer: MarketOfferSummary }) {
  const salary = offer.salary
    ? formatBand({
        medianFrom: offer.salary.from,
        medianTo: offer.salary.to,
        currency: offer.salary.currency,
        period: offer.salary.period,
      })
    : null

  return (
    <TableRow>
      <TableCell className="max-w-xs">
        <div className="flex items-center gap-1.5">
          <span className="truncate font-medium">{offer.title}</span>
          {offer.url ? (
            <a
              href={offer.url}
              target="_blank"
              rel="noreferrer"
              className="shrink-0 text-muted-foreground hover:text-foreground"
              aria-label={`Open ${offer.title} on ${offer.source}`}
            >
              <ExternalLink className="size-3.5" />
            </a>
          ) : null}
        </div>
        <p className="truncate text-xs text-muted-foreground">
          {offer.company ?? 'Company unstated'} · {offer.source}
        </p>
      </TableCell>

      <TableCell className="text-sm text-muted-foreground">
        {offer.experienceLevel ?? '—'}
      </TableCell>

      <TableCell className="max-w-40 text-sm">
        <WorkMode offer={offer} />
        {offer.locations.length > 0 ? (
          <p className="truncate text-xs text-muted-foreground">{offer.locations.join(', ')}</p>
        ) : null}
      </TableCell>

      <TableCell className="text-sm">
        {salary ? (
          <>
            {salary}{' '}
            <span className="text-xs text-muted-foreground">
              {offer.salary?.employmentType ?? ''}
            </span>
          </>
        ) : (
          <span className="text-xs text-muted-foreground">Not stated</span>
        )}
      </TableCell>

      {/*
        The unresolved count is never dropped from this cell. An offer whose every *resolved* skill
        is covered still asks for whatever the catalog could not place: measured on the corpus, of
        ten in-scope offers with nothing missing, nine looked that way only because their unplaced
        terms had been silently discarded. "6 of 6" beside "3 unplaced" is the honest rendering.
      */}
      <TableCell className="text-sm tabular-nums">
        {offer.skillsCovered} of {offer.skillsResolved}
        {offer.skillsUnresolved > 0 ? (
          <span className="text-xs text-muted-foreground">
            {' '}
            + {offer.skillsUnresolved} unplaced
          </span>
        ) : null}
      </TableCell>

      <TableCell className="text-right text-xs text-muted-foreground">
        {formatDate(offer.validTo)}
      </TableCell>
    </TableRow>
  )
}

/**
 * Remote and hybrid are independent flags and a meaningful number of offers set both.
 *
 * Rendering them as two badges would let the same offer be counted twice by eye; rendering only
 * one would drop half of what it said. Naming the overlap is the only option that does neither.
 */
function WorkMode({ offer }: { offer: MarketOfferSummary }) {
  const label = offer.isRemote && offer.isHybrid
    ? 'Remote or hybrid'
    : offer.isRemote
      ? 'Remote'
      : offer.isHybrid
        ? 'Hybrid'
        : 'On-site'

  return (
    <Badge variant="outline" className="text-muted-foreground">
      {label}
    </Badge>
  )
}
