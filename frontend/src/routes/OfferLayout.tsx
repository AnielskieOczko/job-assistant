import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet } from 'react-router'
import { ExternalLink } from 'lucide-react'
import { getOffer } from '@/api/offers'
import { useOfferId } from '@/hooks/useOfferId'
import { keys } from '@/api/keys'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

const TABS = [
  { to: '.', label: 'Overview', end: true },
  { to: 'analysis', label: 'Analysis', end: false },
  { to: 'documents', label: 'Documents', end: false },
]

export function OfferLayout() {
  const offerId = useOfferId()
  const offer = useQuery({
    queryKey: keys.offer(offerId),
    queryFn: () => getOffer(offerId),
    enabled: Number.isFinite(offerId),
  })

  if (offer.isPending) return <Skeleton className="h-32 w-full" />
  if (offer.isError) return <ApiErrorAlert error={offer.error} />
  if (!offer.data) {
    return <EmptyState title="Offer not found" description={`No offer with id ${offerId}.`} />
  }

  const { data } = offer
  const meta = [data.company, data.seniority, data.detectedLanguage].filter(Boolean)

  return (
    <>
      <div className="mb-5">
        <h1 className="font-heading text-2xl font-semibold tracking-tight">
          {data.displayTitle ?? data.title ?? `Offer ${data.id}`}
        </h1>
        <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
          {meta.length > 0 ? <span>{meta.join(' · ')}</span> : <span>Not yet extracted</span>}
          {data.sourceUrl ? (
            <>
              <span>·</span>
              <a
                href={data.sourceUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 hover:text-foreground hover:underline"
              >
                Source <ExternalLink className="size-3" />
              </a>
            </>
          ) : null}
          <span>·</span>
          <span className="font-mono text-xs" title={data.contentHash}>
            {data.contentHash.slice(0, 12)}
          </span>
        </div>
      </div>

      <nav className="mb-6 flex gap-1 border-b">
        {TABS.map((tab) => (
          <NavLink
            key={tab.label}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              cn(
                '-mb-px border-b-2 px-3 py-2 text-sm transition-colors',
                isActive
                  ? 'border-primary font-medium text-foreground'
                  : 'border-transparent text-muted-foreground hover:text-foreground',
              )
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <Outlet />
    </>
  )
}
