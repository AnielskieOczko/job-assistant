import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { ExternalLink, FileStack, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { getShortlist } from '@/api/analyses'
import { pasteOffer } from '@/api/offers'
import { keys } from '@/api/keys'
import { APPLICATION_STATUSES } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { ApplicationStatusBadge } from '@/components/StatusBadges'
import { usePrivacyManifest } from '@/hooks/usePrivacyManifest'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { formatRelative } from '@/lib/format'
import { sentDocumentsLabel } from '@/lib/sentDocuments'
import {
  comparatorFor, matchScoreLabel, OFFER_SORT_LABELS, OFFER_SORTS, scoredCoverageNote,
  type OfferSort,
} from '@/lib/shortlist'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'
import type { ShortlistEntry } from '@/api/types'

const ALL = '__all__'

export function OffersPage() {
  const { profileId } = useSelectedProfile()
  /*
    The offer list and the score arrive together. `GET /api/analyses/shortlist` is the join —
    resolving an analysis per row from here would be one request per offer, and the list has always
    been one request.
  */
  const offers = useQuery({
    queryKey: keys.shortlist(profileId),
    queryFn: () => getShortlist(profileId),
  })
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<string>(ALL)
  const [sort, setSort] = useState<OfferSort>('match')
  const [pasteOpen, setPasteOpen] = useState(false)

  const entries = offers.data?.entries
  const rows = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (entries ?? [])
      .filter((row) => status === ALL || row.application.status === status)
      .filter((row) => {
        if (!term) return true
        const title = row.offer.displayTitle ?? row.offer.title ?? row.offer.rawText
        return `${title} ${row.offer.company ?? ''}`.toLowerCase().includes(term)
      })
      // The server already ranks; re-sorting here is what makes the toggle instant. Both
      // comparators are total, so the same rows always land in the same order.
      .sort(comparatorFor(sort))
  }, [entries, search, status, sort])

  /*
    Filtering changes what is on screen but not what has been measured, so the caveat is computed
    over the whole shortlist rather than the visible rows: "3 of 10 scored" is a fact about the
    offers, not about the search box.
  */
  const coverageNote = offers.data
    ? scoredCoverageNote(offers.data.scored, offers.data.total)
    : null

  return (
    <>
      <PageHeader
        title="Offers"
        description="Paste a job offer, analyse it against your profile, then tailor a CV to it."
        actions={
          <Button onClick={() => setPasteOpen(true)}>
            <Plus /> Paste offer
          </Button>
        }
      />

      {offers.isError ? <ApiErrorAlert error={offers.error} /> : null}

      {offers.isPending ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-12 w-full" />)}
        </div>
      ) : offers.data && offers.data.total === 0 ? (
        <EmptyState
          icon={FileStack}
          title="No offers yet"
          description="Paste the text of a job posting to get started. Identical text is deduplicated, so re-pasting is safe."
          action={<Button onClick={() => setPasteOpen(true)}><Plus /> Paste offer</Button>}
        />
      ) : (
        <>
          <div className="mb-4 flex items-center gap-2">
            <Input
              placeholder="Search title or company…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="max-w-xs"
            />
            <Select value={status} onValueChange={setStatus}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All statuses</SelectItem>
                {APPLICATION_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>{s}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={sort} onValueChange={(value) => setSort(value as OfferSort)}>
              <SelectTrigger className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {OFFER_SORTS.map((option) => (
                  <SelectItem key={option} value={option}>{OFFER_SORT_LABELS[option]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <span className="ml-auto text-sm text-muted-foreground">
              {rows.length} of {offers.data?.total ?? 0}
            </span>
          </div>

          {/*
            Never a rank without saying what it ranks. Ten rows built from three analyses is a
            ranking of three, and the rows alone cannot say so.
          */}
          {coverageNote ? (
            <p className="mb-3 text-sm text-muted-foreground">{coverageNote}</p>
          ) : null}

          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Offer</TableHead>
                  <TableHead className="w-44">Company</TableHead>
                  <TableHead className="w-32">Seniority</TableHead>
                  <TableHead className="w-24">Match</TableHead>
                  <TableHead className="w-32">Status</TableHead>
                  <TableHead className="w-28">Sent</TableHead>
                  <TableHead className="w-32 text-right">Pasted</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map(({ offer, application, score }) => (
                  <TableRow key={offer.id}>
                    <TableCell className="max-w-0">
                      <div className="flex items-center gap-2">
                        <Link
                          to={`/offers/${offer.id}`}
                          className="truncate font-medium hover:underline"
                        >
                          {offer.displayTitle ?? offer.title ?? `Offer ${offer.id}`}
                        </Link>
                        {offer.sourceUrl ? (
                          <a
                            href={offer.sourceUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="text-muted-foreground hover:text-foreground"
                            title={offer.sourceUrl}
                          >
                            <ExternalLink className="size-3.5" />
                          </a>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{offer.company ?? '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{offer.seniority ?? '—'}</TableCell>
                    <TableCell><MatchScoreCell score={score} /></TableCell>
                    <TableCell><ApplicationStatusBadge status={application.status} /></TableCell>
                    {/*
                      What actually went to the employer, which "Applied" on its own does not say.
                      A dash means no document was recorded — not that none was sent, since an
                      application made outside the tool has none to name.
                    */}
                    <TableCell className="text-muted-foreground">
                      {sentDocumentsLabel(application) ?? '—'}
                    </TableCell>
                    <TableCell className="text-right text-sm text-muted-foreground">
                      {formatRelative(offer.createdAt)}
                    </TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                      No offers match those filters.
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </div>
        </>
      )}

      <PasteOfferDialog open={pasteOpen} onOpenChange={setPasteOpen} />
    </>
  )
}

/**
 * The score in a list row.
 *
 * A dash for an unscored offer, never `0%`. An offer nobody has analysed has not scored badly, and
 * a column you are about to sort on is the last place that difference may be blurred.
 *
 * A score from the old rule is labelled rather than quietly compared. Historical scores are never
 * recomputed, so the shortlist is exactly where a V1 and a V2 number end up side by side.
 */
function MatchScoreCell({ score }: { score: ShortlistEntry['score'] }) {
  if (score === null) {
    return (
      <span className="text-muted-foreground" title="Not analysed against this profile yet">
        —
      </span>
    )
  }

  // Rounded before the thresholds, so the colour cannot disagree with the number printed next
  // to it: 74.6% renders as "75%" and must be the same green a flat 75 gets.
  const percent = Math.round(score.matchScore * 100)
  const hue = percent >= 75 ? 'text-emerald-600' : percent >= 45 ? 'text-amber-600' : 'text-red-600'

  return (
    <span className={`font-medium tabular-nums ${hue}`}>
      {matchScoreLabel(score)}
      {score.scoringRule === 'V1_ALL_CATEGORIES' ? (
        <sup
          className="ml-0.5 cursor-help font-normal text-muted-foreground"
          title="Scored before soft skills were excluded, so they count toward this number. Not directly comparable with a newer score."
        >
          V1
        </sup>
      ) : null}
    </span>
  )
}

function PasteOfferDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [text, setText] = useState('')
  const [sourceUrl, setSourceUrl] = useState('')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const privacy = usePrivacyManifest()

  const paste = useMutation({
    mutationFn: () => pasteOffer({ text, sourceUrl: sourceUrl.trim() || null }),
    onSuccess: (result) => {
      // Both 201 and 200 are successes; the flag on the body is what distinguishes them.
      if (result.deduplicated) {
        toast.info('Already saved — opening the existing offer')
      } else {
        toast.success('Offer saved')
      }
      queryClient.invalidateQueries({ queryKey: keys.offers })
      onOpenChange(false)
      setText('')
      setSourceUrl('')
      navigate(`/offers/${result.offer.id}`)
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Paste a job offer</DialogTitle>
          <DialogDescription>
            The raw text is stored verbatim and hashed. Pasting the same posting twice returns the
            offer you already have.{' '}
            {privacy.data ? privacy.data.offerScrubbing : null}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="offer-text">Offer text</Label>
            <Textarea
              id="offer-text"
              rows={16}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Senior Kotlin Engineer…"
              className="max-h-64 overflow-y-auto font-mono text-xs"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="offer-url">Source URL (optional)</Label>
            <Input
              id="offer-url"
              value={sourceUrl}
              onChange={(e) => setSourceUrl(e.target.value)}
              placeholder="https://example.com/job/1"
            />
          </div>
          {paste.isError ? <ApiErrorAlert error={paste.error} /> : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button
            onClick={() => paste.mutate()}
            disabled={!text.trim() || paste.isPending}
          >
            {paste.isPending ? 'Saving…' : 'Save offer'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
