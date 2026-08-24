import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { AlertTriangle, Upload, UserRound } from 'lucide-react'
import { toast } from 'sonner'
import { getProfile, importProfile } from '@/api/profile'
import { ApiError } from '@/api/http'
import { keys } from '@/api/keys'
import { isCurrentRole } from '@/api/types'
import type { CandidateProfile, SkillCategory } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { useSkillNames } from '@/hooks/useSkillNames'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { formatPeriod } from '@/lib/format'

export function ProfilePage() {
  const [importOpen, setImportOpen] = useState(false)

  // 204 No Content when nothing has been imported, so `data` is null rather than a 404 error.
  const profile = useQuery({ queryKey: keys.profile, queryFn: getProfile })

  return (
    <>
      <PageHeader
        title="Profile"
        description="Hand-authored ground truth. No model writes to it, and every claim a generated CV makes must trace back to a record here."
        actions={
          profile.data ? (
            <Button variant="outline" onClick={() => setImportOpen(true)}>
              <Upload /> Re-import
            </Button>
          ) : null
        }
      />

      {profile.isError ? <ApiErrorAlert error={profile.error} /> : null}

      {profile.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : !profile.data ? (
        <EmptyState
          icon={UserRound}
          title="No profile imported"
          description="Import a profile document before analysing an offer — the gap report compares against it."
          action={<Button onClick={() => setImportOpen(true)}><Upload /> Import profile</Button>}
        />
      ) : (
        <ProfileView profile={profile.data} />
      )}

      <ImportDialog open={importOpen} onOpenChange={setImportOpen} />
    </>
  )
}

function ProfileView({ profile }: { profile: CandidateProfile }) {
  const skills = useSkillNames()

  const byCategory = new Map<SkillCategory | 'UNKNOWN', typeof profile.skills>()
  for (const skill of profile.skills) {
    const category = skills.byId.get(skill.skillId)?.category ?? 'UNKNOWN'
    byCategory.set(category, [...(byCategory.get(category) ?? []), skill])
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="pt-6">
          <h2 className="font-heading text-xl font-semibold">{profile.details.fullName}</h2>
          {profile.details.headline ? (
            <p className="text-muted-foreground">{profile.details.headline}</p>
          ) : null}
          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-sm text-muted-foreground">
            {[profile.details.email, profile.details.phone, profile.details.location]
              .filter(Boolean)
              .map((value) => <span key={value}>{value}</span>)}
          </div>
          {profile.links.length > 0 ? (
            <div className="mt-2 flex flex-wrap gap-2">
              {profile.links.map((link) => (
                <a
                  key={link.id}
                  href={link.url}
                  target="_blank"
                  rel="noreferrer"
                  className="text-sm underline underline-offset-2 hover:text-foreground"
                >
                  {link.label}
                </a>
              ))}
            </div>
          ) : null}
          {profile.details.summary ? (
            <p className="mt-4 text-sm leading-relaxed">{profile.details.summary}</p>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Skills ({profile.skills.length})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {[...byCategory.entries()].map(([category, items]) => (
            <div key={category}>
              <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {category}
              </p>
              <div className="flex flex-wrap gap-1.5">
                {items.map((skill) => (
                  <Badge key={skill.id} variant="outline" className="gap-1.5">
                    {skills.nameOf(skill.skillId)}
                    <span className="text-muted-foreground">
                      {skill.proficiency.toLowerCase()}
                      {skill.yearsOfExperience !== null ? ` · ${skill.yearsOfExperience}y` : ''}
                    </span>
                  </Badge>
                ))}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">Experience</CardTitle></CardHeader>
        <CardContent className="space-y-6">
          {profile.experiences.map((experience) => (
            <div key={experience.id} className="border-l-2 pl-4">
              <div className="flex flex-wrap items-baseline gap-2">
                <p className="font-medium">{experience.roleTitle}</p>
                <p className="text-muted-foreground">{experience.company}</p>
                {isCurrentRole(experience) ? <Badge variant="secondary">Current</Badge> : null}
              </div>
              <p className="text-sm text-muted-foreground">
                {formatPeriod(experience.startedOn, experience.endedOn)}
                {experience.location ? ` · ${experience.location}` : ''}
              </p>
              {experience.summary ? (
                <p className="mt-2 text-sm">{experience.summary}</p>
              ) : null}
              <ul className="mt-2 space-y-2">
                {experience.bullets.map((bullet) => (
                  <li key={bullet.id} className="text-sm">
                    <p>{bullet.text}</p>
                    {bullet.skillIds.length > 0 ? (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {bullet.skillIds.map((id) => (
                          <Badge key={id} variant="outline" className="text-[10px]">
                            {skills.nameOf(id)}
                          </Badge>
                        ))}
                      </div>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </CardContent>
      </Card>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="text-base">Education</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            {profile.education.map((entry) => (
              <div key={entry.id}>
                <p className="font-medium">{entry.degree}</p>
                <p className="text-sm text-muted-foreground">
                  {entry.institution}
                  {entry.fieldOfStudy ? ` · ${entry.fieldOfStudy}` : ''}
                </p>
                <p className="text-sm text-muted-foreground">
                  {formatPeriod(entry.startedOn, entry.endedOn)}
                </p>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Languages</CardTitle></CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {profile.languages.map((language) => (
              <Badge key={language.id} variant="outline">
                {language.language} · {language.level}
              </Badge>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function ImportDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [text, setText] = useState('')
  const [parseError, setParseError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const submit = useMutation({
    mutationFn: (document: unknown) => importProfile(document),
    onSuccess: () => {
      toast.success('Profile imported')
      queryClient.invalidateQueries({ queryKey: keys.profile })
      onOpenChange(false)
      setText('')
    },
  })

  function run() {
    setParseError(null)
    let parsed: unknown
    try {
      parsed = JSON.parse(text)
    } catch (error) {
      // Catch malformed JSON here rather than spending a round trip on it.
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
      return
    }
    submit.mutate(parsed)
  }

  const error = submit.error instanceof ApiError ? submit.error : null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
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
              rows={16}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder='{ "details": { "fullName": "…" }, "skills": [ … ] }'
              className="font-mono text-xs"
            />
          </TabsContent>
          <TabsContent value="upload">
            <input
              type="file"
              accept="application/json,.json"
              className="block w-full text-sm file:mr-3 file:rounded-md file:border file:bg-muted file:px-3 file:py-1.5 file:text-sm"
              onChange={async (e) => {
                const file = e.target.files?.[0]
                if (file) setText(await file.text())
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

        {error?.status === 400 ? <ImportRejected error={error} /> : null}
        {submit.isError && error?.status !== 400 ? <ApiErrorAlert error={submit.error} /> : null}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button onClick={run} disabled={!text.trim() || submit.isPending}>
            {submit.isPending ? 'Importing…' : 'Replace profile'}
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
