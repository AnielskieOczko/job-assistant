import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { ExternalLink, FileStack, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { listOffers, pasteOffer } from '@/api/offers'
import { keys } from '@/api/keys'
import { APPLICATION_STATUSES } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { ApplicationStatusBadge } from '@/components/StatusBadges'
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

const ALL = '__all__'

export function OffersPage() {
  const offers = useQuery({ queryKey: keys.offers, queryFn: listOffers })
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<string>(ALL)
  const [pasteOpen, setPasteOpen] = useState(false)

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (offers.data ?? [])
      .filter((row) => status === ALL || row.application.status === status)
      .filter((row) => {
        if (!term) return true
        const title = row.offer.displayTitle ?? row.offer.title ?? row.offer.rawText
        return `${title} ${row.offer.company ?? ''}`.toLowerCase().includes(term)
      })
      .sort((a, b) => b.offer.createdAt.localeCompare(a.offer.createdAt))
  }, [offers.data, search, status])

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
      ) : offers.data && offers.data.length === 0 ? (
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
            <span className="ml-auto text-sm text-muted-foreground">
              {rows.length} of {offers.data?.length ?? 0}
            </span>
          </div>

          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Offer</TableHead>
                  <TableHead className="w-44">Company</TableHead>
                  <TableHead className="w-32">Seniority</TableHead>
                  <TableHead className="w-32">Status</TableHead>
                  <TableHead className="w-28">Sent</TableHead>
                  <TableHead className="w-32 text-right">Pasted</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map(({ offer, application }) => (
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
                    <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
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
            offer you already have.
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
