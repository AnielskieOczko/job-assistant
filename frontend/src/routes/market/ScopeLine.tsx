import { Link } from 'react-router'
import { AlertTriangle } from 'lucide-react'
import type { MarketScopeReport } from '@/api/types'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { formatDate } from '@/lib/format'
import { percent, plural } from './format'

/**
 * The population every number on this page is measured over.
 *
 * Rendered above the statistics, never beneath them. A median with no source, no window and no
 * size is indistinguishable from an accident, and this line is the whole of that context.
 *
 * Note what it does *not* say: never "the market". One board, one division, one window — the
 * source is named on the line and the corpus size sits beside the scope size so a reader can see
 * how narrow the slice is.
 */
export function ScopeLine({ scope }: { scope: MarketScopeReport }) {
  const sources = scope.sources.length > 0 ? scope.sources.join(', ') : 'no source yet'
  const skills = scope.scopeSkills.length > 0 ? scope.scopeSkills.join(' · ') : 'nothing configured'
  // Compared as rendered dates, not as timestamps: one poll spans a few seconds, so two instants
  // that differ still name the same day and printing "Aug 28 – Aug 28" would imply a window the
  // corpus does not have.
  const first = formatDate(scope.firstSeenAt)
  const last = formatDate(scope.lastSeenAt)
  const window = first === last ? last : `${first} – ${last}`

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        <span className="font-medium text-foreground">{sources}</span>
        {' · '}
        {skills}
        {' · '}
        {/*
          offersInScope is currently-valid offers only; the expired ones are a disjoint count and
          are excluded from every statistic on the page. Naming both is the difference between
          "now" and "since we started looking" — the corpus is never pruned, so without this the
          medians would quietly drift into being historical.
        */}
        <span className="font-medium text-foreground">
          {plural(scope.offersInScope, 'offer')} in scope
        </span>
        {scope.expiredInScope > 0
          ? `, ${scope.expiredInScope.toLocaleString()} expired and excluded`
          : null}
        {' · '}
        {scope.corpusOffers.toLocaleString()} in the corpus
        {' · '}
        polled {window}
      </p>

      {scope.unresolvedScopeSkills.length > 0 ? (
        <Alert variant="destructive">
          <AlertTriangle />
          <AlertTitle>Part of the configured scope resolved to nothing</AlertTitle>
          <AlertDescription>
            <p>
              {scope.unresolvedScopeSkills.join(', ')} — the catalog has no skill by that name, so
              offers asking for it were never counted as in scope. Every number below is narrower
              than intended until the name is fixed or added to the catalog.
            </p>
          </AlertDescription>
        </Alert>
      ) : null}

      <BlindSpot scope={scope} />
    </div>
  )
}

/**
 * The ceiling on every demand claim this page makes.
 *
 * Not a footnote. Roughly a quarter to a third of what in-scope offers ask for is vocabulary the
 * catalog cannot place, and a ranking that does not say so implies the catalog saw everything.
 * The terms are already queued for review under `market_occurrences`, so this links there rather
 * than merely complaining.
 */
function BlindSpot({ scope }: { scope: MarketScopeReport }) {
  if (scope.skillMentions === 0) return null
  const share = scope.unresolvedMentions / scope.skillMentions
  if (scope.unresolvedMentions === 0) return null

  return (
    <p className="rounded-md border border-dashed px-3 py-2 text-xs text-muted-foreground">
      <span className="font-medium text-foreground">
        {percent(share)} of what these offers ask for is unplaced
      </span>{' '}
      — {scope.unresolvedMentions.toLocaleString()} of{' '}
      {scope.skillMentions.toLocaleString()} in-scope skill mentions are terms the catalog has no
      entry for. They are neither covered nor missing, so no ranking below can claim to be
      complete. They are waiting in the{' '}
      <Link to="/catalog" className="underline underline-offset-4 hover:text-foreground">
        review queue
      </Link>
      .
    </p>
  )
}
