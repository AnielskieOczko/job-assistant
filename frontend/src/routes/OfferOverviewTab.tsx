import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown } from 'lucide-react'
import { toast } from 'sonner'
import { getOffer, listOffers, updateOfferStatus } from '@/api/offers'
import { keys } from '@/api/keys'
import { APPLICATION_STATUSES } from '@/api/types'
import type { ApplicationStatus } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { CopyButton } from '@/components/CopyButton'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Collapsible, CollapsibleContent, CollapsibleTrigger,
} from '@/components/ui/collapsible'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { useOfferId } from '@/hooks/useOfferId'

/** `appliedOn` only means anything once you have actually applied. */
const APPLIED_ONWARD: ApplicationStatus[] = ['APPLIED', 'INTERVIEWING', 'REJECTED', 'OFFER']

export function OfferOverviewTab() {
  const offerId = useOfferId()
  const queryClient = useQueryClient()

  // The application record only comes back on the list endpoint, not GET /api/offers/{id}.
  const offers = useQuery({ queryKey: keys.offers, queryFn: listOffers })
  const offer = useQuery({ queryKey: keys.offer(offerId), queryFn: () => getOffer(offerId) })
  const application = offers.data?.find((row) => row.offer.id === offerId)?.application

  const [status, setStatus] = useState<ApplicationStatus>('SAVED')
  const [appliedOn, setAppliedOn] = useState('')
  const [notes, setNotes] = useState('')

  // Seed the form from the server record once it arrives, and again whenever a different record
  // is loaded. Done during render rather than in an effect so there is no throwaway first paint
  // showing the placeholder values.
  const [seeded, setSeeded] = useState<number | null>(null)
  if (application && seeded !== application.id) {
    setSeeded(application.id)
    setStatus(application.status)
    setAppliedOn(application.appliedOn ?? '')
    setNotes(application.notes ?? '')
  }

  const save = useMutation({
    mutationFn: () =>
      updateOfferStatus(offerId, {
        status,
        appliedOn: appliedOn || null,
        notes: notes.trim() || null,
      }),
    onSuccess: () => {
      toast.success('Application updated')
      queryClient.invalidateQueries({ queryKey: keys.offers })
      queryClient.invalidateQueries({ queryKey: keys.offer(offerId) })
    },
  })

  const dateEnabled = APPLIED_ONWARD.includes(status)

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
      <Card className="min-w-0">
        <CardHeader>
          <CardTitle className="text-base">Offer text</CardTitle>
        </CardHeader>
        <CardContent>
          <Collapsible defaultOpen>
            <div className="mb-3 flex items-center gap-2">
              <CollapsibleTrigger asChild>
                <Button variant="outline" size="sm" className="group">
                  <ChevronDown className="transition-transform group-data-[state=closed]:-rotate-90" />
                  Toggle
                </Button>
              </CollapsibleTrigger>
              <CopyButton value={offer.data?.rawText ?? ''} />
              <p className="ml-auto text-xs text-muted-foreground">
                Exactly what the extractor read.
              </p>
            </div>
            <CollapsibleContent>
              <pre className="max-h-[32rem] overflow-auto whitespace-pre-wrap rounded-md border bg-muted/40 p-4 font-mono text-xs leading-relaxed">
                {offer.data?.rawText}
              </pre>
            </CollapsibleContent>
          </Collapsible>
        </CardContent>
      </Card>

      <Card className="h-fit">
        <CardHeader>
          <CardTitle className="text-base">Application</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-1.5">
            <Label>Status</Label>
            <Select value={status} onValueChange={(v) => setStatus(v as ApplicationStatus)}>
              <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {APPLICATION_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>{s}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="applied-on" className={cn(!dateEnabled && 'text-muted-foreground')}>
              Applied on
            </Label>
            <Input
              id="applied-on"
              type="date"
              value={appliedOn}
              disabled={!dateEnabled}
              onChange={(e) => setAppliedOn(e.target.value)}
            />
            {!dateEnabled ? (
              <p className="text-xs text-muted-foreground">Available from “Applied” onward.</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="notes">Notes</Label>
            <Textarea
              id="notes"
              rows={4}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Referred by a friend…"
            />
          </div>

          {save.isError ? <ApiErrorAlert error={save.error} /> : null}

          <Button className="w-full" onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
