import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Pencil, Sparkles, TriangleAlert } from 'lucide-react'
import { polishField } from '@/api/polish'
import type { PolishField } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { acceptedText, draftFor, flaggedIn, markTerms } from './polish'

/**
 * "Polish with AI" for one free-prose field.
 *
 * It sits inside the field's own editing dialog and writes nowhere: accepting calls [onAccept],
 * which fills the form control the candidate is already looking at, and the profile changes when
 * they save that form — the same `PUT` it has always sent. There is no path from this component to
 * the profile that does not go through a human pressing Save twice.
 *
 * Rendered inline rather than in a dialog of its own, because every one of these fields is already
 * being edited inside a dialog: the panel opens under the field so the original stays on screen
 * beside the suggestion, and nothing has to nest a modal inside a modal to achieve it.
 */
export function PolishAction({
  profileId,
  field,
  text,
  onAccept,
}: {
  profileId: number
  field: PolishField
  /** The field as it currently stands in the form. Blank disables the button — this polishes writing that exists. */
  text: string
  onAccept: (polished: string) => void
}) {
  const [draft, setDraft] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  const polish = useMutation({
    mutationFn: () => polishField(profileId, field, text.trim()),
    onSuccess: (suggestion) => {
      setDraft(draftFor(suggestion))
      setEditing(false)
    },
  })

  const dismiss = () => {
    setDraft(null)
    setEditing(false)
    polish.reset()
  }

  const suggestion = polish.data
  const accepted = suggestion && draft !== null ? acceptedText(suggestion.original, draft) : null
  const flagged = suggestion && draft !== null ? flaggedIn(draft, suggestion.unheldSkills) : []

  return (
    <div className="space-y-2">
      <Button
        type="button"
        variant="ghost"
        size="sm"
        disabled={!text.trim() || polish.isPending}
        onClick={() => polish.mutate()}
      >
        <Sparkles /> {polish.isPending ? 'Polishing…' : 'Polish with AI'}
      </Button>

      {polish.isError ? <ApiErrorAlert error={polish.error} /> : null}

      {suggestion && draft !== null ? (
        <div className="space-y-3 rounded-md border p-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Yours</p>
              <p className="whitespace-pre-wrap text-sm text-muted-foreground">{suggestion.original}</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Suggested</p>
              {editing ? (
                <Textarea
                  aria-label="Suggested text"
                  rows={4}
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                />
              ) : (
                <p className="whitespace-pre-wrap text-sm">
                  {markTerms(draft, suggestion.unheldSkills).map((segment, index) =>
                    segment.flagged ? (
                      <span
                        key={index}
                        className="rounded bg-amber-500/15 px-0.5 text-amber-700 dark:text-amber-300"
                      >
                        {segment.text}
                      </span>
                    ) : (
                      <span key={index}>{segment.text}</span>
                    ),
                  )}
                </p>
              )}
            </div>
          </div>

          {flagged.length > 0 ? (
            <Alert className="border-amber-500/40">
              <TriangleAlert className="text-amber-600 dark:text-amber-400" />
              <AlertTitle>This names {flagged.join(', ')}, which your profile does not claim</AlertTitle>
              <AlertDescription>
                Edit it out, or add the skill under Skills if you genuinely have it. A generated CV
                saying the same thing would be refused outright — an employer is reading that one.
              </AlertDescription>
            </Alert>
          ) : null}

          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs text-muted-foreground">
              Suggested by {suggestion.modelProfile}. Nothing is stored until you accept and save.
            </p>
            <div className="flex gap-2">
              {editing ? null : (
                <Button type="button" variant="ghost" size="sm" onClick={() => setEditing(true)}>
                  <Pencil /> Edit
                </Button>
              )}
              <Button type="button" variant="outline" size="sm" onClick={dismiss}>
                Discard
              </Button>
              <Button
                type="button"
                size="sm"
                disabled={accepted === null}
                onClick={() => {
                  if (accepted !== null) {
                    onAccept(accepted)
                    dismiss()
                  }
                }}
              >
                Use this
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
