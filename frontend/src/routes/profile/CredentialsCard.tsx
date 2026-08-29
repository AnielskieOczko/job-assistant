import { useState } from 'react'
import { Plus } from 'lucide-react'
import { addCredential, deleteCredential, reorderCredentials, updateCredential } from '@/api/profile'
import { CREDENTIAL_KINDS } from '@/api/types'
import type { CandidateProfile, Credential, CredentialKind } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { formatCredentialPeriod } from '@/lib/format'
import { ConfirmDelete } from './ConfirmDelete'
import { Field } from './Field'
import { RowActions } from './RowActions'
import { blankToNull, movedIds, useProfileEdit } from './mutations'

export function CredentialsCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [dialog, setDialog] = useState<Credential | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Credential | null>(null)

  const reorder = useProfileEdit(profileId, (ids: number[]) => reorderCredentials(profileId, ids), 'Credentials reordered')
  const remove = useProfileEdit(profileId, (id: number) => deleteCredential(profileId, id), 'Credential removed')
  const entries = profile.credentials

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Credentials</CardTitle>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => setDialog('new')}>
                <Plus /> Add
              </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        {entries.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing recorded.</p>
        ) : (
          entries.map((entry, index) => (
            <div key={entry.id} className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="font-medium">{entry.title}</p>
                <p className="text-sm text-muted-foreground">
                  {entry.issuer} · {entry.kind.toLowerCase()}
                </p>
                <p className="text-sm text-muted-foreground">
                  {formatCredentialPeriod(entry.issuedOn, entry.expiresOn)}
                </p>
                {entry.url ? (
                  <a
                    href={entry.url}
                    target="_blank"
                    rel="noreferrer"
                    className="text-xs underline text-muted-foreground"
                  >
                    Certificate ↗
                  </a>
                ) : null}
              </div>
              <RowActions
                label={entry.title}
                disabled={reorder.isPending}
                onUp={index > 0 ? () => reorder.mutate(movedIds(entries, index, index - 1)) : undefined}
                onDown={
                  index < entries.length - 1
                    ? () => reorder.mutate(movedIds(entries, index, index + 1))
                    : undefined
                }
                onEdit={() => setDialog(entry)}
                onDelete={() => setDeleting(entry)}
              />
            </div>
          ))
        )}
        {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
      </CardContent>

      <CredentialDialog profileId={profileId} entry={dialog} onClose={() => setDialog(null)} />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); remove.reset() }
        }}
        title={`Remove ${deleting?.title ?? 'entry'}?`}
        description="It disappears from any CV generated from now on."
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }}
      />
    </Card>
  )
}

function CredentialDialog({
  profileId,
  entry,
  onClose,
}: {
  profileId: number
  entry: Credential | 'new' | null
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [title, setTitle] = useState('')
  const [issuer, setIssuer] = useState('')
  const [kind, setKind] = useState<CredentialKind>('COURSE')
  const [url, setUrl] = useState('')
  const [credentialId, setCredentialId] = useState('')
  const [issuedOn, setIssuedOn] = useState('')
  const [expiresOn, setExpiresOn] = useState('')

  const key = entry === 'new' ? 'new' : (entry?.id ?? null)
  if (entry !== null && seeded !== key) {
    setSeeded(key)
    const source = entry === 'new' ? null : entry
    setTitle(source?.title ?? '')
    setIssuer(source?.issuer ?? '')
    setKind(source?.kind ?? 'COURSE')
    setUrl(source?.url ?? '')
    setCredentialId(source?.credentialId ?? '')
    setIssuedOn(source?.issuedOn ?? '')
    setExpiresOn(source?.expiresOn ?? '')
  } else if (entry === null && seeded !== null) {
    // Forces a reseed on the next open, even if it reuses the same key (another "new", or the
    // same row edited twice) - otherwise the dialog would reopen showing whatever was left in
    // these fields from the last time it was open, discarded or not.
    setSeeded(null)
  }

  const create = useProfileEdit(
    profileId,
    (body: Parameters<typeof addCredential>[1]) => addCredential(profileId, body),
    'Credential added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; body: Parameters<typeof updateCredential>[2] }) =>
      updateCredential(profileId, args.id, args.body),
    'Credential saved',
  )
  const active = entry === 'new' ? create : update

  return (
    <Dialog open={entry !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{entry === 'new' ? 'Add credential' : 'Edit credential'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <Field label="Title" value={title} onChange={setTitle} placeholder="AWS Solutions Architect" />
          <Field label="Issuer" value={issuer} onChange={setIssuer} placeholder="Amazon Web Services" />
          <div className="space-y-1.5">
            <Label htmlFor="credential-kind">Kind</Label>
            <Select value={kind} onValueChange={(next) => setKind(next as CredentialKind)}>
              <SelectTrigger id="credential-kind" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {CREDENTIAL_KINDS.map((option) => (
                  <SelectItem key={option} value={option}>{option.toLowerCase()}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Field label="URL" value={url} onChange={setUrl} placeholder="https://…" />
          <Field label="Credential ID" value={credentialId} onChange={setCredentialId} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Issued" value={issuedOn} onChange={setIssuedOn} type="date" />
            <Field label="Expires" value={expiresOn} onChange={setExpiresOn} type="date" />
          </div>
        </div>
        {active.isError ? <ApiErrorAlert error={active.error} /> : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!title.trim() || !issuer.trim() || active.isPending}
            onClick={() => {
              const body = {
                title: title.trim(),
                issuer: issuer.trim(),
                kind,
                url: blankToNull(url),
                credentialId: blankToNull(credentialId),
                issuedOn: issuedOn || null,
                expiresOn: expiresOn || null,
              }
              if (entry === 'new') create.mutate(body, { onSuccess: onClose })
              else if (entry) update.mutate({ id: entry.id, body }, { onSuccess: onClose })
            }}
          >
            {active.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
