import { useState } from 'react'
import { Plus } from 'lucide-react'
import {
  addConsentClause, deleteConsentClause, updateConsentClause,
} from '@/api/profile'
import type { CandidateProfile, ConsentClause } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { PrivacyIndicator } from '@/components/PrivacyIndicator'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDelete } from './ConfirmDelete'
import { Field } from './Field'
import { RowActions } from './RowActions'
import { useProfileEdit } from './mutations'

/**
 * Standard Polish RODO wording, offered as a starting point to paste and edit - never inserted
 * automatically. The actual wording varies with the recruiter's own privacy notice and with
 * whether consent covers future recruitment too, so this is a draft, not a default.
 */
const STANDARD_POLISH_WORDING =
  'Wyrażam zgodę na przetwarzanie moich danych osobowych zawartych w niniejszej aplikacji przez ' +
  '{{company}} na potrzeby obecnego procesu rekrutacji, zgodnie z Rozporządzeniem Parlamentu ' +
  'Europejskiego i Rady (UE) 2016/679 z dnia 27 kwietnia 2016 r. w sprawie ochrony osób fizycznych ' +
  'w związku z przetwarzaniem danych osobowych i w sprawie swobodnego przepływu takich danych ' +
  'oraz uchylenia dyrektywy 95/46/WE (RODO).'

export function ConsentClauseCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [dialog, setDialog] = useState<ConsentClause | 'new' | null>(null)
  const [deleting, setDeleting] = useState<ConsentClause | null>(null)

  const remove = useProfileEdit(profileId, (id: number) => deleteConsentClause(profileId, id), 'Consent clause removed')
  const entries = profile.consentClauses

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-1.5 text-base">
          Consent clauses
          <PrivacyIndicator field="consentClause" />
        </CardTitle>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => setDialog('new')}>
            <Plus /> Add
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-xs text-muted-foreground">
          Rendered onto a generated CV from the database, never through a model - one per output
          language. <code>{'{{company}}'}</code> is replaced with the offer's company name.
        </p>
        {entries.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing recorded.</p>
        ) : (
          entries.map((entry) => (
            <div key={entry.id} className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="font-medium">{entry.language}</p>
                <p className="line-clamp-2 text-sm text-muted-foreground">{entry.text}</p>
              </div>
              <RowActions
                label={entry.language}
                disabled={remove.isPending}
                onEdit={() => setDialog(entry)}
                onDelete={() => setDeleting(entry)}
              />
            </div>
          ))
        )}
      </CardContent>

      <ConsentClauseDialog profileId={profileId} entry={dialog} onClose={() => setDialog(null)} />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); remove.reset() }
        }}
        title={`Remove the ${deleting?.language ?? ''} consent clause?`}
        description="CVs generated in this language from now on will render without one."
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }}
      />
    </Card>
  )
}

function ConsentClauseDialog({
  profileId,
  entry,
  onClose,
}: {
  profileId: number
  entry: ConsentClause | 'new' | null
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [language, setLanguage] = useState('')
  const [text, setText] = useState('')

  const key = entry === 'new' ? 'new' : (entry?.id ?? null)
  if (entry !== null && seeded !== key) {
    setSeeded(key)
    const source = entry === 'new' ? null : entry
    setLanguage(source?.language ?? '')
    setText(source?.text ?? '')
  } else if (entry === null && seeded !== null) {
    setSeeded(null)
  }

  const create = useProfileEdit(
    profileId,
    (body: Parameters<typeof addConsentClause>[1]) => addConsentClause(profileId, body),
    'Consent clause added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; body: Parameters<typeof updateConsentClause>[2] }) =>
      updateConsentClause(profileId, args.id, args.body),
    'Consent clause saved',
  )
  const active = entry === 'new' ? create : update

  return (
    <Dialog open={entry !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{entry === 'new' ? 'Add consent clause' : 'Edit consent clause'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <Field label="Language" value={language} onChange={setLanguage} placeholder="Polish" />
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="consent-clause-text" className="text-sm font-medium">Text</label>
              <Button
                type="button"
                variant="link"
                size="sm"
                className="h-auto p-0 text-xs"
                onClick={() => setText(STANDARD_POLISH_WORDING)}
              >
                Paste standard Polish wording
              </Button>
            </div>
            <Textarea
              id="consent-clause-text"
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={6}
              placeholder="I consent to the processing of my personal data by {{company}}..."
            />
          </div>
        </div>
        {active.isError ? <ApiErrorAlert error={active.error} /> : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!language.trim() || !text.trim() || active.isPending}
            onClick={() => {
              const body = { language: language.trim(), text: text.trim() }
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
