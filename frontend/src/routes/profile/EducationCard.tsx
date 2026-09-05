import { useState } from 'react'
import { Plus } from 'lucide-react'
import { addEducation, deleteEducation, reorderEducation, updateEducation } from '@/api/profile'
import type { CandidateProfile, Education } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { PrivacyIndicatorGroup } from '@/components/PrivacyIndicator'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { formatPeriod } from '@/lib/format'
import { ConfirmDelete } from './ConfirmDelete'
import { Field } from './Field'
import { RowActions } from './RowActions'
import { blankToNull, useProfileEdit, useReorderableRows } from './mutations'
import { useSeededKey } from './seededKey'

export function EducationCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [dialog, setDialog] = useState<Education | 'new' | null>(null)
  const entries = profile.education

  const { reorder, deleting, rowActions, confirmDeleteProps } = useReorderableRows(profileId, entries, {
    reorder: (ids: number[]) => reorderEducation(profileId, ids),
    remove: (id: number) => deleteEducation(profileId, id),
    reorderSuccess: 'Education reordered',
    removeSuccess: 'Entry removed',
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-1.5 text-base">
          Education
          <PrivacyIndicatorGroup fields={['schools', 'dates']} />
        </CardTitle>
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
                <p className="font-medium">{entry.degree}</p>
                <p className="text-sm text-muted-foreground">
                  {entry.institution}
                  {entry.fieldOfStudy ? ` · ${entry.fieldOfStudy}` : ''}
                </p>
                <p className="text-sm text-muted-foreground">
                  {formatPeriod(entry.startedOn, entry.endedOn)}
                </p>
              </div>
              <RowActions
                {...rowActions(index, { label: entry.degree, onEdit: () => setDialog(entry) })}
              />
            </div>
          ))
        )}
        {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
      </CardContent>

      <EducationDialog profileId={profileId} entry={dialog} onClose={() => setDialog(null)} />
      <ConfirmDelete
        {...confirmDeleteProps(
          `Remove ${deleting?.degree ?? 'entry'}?`,
          'It disappears from any CV generated from now on.',
        )}
      />
    </Card>
  )
}

function EducationDialog({
  profileId,
  entry,
  onClose,
}: {
  profileId: number
  entry: Education | 'new' | null
  onClose: () => void
}) {
  const [institution, setInstitution] = useState('')
  const [degree, setDegree] = useState('')
  const [fieldOfStudy, setFieldOfStudy] = useState('')
  const [startedOn, setStartedOn] = useState('')
  const [endedOn, setEndedOn] = useState('')

  const key = entry === 'new' ? 'new' : (entry?.id ?? null)
  if (useSeededKey(key)) {
    const source = entry === 'new' ? null : entry
    setInstitution(source?.institution ?? '')
    setDegree(source?.degree ?? '')
    setFieldOfStudy(source?.fieldOfStudy ?? '')
    setStartedOn(source?.startedOn ?? '')
    setEndedOn(source?.endedOn ?? '')
  }

  const create = useProfileEdit(
    profileId,
    (body: Parameters<typeof addEducation>[1]) => addEducation(profileId, body),
    'Entry added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; body: Parameters<typeof updateEducation>[2] }) =>
      updateEducation(profileId, args.id, args.body),
    'Entry saved',
  )
  const active = entry === 'new' ? create : update

  return (
    <Dialog open={entry !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{entry === 'new' ? 'Add education' : 'Edit education'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <Field label="Institution" value={institution} onChange={setInstitution} />
          <Field label="Degree" value={degree} onChange={setDegree} placeholder="BSc" />
          <Field label="Field of study" value={fieldOfStudy} onChange={setFieldOfStudy} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Started" value={startedOn} onChange={setStartedOn} type="date" />
            <Field label="Ended" value={endedOn} onChange={setEndedOn} type="date" />
          </div>
        </div>
        {active.isError ? <ApiErrorAlert error={active.error} /> : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!institution.trim() || !degree.trim() || active.isPending}
            onClick={() => {
              const body = {
                institution: institution.trim(),
                degree: degree.trim(),
                fieldOfStudy: blankToNull(fieldOfStudy),
                startedOn: startedOn || null,
                endedOn: endedOn || null,
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
