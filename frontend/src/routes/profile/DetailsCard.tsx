import { useState } from 'react'
import { Pencil, Plus } from 'lucide-react'
import { addLink, deleteLink, putDetails, reorderLinks, updateLink } from '@/api/profile'
import type { CandidateProfile, ProfileLink } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDelete } from './ConfirmDelete'
import { Field } from './Field'
import { RowActions } from './RowActions'
import { blankToNull, movedIds, useProfileEdit } from './mutations'

export function DetailsCard({ profile }: { profile: CandidateProfile }) {
  const [editing, setEditing] = useState(false)
  const [linkDialog, setLinkDialog] = useState<ProfileLink | 'new' | null>(null)
  const [deleting, setDeleting] = useState<ProfileLink | null>(null)

  const removeLink = useProfileEdit(deleteLink, 'Link removed')
  const reorder = useProfileEdit(reorderLinks, 'Links reordered')
  const { details, links } = profile

  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 className="font-heading text-xl font-semibold">{details.fullName}</h2>
            {details.headline ? <p className="text-muted-foreground">{details.headline}</p> : null}
            <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-sm text-muted-foreground">
              {[details.email, details.phone, details.location]
                .filter(Boolean)
                .map((value) => <span key={value}>{value}</span>)}
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
            <Pencil /> Edit
          </Button>
        </div>

        {details.summary ? (
          <p className="mt-4 text-sm leading-relaxed">{details.summary}</p>
        ) : null}

        <div className="mt-4 border-t pt-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Links</p>
            <Button variant="ghost" size="sm" onClick={() => setLinkDialog('new')}>
              <Plus /> Add link
            </Button>
          </div>
          {links.length === 0 ? (
            <p className="mt-1 text-sm text-muted-foreground">No links yet.</p>
          ) : (
            <ul className="mt-1 divide-y">
              {links.map((link, index) => (
                <li key={link.id} className="flex items-center justify-between gap-3 py-1.5">
                  <a
                    href={link.url}
                    target="_blank"
                    rel="noreferrer"
                    className="min-w-0 truncate text-sm underline underline-offset-2"
                  >
                    {link.label}
                    <span className="ml-2 text-muted-foreground">{link.url}</span>
                  </a>
                  <RowActions
                    label={link.label}
                    disabled={reorder.isPending}
                    onUp={index > 0 ? () => reorder.mutate(movedIds(links, index, index - 1)) : undefined}
                    onDown={
                      index < links.length - 1
                        ? () => reorder.mutate(movedIds(links, index, index + 1))
                        : undefined
                    }
                    onEdit={() => setLinkDialog(link)}
                    onDelete={() => setDeleting(link)}
                  />
                </li>
              ))}
            </ul>
          )}
          {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
        </div>
      </CardContent>

      <DetailsDialog profile={profile} open={editing} onOpenChange={setEditing} />
      <LinkDialog link={linkDialog} onClose={() => setLinkDialog(null)} />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); removeLink.reset() }
        }}
        title={`Remove ${deleting?.label ?? 'link'}?`}
        description="The link disappears from the header of any CV generated from now on."
        pending={removeLink.isPending}
        error={removeLink.error}
        onConfirm={() => {
          if (deleting) removeLink.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }}
      />
    </Card>
  )
}

function DetailsDialog({
  profile,
  open,
  onOpenChange,
}: {
  profile: CandidateProfile
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [seeded, setSeeded] = useState<number | null>(null)
  const [fullName, setFullName] = useState('')
  const [headline, setHeadline] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [location, setLocation] = useState('')
  const [summary, setSummary] = useState('')

  // Seed during render rather than in an effect - the idiom the rest of the app uses.
  if (open && seeded !== profile.revision) {
    setSeeded(profile.revision)
    setFullName(profile.details.fullName)
    setHeadline(profile.details.headline ?? '')
    setEmail(profile.details.email ?? '')
    setPhone(profile.details.phone ?? '')
    setLocation(profile.details.location ?? '')
    setSummary(profile.details.summary ?? '')
  }

  const save = useProfileEdit(putDetails, 'Details saved')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Edit details</DialogTitle>
          <DialogDescription>These appear in the header of every generated document.</DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <Field label="Full name" value={fullName} onChange={setFullName} />
          <Field label="Headline" value={headline} onChange={setHeadline} placeholder="Backend Engineer" />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Email" value={email} onChange={setEmail} />
            <Field label="Phone" value={phone} onChange={setPhone} />
          </div>
          <Field label="Location" value={location} onChange={setLocation} />
          <div className="space-y-1.5">
            <Label htmlFor="details-summary">Summary</Label>
            <Textarea
              id="details-summary"
              rows={4}
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
            />
          </div>
        </div>

        {save.isError ? <ApiErrorAlert error={save.error} /> : null}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button
            disabled={!fullName.trim() || save.isPending}
            onClick={() =>
              save.mutate(
                {
                  fullName: fullName.trim(),
                  headline: blankToNull(headline),
                  email: blankToNull(email),
                  phone: blankToNull(phone),
                  location: blankToNull(location),
                  summary: blankToNull(summary),
                },
                { onSuccess: () => onOpenChange(false) },
              )
            }
          >
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function LinkDialog({ link, onClose }: { link: ProfileLink | 'new' | null; onClose: () => void }) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [label, setLabel] = useState('')
  const [url, setUrl] = useState('')

  const key = link === 'new' ? 'new' : (link?.id ?? null)
  if (link !== null && seeded !== key) {
    setSeeded(key)
    setLabel(link === 'new' ? '' : link.label)
    setUrl(link === 'new' ? '' : link.url)
  }

  const create = useProfileEdit(addLink, 'Link added')
  const update = useProfileEdit(
    (args: { id: number; label: string; url: string }) => updateLink(args.id, { label: args.label, url: args.url }),
    'Link saved',
  )
  const active = link === 'new' ? create : update

  return (
    <Dialog open={link !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{link === 'new' ? 'Add link' : 'Edit link'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <Field label="Label" value={label} onChange={setLabel} placeholder="GitHub" />
          <Field label="URL" value={url} onChange={setUrl} placeholder="https://github.com/…" />
        </div>
        {active.isError ? <ApiErrorAlert error={active.error} /> : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!label.trim() || !url.trim() || active.isPending}
            onClick={() => {
              const body = { label: label.trim(), url: url.trim() }
              if (link === 'new') create.mutate(body, { onSuccess: onClose })
              else if (link) update.mutate({ id: link.id, ...body }, { onSuccess: onClose })
            }}
          >
            {active.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
