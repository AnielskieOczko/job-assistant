import { useState } from 'react'
import { Plus, X } from 'lucide-react'
import {
  addBullet, addExperience, deleteBullet, deleteExperience, reorderBullets, reorderExperiences,
  updateBullet, updateExperience,
} from '@/api/profile'
import { isCurrentRole } from '@/api/types'
import type { CandidateProfile, ExperienceBullet, WorkExperience } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
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
import { RowActions } from './RowActions'
import { blankToNull, movedIds, useProfileEdit } from './mutations'

export function ExperienceCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [dialog, setDialog] = useState<WorkExperience | 'new' | null>(null)
  const [deleting, setDeleting] = useState<WorkExperience | null>(null)

  const reorder = useProfileEdit(profileId, (ids: number[]) => reorderExperiences(profileId, ids), 'Roles reordered')
  const remove = useProfileEdit(profileId, (id: number) => deleteExperience(profileId, id), 'Role removed')
  const { experiences } = profile

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Experience</CardTitle>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => setDialog('new')}>
                <Plus /> Add role
              </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-6">
        {experiences.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No roles yet. Bullets under a role are what a tailored CV is allowed to draw on.
          </p>
        ) : (
          experiences.map((experience, index) => (
            <div key={experience.id} className="border-l-2 pl-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-baseline gap-2">
                    <p className="font-medium">{experience.roleTitle}</p>
                    <p className="text-muted-foreground">{experience.company}</p>
                    {isCurrentRole(experience) ? <Badge variant="secondary">Current</Badge> : null}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {formatPeriod(experience.startedOn, experience.endedOn)}
                    {experience.location ? ` · ${experience.location}` : ''}
                  </p>
                </div>
                <RowActions
                  label={experience.roleTitle}
                  disabled={reorder.isPending}
                  onUp={index > 0 ? () => reorder.mutate(movedIds(experiences, index, index - 1)) : undefined}
                  onDown={
                    index < experiences.length - 1
                      ? () => reorder.mutate(movedIds(experiences, index, index + 1))
                      : undefined
                  }
                  onEdit={() => setDialog(experience)}
                  onDelete={() => setDeleting(experience)}
                />
              </div>

              {experience.summary ? <p className="mt-2 text-sm">{experience.summary}</p> : null}

              <Bullets profileId={profileId} experience={experience} profile={profile} />
            </div>
          ))
        )}
        {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
      </CardContent>

      <ExperienceDialog profileId={profileId} experience={dialog} onClose={() => setDialog(null)} />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); remove.reset() }
        }}
        title={`Remove ${deleting?.roleTitle ?? 'role'}?`}
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

function Bullets({
  profileId,
  experience,
  profile,
}: {
  profileId: number
  experience: WorkExperience
  profile: CandidateProfile
}) {
  const [dialog, setDialog] = useState<ExperienceBullet | 'new' | null>(null)
  const [deleting, setDeleting] = useState<ExperienceBullet | null>(null)
  const names = useSkillNames()

  const reorder = useProfileEdit(
    profileId,
    (ids: number[]) => reorderBullets(profileId, experience.id, ids),
    'Bullets reordered',
  )
  const remove = useProfileEdit(profileId, (id: number) => deleteBullet(profileId, id), 'Bullet removed')
  const { bullets } = experience

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

      <BulletDialog
        profileId={profileId}
        bullet={dialog}
        experienceId={experience.id}
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

function BulletDialog({
  profileId,
  bullet,
  experienceId,
  profile,
  onClose,
}: {
  profileId: number
  bullet: ExperienceBullet | 'new' | null
  experienceId: number
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
    (body: { text: string; skillIds: number[] }) => addBullet(profileId, experienceId, body),
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
            <Label htmlFor="bullet-text">Text</Label>
            <Textarea
              id="bullet-text"
              rows={3}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Cut checkout latency by 40% by rewriting the pricing service."
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

function ExperienceDialog({
  profileId,
  experience,
  onClose,
}: {
  profileId: number
  experience: WorkExperience | 'new' | null
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [company, setCompany] = useState('')
  const [roleTitle, setRoleTitle] = useState('')
  const [location, setLocation] = useState('')
  const [startedOn, setStartedOn] = useState('')
  const [endedOn, setEndedOn] = useState('')
  const [summary, setSummary] = useState('')

  const key = experience === 'new' ? 'new' : (experience?.id ?? null)
  if (experience !== null && seeded !== key) {
    setSeeded(key)
    const source = experience === 'new' ? null : experience
    setCompany(source?.company ?? '')
    setRoleTitle(source?.roleTitle ?? '')
    setLocation(source?.location ?? '')
    setStartedOn(source?.startedOn ?? '')
    setEndedOn(source?.endedOn ?? '')
    setSummary(source?.summary ?? '')
  }

  const create = useProfileEdit(
    profileId,
    (body: Parameters<typeof addExperience>[1]) => addExperience(profileId, body),
    'Role added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; body: Parameters<typeof updateExperience>[2] }) =>
      updateExperience(profileId, args.id, args.body),
    'Role saved',
  )
  const active = experience === 'new' ? create : update

  return (
    <Dialog open={experience !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{experience === 'new' ? 'Add role' : 'Edit role'}</DialogTitle>
          <DialogDescription>
            Leave the end date empty for a role you are still in. Editing a role never disturbs the
            bullets under it.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Company" value={company} onChange={setCompany} />
            <Field label="Role title" value={roleTitle} onChange={setRoleTitle} />
          </div>
          <Field label="Location" value={location} onChange={setLocation} placeholder="Warsaw, Poland" />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Started on" value={startedOn} onChange={setStartedOn} type="date" />
            <Field label="Ended on" value={endedOn} onChange={setEndedOn} type="date" />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="experience-summary">Summary</Label>
            <Textarea
              id="experience-summary"
              rows={3}
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
            />
          </div>
        </div>

        {active.isError ? <ApiErrorAlert error={active.error} /> : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!company.trim() || !roleTitle.trim() || !startedOn || active.isPending}
            onClick={() => {
              const body = {
                company: company.trim(),
                roleTitle: roleTitle.trim(),
                location: blankToNull(location),
                startedOn,
                endedOn: endedOn || null,
                summary: blankToNull(summary),
              }
              if (experience === 'new') create.mutate(body, { onSuccess: onClose })
              else if (experience) update.mutate({ id: experience.id, body }, { onSuccess: onClose })
            }}
          >
            {active.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
