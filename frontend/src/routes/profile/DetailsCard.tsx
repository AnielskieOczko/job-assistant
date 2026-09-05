import { useRef, useState } from 'react'
import { Link as LinkIcon, Mail, MapPin, Pencil, Phone, Plus, User } from 'lucide-react'
import {
  addLink, deletePortrait, deleteLink, portraitUrl, putDetails, putPortrait, reorderLinks, updateLink,
} from '@/api/profile'
import type { CandidateProfile, DetailsRequest, LinkRequest, ProfileLink } from '@/api/types'

/** lucide-react ships no brand marks, so GitHub/LinkedIn are hand-drawn rather than a new dependency. */
function GithubMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
    </svg>
  )
}

function LinkedinMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667h-3.554v-11.452h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zm-15.11-13.019c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019h-3.564v-11.452h3.564v11.452zm16.926-20.452h-20.454c-.979 0-1.771.774-1.771 1.729v20.542c0 .956.792 1.729 1.771 1.729h20.451c.978 0 1.778-.773 1.778-1.729v-20.542c0-.955-.8-1.729-1.778-1.729z" />
    </svg>
  )
}
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { PrivacyIndicator } from '@/components/PrivacyIndicator'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDelete } from './ConfirmDelete'
import { Field } from './Field'
import { PolishAction } from './PolishAction'
import { RowActions } from './RowActions'
import { blankToNull, movedIds, useProfileEdit } from './mutations'

/** Falls back to a generic link icon for anything that isn't a recognized platform. */
function iconForLink(url: string) {
  let host = ''
  try {
    host = new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return LinkIcon
  }
  if (host.includes('github.com')) return GithubMark
  if (host.includes('linkedin.com')) return LinkedinMark
  return LinkIcon
}

export function DetailsCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [editing, setEditing] = useState(false)
  const [linkDialog, setLinkDialog] = useState<ProfileLink | 'new' | null>(null)
  const [deleting, setDeleting] = useState<ProfileLink | null>(null)

  const removeLink = useProfileEdit(profileId, (id: number) => deleteLink(profileId, id), 'Link removed')
  const reorder = useProfileEdit(profileId, (ids: number[]) => reorderLinks(profileId, ids), 'Links reordered')
  const { details, links } = profile

  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-start justify-between gap-4">
          <PortraitControl profileId={profileId} profile={profile} />
          <div className="min-w-0 flex-1">
            <h2 className="flex items-center gap-1.5 font-heading text-xl font-semibold">
              {details.fullName}
              <PrivacyIndicator field="fullName" />
            </h2>
            {details.headline ? <p className="text-muted-foreground">{details.headline}</p> : null}
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1.5 text-sm text-muted-foreground">
              {(
                [
                  [Mail, 'email', details.email],
                  [Phone, 'phone', details.phone],
                  [MapPin, 'location', details.location],
                ] as const
              )
                .filter(
                  (entry): entry is [typeof Mail, 'email' | 'phone' | 'location', string] =>
                    Boolean(entry[2]),
                )
                .map(([Icon, field, value]) => (
                  <span key={field} className="inline-flex items-center gap-1.5">
                    <Icon className="size-3.5 shrink-0" />
                    {value}
                    <PrivacyIndicator field={field} />
                  </span>
                ))}
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
            <Pencil /> Edit
          </Button>
        </div>

        {details.summary ? (
          <p className="mt-4 text-sm leading-relaxed">{details.summary}</p>
        ) : null}

        {details.careerGoal ? (
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            <span className="font-medium text-foreground">Aiming at: </span>
            {details.careerGoal}
          </p>
        ) : null}

        <div className="mt-4 border-t pt-3">
          <div className="flex items-center justify-between">
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Links
              <PrivacyIndicator field="links" />
            </p>
            <Button variant="ghost" size="sm" onClick={() => setLinkDialog('new')}>
              <Plus /> Add link
            </Button>
          </div>
          {links.length === 0 ? (
            <p className="mt-1 text-sm text-muted-foreground">No links yet.</p>
          ) : (
            <ul className="mt-1 divide-y">
              {links.map((link, index) => {
                const Icon = iconForLink(link.url)
                return (
                <li key={link.id} className="flex items-center justify-between gap-3 py-1.5">
                  <a
                    href={link.url}
                    target="_blank"
                    rel="noreferrer"
                    className="flex min-w-0 items-center gap-2 truncate text-sm underline underline-offset-2"
                  >
                    <Icon className="size-3.5 shrink-0 text-muted-foreground" />
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
                )
              })}
            </ul>
          )}
          {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
        </div>
      </CardContent>

      <DetailsDialog profileId={profileId} profile={profile} open={editing} onOpenChange={setEditing} />
      <LinkDialog profileId={profileId} link={linkDialog} onClose={() => setLinkDialog(null)} />
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

/**
 * The profile photo, which is optional everywhere: the CV header reserves no space for one, so an
 * empty slot here is a real state rather than a missing value.
 *
 * The image is fetched by URL rather than read out of the profile - the profile document carries
 * `hasPortrait` and never the bytes, because it is also what the prompt builders read.
 */
function PortraitControl({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const picker = useRef<HTMLInputElement>(null)

  const upload = useProfileEdit(profileId, (file: File) => putPortrait(profileId, file), 'Photo saved')
  const remove = useProfileEdit(profileId, () => deletePortrait(profileId), 'Photo removed')

  return (
    <div className="shrink-0">
      <div className="mb-1 flex justify-end"><PrivacyIndicator field="portrait" /></div>
      <input
        ref={picker}
        type="file"
        accept="image/png,image/jpeg,image/webp"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          // Cleared so choosing the same file twice still fires a change event.
          e.target.value = ''
          if (file) upload.mutate(file)
        }}
      />

      {profile.hasPortrait ? (
        <img
          src={portraitUrl(profileId, profile.revision)}
          alt={`Portrait of ${profile.details.fullName}`}
          className="size-20 rounded-md border object-cover"
        />
      ) : (
        <button
          type="button"
          onClick={() => picker.current?.click()}
          disabled={upload.isPending}
          className="flex size-20 flex-col items-center justify-center gap-1 rounded-md border border-dashed text-muted-foreground hover:bg-accent"
        >
          <User className="size-5" />
          <span className="text-[10px] leading-none">Add photo</span>
        </button>
      )}

      {profile.hasPortrait ? (
        <div className="mt-1 flex justify-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            className="h-6 px-1.5 text-xs"
            disabled={upload.isPending}
            onClick={() => picker.current?.click()}
          >
            Replace
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-6 px-1.5 text-xs"
            disabled={remove.isPending}
            onClick={() => remove.mutate(undefined)}
          >
            Remove
          </Button>
        </div>
      ) : null}

      {upload.isError ? <ApiErrorAlert error={upload.error} /> : null}
      {remove.isError ? <ApiErrorAlert error={remove.error} /> : null}
    </div>
  )
}

