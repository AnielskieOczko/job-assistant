import { useState } from 'react'
import { Calendar, Plus, X } from 'lucide-react'
import {
  addProject, addProjectBullet, deleteBullet, deleteProject, reorderProjectBullets,
  reorderProjects, updateBullet, updateProject,
} from '@/api/profile'
import type { CandidateProfile, ExperienceBullet, Project } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { PrivacyIndicatorGroup } from '@/components/PrivacyIndicator'
import { SkillCombobox } from '@/components/SkillCombobox'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useSkillNames } from '@/hooks/useSkillNames'
import { formatPeriod } from '@/lib/format'
import { ConfirmDelete } from './ConfirmDelete'
import { Field } from './Field'
import { PolishAction } from './PolishAction'
import { RowActions } from './RowActions'
import { blankToNull, movedIds, useProfileEdit } from './mutations'

export function ProjectCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [dialog, setDialog] = useState<Project | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Project | null>(null)
  const names = useSkillNames()

  const reorder = useProfileEdit(profileId, (ids: number[]) => reorderProjects(profileId, ids), 'Projects reordered')
  const remove = useProfileEdit(profileId, (id: number) => deleteProject(profileId, id), 'Project removed')
  const { projects } = profile

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-1.5 text-base">
          Projects
          <PrivacyIndicatorGroup fields={['links', 'bulletText']} />
        </CardTitle>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => setDialog('new')}>
            <Plus /> Add project
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {projects.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No side projects yet. Bullets under a project are what a tailored CV is allowed to draw on,
            the same rule a role's bullets follow.
          </p>
        ) : (
          <div className="space-y-6 border-l border-foreground/15 pl-6">
            {projects.map((project, index) => (
              <div key={project.id} className="relative">
                <span className="absolute top-1 -left-[1.9rem] size-3 rounded-full border-2 border-foreground/30 bg-background" />
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-baseline gap-2">
                      <p className="font-medium">{project.name}</p>
                    </div>
                    {project.startedOn || project.endedOn ? (
                      <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                        <Calendar className="size-3.5 shrink-0" />
                        {formatPeriod(project.startedOn, project.endedOn)}
                      </p>
                    ) : null}
                  </div>
                  <RowActions
                    label={project.name}
                    disabled={reorder.isPending}
                    onUp={index > 0 ? () => reorder.mutate(movedIds(projects, index, index - 1)) : undefined}
                    onDown={
                      index < projects.length - 1
                        ? () => reorder.mutate(movedIds(projects, index, index + 1))
                        : undefined
                    }
                    onEdit={() => setDialog(project)}
                    onDelete={() => setDeleting(project)}
                  />
                </div>

                {project.skillIds.length > 0 ? (
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {project.skillIds.map((id) => (
                      <Badge key={id} variant="secondary" className="text-[11px]">{names.nameOf(id)}</Badge>
                    ))}
                  </div>
                ) : null}

                {project.description ? <p className="mt-2 text-sm">{project.description}</p> : null}
                {project.url ? (
                  <a
                    href={project.url}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-1 block truncate text-sm text-muted-foreground underline underline-offset-2"
                  >
                    {project.url}
                  </a>
                ) : null}

                <ProjectBullets profileId={profileId} project={project} profile={profile} />
              </div>
            ))}
          </div>
        )}
        {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
      </CardContent>

      <ProjectDialog profileId={profileId} project={dialog} onClose={() => setDialog(null)} />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); remove.reset() }
        }}
        title={`Remove ${deleting?.name ?? 'project'}?`}
        description={`This also removes its ${deleting?.bullets.length ?? 0} bullet(s). Any CV already generated from them keeps its stored text but will read as stale.`}
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }}
      />
    </Card>
  )
}

