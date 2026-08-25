import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import { importProfile } from '@/api/profile'
import { ApiError } from '@/api/http'
import { keys } from '@/api/keys'
import type { CandidateProfile } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'

export function ImportDialog({
  profileId,
  open,
  onOpenChange,
  current,
}: {
  profileId: number
  open: boolean
  onOpenChange: (open: boolean) => void
  current: CandidateProfile | null
}) {
  const [text, setText] = useState('')
  const [parseError, setParseError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const queryClient = useQueryClient()

  const submit = useMutation({
    mutationFn: (document: unknown) => importProfile(profileId, document),
    onSuccess: (profile) => {
      toast.success('Profile imported')
      queryClient.setQueryData(keys.profile(profileId), profile)
      queryClient.invalidateQueries({ queryKey: keys.aggregate(profileId) })
      onOpenChange(false)
      setText('')
      setConfirming(false)
    },
  })

  function parsed(): unknown | undefined {
    setParseError(null)
    try {
      return JSON.parse(text)
    } catch (error) {
      // Catch malformed JSON here rather than spending a round trip on it.
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
      return undefined
    }
  }

  const error = submit.error instanceof ApiError ? submit.error : null
  const bulletCount = current?.experiences.reduce((total, e) => total + e.bullets.length, 0) ?? 0

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) setConfirming(false)
      }}
    >
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Import profile</DialogTitle>
          <DialogDescription>
            This is a <strong>full replace</strong>, not a merge — the document becomes the profile.
          </DialogDescription>
        </DialogHeader>

        <Tabs defaultValue="paste">
          <TabsList>
            <TabsTrigger value="paste">Paste JSON</TabsTrigger>
            <TabsTrigger value="upload">Upload file</TabsTrigger>
          </TabsList>
          <TabsContent value="paste">
            <Textarea
              rows={14}
              value={text}
              onChange={(e) => { setText(e.target.value); setConfirming(false) }}
              placeholder='{ "details": { "fullName": "…" }, "skills": [ … ] }'
              className="max-h-64 overflow-y-auto font-mono text-xs"
            />
          </TabsContent>
          <TabsContent value="upload">
            <input
              type="file"
              accept="application/json,.json"
              className="block w-full text-sm file:mr-3 file:rounded-md file:border file:bg-muted file:px-3 file:py-1.5 file:text-sm"
              onChange={async (e) => {
                const file = e.target.files?.[0]
                if (file) { setText(await file.text()); setConfirming(false) }
              }}
            />
            {text ? (
              <p className="mt-2 text-sm text-muted-foreground">
                {text.length.toLocaleString()} characters loaded — switch to “Paste JSON” to review.
              </p>
            ) : null}
          </TabsContent>
        </Tabs>

        {parseError ? (
          <Alert variant="destructive">
            <AlertTriangle />
            <AlertTitle>That is not valid JSON</AlertTitle>
            <AlertDescription><p className="font-mono text-xs">{parseError}</p></AlertDescription>
          </Alert>
        ) : null}

        {/*
          Import reassigns every id, so the bullets a stored CV cites stop existing. It was always
          this way, but now that the profile is edited a piece at a time it is worth saying out loud
          before the click rather than leaving it to be discovered.
        */}
        {confirming && current ? (
          <Alert variant="destructive">
            <AlertTriangle />
            <AlertTitle>Replace the whole profile?</AlertTitle>
            <AlertDescription>
              <ul className="list-inside list-disc space-y-0.5">
                <li>
                  {current.skills.length} skill(s), {current.experiences.length} role(s) and{' '}
                  {bulletCount} bullet(s) are discarded.
                </li>
                <li>Every id is reassigned, so generated CVs and analyses will read as stale.</li>
              </ul>
            </AlertDescription>
          </Alert>
        ) : null}

        {error?.status === 400 ? <ImportRejected error={error} /> : null}
        {submit.isError && error?.status !== 400 ? <ApiErrorAlert error={submit.error} /> : null}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button
            variant={confirming ? 'destructive' : 'default'}
            disabled={!text.trim() || submit.isPending}
            onClick={() => {
              const document = parsed()
              if (document === undefined) return
              // A profile already exists, so ask once before discarding it.
              if (current && !confirming) { setConfirming(true); return }
              submit.mutate(document)
            }}
          >
            {submit.isPending
              ? 'Importing…'
              : confirming
                ? 'Yes, replace it'
                : current
                  ? 'Replace profile'
                  : 'Import profile'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * The import refuses to guess. An unresolvable skill would silently vanish from every future gap
 * report, and a bullet evidencing an undeclared skill could put that skill on a CV with nothing
 * behind it — so both are listed in full rather than dropped.
 */
function ImportRejected({ error }: { error: ApiError }) {
  const unresolved = error.unresolvedSkills
  const undeclared = error.undeclaredBulletSkills

  return (
    <Alert variant="destructive">
      <AlertTriangle />
      <AlertTitle>Profile import rejected</AlertTitle>
      <AlertDescription className="space-y-3">
        {unresolved.length > 0 ? (
          <div>
            <p className="font-medium">Not in the skill catalog</p>
            <div className="mt-1 flex flex-wrap gap-1.5">
              {unresolved.map((name) => <Badge key={name} variant="destructive">{name}</Badge>)}
            </div>
            <p className="mt-1.5">
              Add them in the{' '}
              <Link to="/catalog" className="underline underline-offset-2">catalog</Link>, or
              correct the spelling. Aliases work — <code>postgres</code>, <code>PostgreSQL</code>{' '}
              and <code>psql</code> all resolve to the same skill.
            </p>
          </div>
        ) : null}
        {undeclared.length > 0 ? (
          <div>
            <p className="font-medium">Tagged on a bullet but not declared in <code>skills[]</code></p>
            <div className="mt-1 flex flex-wrap gap-1.5">
              {undeclared.map((name) => <Badge key={name} variant="destructive">{name}</Badge>)}
            </div>
          </div>
        ) : null}
      </AlertDescription>
    </Alert>
  )
}