function DetailsDialog({
  profileId,
  profile,
  open,
  onOpenChange,
}: {
  profileId: number
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
  const [careerGoal, setCareerGoal] = useState('')

  // Seed during render rather than in an effect - the idiom the rest of the app uses.
  if (open && seeded !== profile.revision) {
    setSeeded(profile.revision)
    setFullName(profile.details.fullName)
    setHeadline(profile.details.headline ?? '')
    setEmail(profile.details.email ?? '')
    setPhone(profile.details.phone ?? '')
    setLocation(profile.details.location ?? '')
    setSummary(profile.details.summary ?? '')
    setCareerGoal(profile.details.careerGoal ?? '')
  }

  const save = useProfileEdit(profileId, (body: DetailsRequest) => putDetails(profileId, body), 'Details saved')

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
          <div className="space-y-1.5">
            <Label htmlFor="details-career-goal">Career goal</Label>
            <Textarea
              id="details-career-goal"
              rows={3}
              placeholder="e.g. I'm moving from product management into backend development."
              value={careerGoal}
              onChange={(e) => setCareerGoal(e.target.value)}
            />
            <PolishAction
              profileId={profileId}
              field="CAREER_GOAL"
              text={careerGoal}
              onAccept={setCareerGoal}
            />
            <p className="text-xs text-muted-foreground">
              What you're aiming at next, not what you've already done. Write in the first person and
              leave out your name and contact details — this goes to the AI that writes your gap
              report and cover letter, and a name here will cause generation to fail.
            </p>
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
                  careerGoal: blankToNull(careerGoal),
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

function LinkDialog({
  profileId,
  link,
  onClose,
}: {
  profileId: number
  link: ProfileLink | 'new' | null
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [label, setLabel] = useState('')
  const [url, setUrl] = useState('')

  const key = link === 'new' ? 'new' : (link?.id ?? null)
  if (link !== null && seeded !== key) {
    setSeeded(key)
    setLabel(link === 'new' ? '' : link.label)
    setUrl(link === 'new' ? '' : link.url)
  }

  const create = useProfileEdit(profileId, (body: LinkRequest) => addLink(profileId, body), 'Link added')
  const update = useProfileEdit(
    profileId,
    (args: { id: number; label: string; url: string }) =>
      updateLink(profileId, args.id, { label: args.label, url: args.url }),
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