function ProjectBullets({
  profileId,
  project,
  profile,
}: {
  profileId: number
  project: Project
  profile: CandidateProfile
}) {
  const [dialog, setDialog] = useState<ExperienceBullet | 'new' | null>(null)
  const [deleting, setDeleting] = useState<ExperienceBullet | null>(null)
  const names = useSkillNames()

  const reorder = useProfileEdit(
    profileId,
    (ids: number[]) => reorderProjectBullets(profileId, project.id, ids),
    'Bullets reordered',
  )
  const remove = useProfileEdit(profileId, (id: number) => deleteBullet(profileId, id), 'Bullet removed')
  const { bullets } = project

  return (
    <>
      <ul className="mt-2 space-y-2">
        {bullets.map((bullet, index) => (
          <li key={bullet.id} className="flex items-start justify-between gap-3">
            <div className="min-w-0 text-sm">
              <p>{bullet.text}</p>
              {bullet.skillIds.length > 0 ? (
                <div className="mt-1 flex flex-wrap gap-1">
                  {bullet.skillIds.map((id) => (
                    <Badge key={id} variant="outline" className="text-[10px]">{names.nameOf(id)}</Badge>
                  ))}
                </div>
              ) : null}
            </div>
            <RowActions
              label="bullet"
              disabled={reorder.isPending}
              onUp={index > 0 ? () => reorder.mutate(movedIds(bullets, index, index - 1)) : undefined}
              onDown={
                index < bullets.length - 1
                  ? () => reorder.mutate(movedIds(bullets, index, index + 1))
                  : undefined
              }
              onEdit={() => setDialog(bullet)}
              onDelete={() => setDeleting(bullet)}
            />
          </li>
        ))}
      </ul>

      <Button variant="ghost" size="sm" className="mt-1" onClick={() => setDialog('new')}>
        <Plus /> Add bullet
      </Button>
      {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}

      <ProjectBulletDialog
        profileId={profileId}
        bullet={dialog}
        projectId={project.id}
        profile={profile}
        onClose={() => setDialog(null)}
      />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); remove.reset() }
        }}
        title="Remove this bullet?"
        description="A CV that cited it keeps its stored text, but the evidence behind that claim is gone from the profile."
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }}
      />
    </>
  )
}

