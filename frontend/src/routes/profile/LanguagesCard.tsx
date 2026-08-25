import { useState } from 'react'
import { Plus } from 'lucide-react'
import { addLanguage, deleteLanguage, reorderLanguages, updateLanguage } from '@/api/profile'
import { LANGUAGE_LEVELS } from '@/api/types'
import type { CandidateProfile, LanguageLevel, LanguageSkill } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { ConfirmDelete } from './ConfirmDelete'
import { Field } from './Field'
import { RowActions } from './RowActions'
import { movedIds, useProfileEdit } from './mutations'

export function LanguagesCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [dialog, setDialog] = useState<LanguageSkill | 'new' | null>(null)
  const [deleting, setDeleting] = useState<LanguageSkill | null>(null)

  const reorder = useProfileEdit(profileId, (ids: number[]) => reorderLanguages(profileId, ids), 'Languages reordered')
  const remove = useProfileEdit(profileId, (id: number) => deleteLanguage(profileId, id), 'Language removed')
  const { languages } = profile

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Languages</CardTitle>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => setDialog('new')}>
                <Plus /> Add
              </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        {languages.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            None recorded. An offer asking for B2 German is reported as unmet without one.
          </p>
        ) : (
          languages.map((language, index) => (
            <div key={language.id} className="flex items-center justify-between gap-3">
              <p className="text-sm">
                {language.language}
                <span className="ml-2 text-muted-foreground">{language.level}</span>
              </p>
              <RowActions
                label={language.language}
                disabled={reorder.isPending}
                onUp={index > 0 ? () => reorder.mutate(movedIds(languages, index, index - 1)) : undefined}
                onDown={
                  index < languages.length - 1
                    ? () => reorder.mutate(movedIds(languages, index, index + 1))
                    : undefined
                }
                onEdit={() => setDialog(language)}
                onDelete={() => setDeleting(language)}
              />
            </div>
          ))
        )}
        {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
      </CardContent>

      <LanguageDialog profileId={profileId} language={dialog} onClose={() => setDialog(null)} />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); remove.reset() }
        }}
        title={`Remove ${deleting?.language ?? 'language'}?`}
        description="Future analyses will report any offer requiring it as unmet."
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }}
      />
    </Card>
  )
}

function LanguageDialog({
  profileId,
  language,
  onClose,
}: {
  profileId: number
  language: LanguageSkill | 'new' | null
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [name, setName] = useState('')
  const [level, setLevel] = useState<LanguageLevel>('B2')

  const key = language === 'new' ? 'new' : (language?.id ?? null)
  if (language !== null && seeded !== key) {
    setSeeded(key)
    setName(language === 'new' ? '' : language.language)
    setLevel(language === 'new' ? 'B2' : language.level)
  }

  const create = useProfileEdit(
    profileId,
    (body: { language: string; level: LanguageLevel }) => addLanguage(profileId, body),
    'Language added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; language: string; level: LanguageLevel }) =>
      updateLanguage(profileId, args.id, { language: args.language, level: args.level }),
    'Language saved',
  )
  const active = language === 'new' ? create : update

  return (
    <Dialog open={language !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{language === 'new' ? 'Add language' : 'Edit language'}</DialogTitle>
          <DialogDescription>
            Levels are CEFR and compared by rank, so C1 satisfies a requirement for B2. Casing does
            not matter — “English” and “english” are the same entry.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <Field label="Language" value={name} onChange={setName} placeholder="German" />
          <div className="space-y-1.5">
            <Label htmlFor="language-level">Level</Label>
            <Select value={level} onValueChange={(next) => setLevel(next as LanguageLevel)}>
              <SelectTrigger id="language-level" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {LANGUAGE_LEVELS.map((option) => (
                  <SelectItem key={option} value={option}>{option}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        {active.isError ? <ApiErrorAlert error={active.error} /> : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!name.trim() || active.isPending}
            onClick={() => {
              if (language === 'new') {
                create.mutate({ language: name.trim(), level }, { onSuccess: onClose })
              } else if (language) {
                update.mutate({ id: language.id, language: name.trim(), level }, { onSuccess: onClose })
              }
            }}
          >
            {active.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