function ProjectBulletDialog({
  profileId,
  bullet,
  projectId,
  profile,
  onClose,
}: {
  profileId: number
  bullet: ExperienceBullet | 'new' | null
  projectId: number
  profile: CandidateProfile
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [text, setText] = useState('')
  const [skillIds, setSkillIds] = useState<number[]>([])
  const [picking, setPicking] = useState<number | null>(null)
  const names = useSkillNames()

  const key = bullet === 'new' ? 'new' : (bullet?.id ?? null)
  if (bullet !== null && seeded !== key) {
    setSeeded(key)
    setText(bullet === 'new' ? '' : bullet.text)
    setSkillIds(bullet === 'new' ? [] : [...bullet.skillIds])
    setPicking(null)
  }

  const create = useProfileEdit(
    profileId,
    (body: { text: string; skillIds: number[] }) => addProjectBullet(profileId, projectId, body),
    'Bullet added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; text: string; skillIds: number[] }) =>
      updateBullet(profileId, args.id, { text: args.text, skillIds: args.skillIds }),
    'Bullet saved',
  )
  const active = bullet === 'new' ? create : update

  const held = new Set(profile.skills.map((skill) => skill.skillId))

  return (
    <Dialog open={bullet !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{bullet === 'new' ? 'Add bullet' : 'Edit bullet'}</DialogTitle>
          <DialogDescription>
            Tag only the skills this line genuinely evidences — a tag is what lets a tailored CV
            claim the skill, so it can only name skills you already hold.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="project-bullet-text">Text</Label>
            <Textarea
              id="project-bullet-text"
              rows={3}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Built a CLI that cut deploy time from 20 minutes to 2."
            />
            <PolishAction
              profileId={profileId}
              field="EXPERIENCE_BULLET"
              text={text}
              onAccept={setText}
            />
          </div>

          <div className="space-y-1.5">
            <Label>Skills evidenced</Label>
            <div className="flex flex-wrap gap-1.5">
              {skillIds.map((id) => (
                <Badge key={id} variant="secondary" className="gap-1">
                  {names.nameOf(id)}
                  <button
                    type="button"
                    aria-label={`Untag ${names.nameOf(id)}`}
                    onClick={() => setSkillIds(skillIds.filter((other) => other !== id))}
                    className="hover:text-destructive"
                  >
                    <X className="size-3" />
                  </button>
                </Badge>
              ))}
              {skillIds.length === 0 ? (
                <span className="text-sm text-muted-foreground">None tagged.</span>
              ) : null}
            </div>
            <SkillCombobox
              value={picking}
              placeholder="Tag a skill…"
              className="w-full"
              onChange={(id) => {
                if (!skillIds.includes(id)) setSkillIds([...skillIds, id])
                setPicking(null)
              }}
            />
            {skillIds.some((id) => !held.has(id)) ? (
              <p className="text-sm text-destructive">
                Some of these are not on your skills list yet. Add them under Skills first — the
                save will be refused otherwise.
              </p>
            ) : null}
          </div>
        </div>

        {active.isError ? <ApiErrorAlert error={active.error} /> : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!text.trim() || active.isPending}
            onClick={() => {
              const body = { text: text.trim(), skillIds }
              if (bullet === 'new') create.mutate(body, { onSuccess: onClose })
              else if (bullet) update.mutate({ id: bullet.id, ...body }, { onSuccess: onClose })
            }}
          >
            {active.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ProjectDialog({
  profileId,
  project,
  onClose,
}: {
  profileId: number
  project: Project | 'new' | null
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [description, setDescription] = useState('')
  const [startedOn, setStartedOn] = useState('')
  const [endedOn, setEndedOn] = useState('')
  const [skillIds, setSkillIds] = useState<number[]>([])
  const [picking, setPicking] = useState<number | null>(null)
  const names = useSkillNames()

  const key = project === 'new' ? 'new' : (project?.id ?? null)
  if (project !== null && seeded !== key) {
    setSeeded(key)
    const source = project === 'new' ? null : project
    setName(source?.name ?? '')
    setUrl(source?.url ?? '')
    setDescription(source?.description ?? '')
    setStartedOn(source?.startedOn ?? '')
    setEndedOn(source?.endedOn ?? '')
    setSkillIds(source ? [...source.skillIds] : [])
    setPicking(null)
  }

  const create = useProfileEdit(
    profileId,
    (body: Parameters<typeof addProject>[1]) => addProject(profileId, body),
    'Project added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; body: Parameters<typeof updateProject>[2] }) =>
      updateProject(profileId, args.id, args.body),
    'Project saved',
  )
  const active = project === 'new' ? create : update

  return (
    <Dialog open={project !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{project === 'new' ? 'Add project' : 'Edit project'}</DialogTitle>
          <DialogDescription>
            A project's URL is never sent to a model, the same rule your name and photo already
            follow — it only ever reaches the rendered document straight from here.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <Field label="Name" value={name} onChange={setName} placeholder="Side project" />
          <PolishAction profileId={profileId} field="PROJECT_NAME" text={name} onAccept={setName} />
          <Field label="URL" value={url} onChange={setUrl} placeholder="https://github.com/you/project" />
          <div className="space-y-1.5">
            <Label htmlFor="project-description">Description</Label>
            <Textarea
              id="project-description"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <PolishAction
              profileId={profileId}
              field="PROJECT_DESCRIPTION"
              text={description}
              onAccept={setDescription}
            />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Started on" value={startedOn} onChange={setStartedOn} type="date" />
            <Field label="Ended on" value={endedOn} onChange={setEndedOn} type="date" />
          </div>

          <div className="space-y-1.5">
            <Label>Skills used</Label>
            <div className="flex flex-wrap gap-1.5">
              {skillIds.map((id) => (
                <Badge key={id} variant="secondary" className="gap-1">
                  {names.nameOf(id)}
                  <button
                    type="button"
                    aria-label={`Untag ${names.nameOf(id)}`}
                    onClick={() => setSkillIds(skillIds.filter((other) => other !== id))}
                    className="hover:text-destructive"
                  >
                    <X className="size-3" />
                  </button>
                </Badge>
              ))}
              {skillIds.length === 0 ? (
                <span className="text-sm text-muted-foreground">None declared.</span>
              ) : null}
            </div>
            <SkillCombobox
              value={picking}
              placeholder="Tag a skill…"
              className="w-full"
              onChange={(id) => {
                if (!skillIds.includes(id)) setSkillIds([...skillIds, id])
                setPicking(null)
              }}
            />
            <p className="text-xs text-muted-foreground">
              Only skills already on your profile can be used here — this is a badge for the
              project, separate from what its bullets individually evidence.
            </p>
          </div>
        </div>

        {active.isError ? <ApiErrorAlert error={active.error} /> : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!name.trim() || active.isPending}
            onClick={() => {
              const body = {
                name: name.trim(),
                url: blankToNull(url),
                description: blankToNull(description),
                startedOn: startedOn || null,
                endedOn: endedOn || null,
                skillIds,
              }
              if (project === 'new') create.mutate(body, { onSuccess: onClose })
              else if (project) update.mutate({ id: project.id, body }, { onSuccess: onClose })
            }}
          >
            {active.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
