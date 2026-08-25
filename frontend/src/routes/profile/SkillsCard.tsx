import { useState } from 'react'
import { Plus } from 'lucide-react'
import { addSkill, deleteSkill, reorderSkills, updateSkill } from '@/api/profile'
import { PROFICIENCIES, SKILL_CATEGORIES, SKILL_CATEGORY_LABELS } from '@/api/types'
import type { CandidateProfile, Proficiency, ProfileSkill, SkillCategory } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { SkillCombobox } from '@/components/SkillCombobox'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { useSkillNames } from '@/hooks/useSkillNames'
import { cn } from '@/lib/utils'
import { ConfirmDelete } from './ConfirmDelete'
import { RowActions } from './RowActions'
import { swappedIds, useProfileEdit } from './mutations'

/**
 * Mastery as ink, not hue: this app is deliberately near-monochrome, so proficiency reads as
 * increasing weight - faint outline at Beginner, solid fill only at Expert - rather than a
 * traffic-light palette that would be the only color anywhere outside error states.
 */
const PROFICIENCY_STYLES: Record<Proficiency, string> = {
  BEGINNER: 'border-foreground/15 text-foreground/50',
  WORKING: 'border-foreground/25 text-foreground/70',
  PROFICIENT: 'border-foreground/40 text-foreground/90',
  EXPERT: 'border-transparent bg-foreground text-background',
}

function ProficiencyBadge({ level }: { level: Proficiency }) {
  return (
    <Badge variant="outline" className={cn('capitalize', PROFICIENCY_STYLES[level])}>
      {level.toLowerCase()}
    </Badge>
  )
}

export function SkillsCard({ profileId, profile }: { profileId: number; profile: CandidateProfile }) {
  const [dialog, setDialog] = useState<ProfileSkill | 'new' | null>(null)
  const [deleting, setDeleting] = useState<ProfileSkill | null>(null)
  const names = useSkillNames()

  const reorder = useProfileEdit(profileId, (ids: number[]) => reorderSkills(profileId, ids), 'Skills reordered')
  const remove = useProfileEdit(profileId, (id: number) => deleteSkill(profileId, id), 'Skill removed')
  const { skills } = profile

  const groups: { category: SkillCategory | 'UNCATEGORIZED'; label: string; items: ProfileSkill[] }[] = [
    ...SKILL_CATEGORIES.map((category) => ({
      category,
      label: SKILL_CATEGORY_LABELS[category],
      items: skills.filter((skill) => names.byId.get(skill.skillId)?.category === category),
    })),
    {
      category: 'UNCATEGORIZED' as const,
      label: 'Uncategorized',
      items: skills.filter((skill) => names.byId.get(skill.skillId) === undefined),
    },
  ].filter((group) => group.items.length > 0)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Skills ({skills.length})</CardTitle>
        <CardAction>
          <Button variant="outline" size="sm" onClick={() => setDialog('new')}>
                <Plus /> Add skill
              </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {skills.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No skills yet. The gap report has nothing to compare an offer against until you add some.
          </p>
        ) : (
          <div className="space-y-4">
            {groups.map((group) => (
              <div key={group.category}>
                <h4 className="mb-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                  {group.label}
                </h4>
                <ul className="divide-y">
                  {group.items.map((skill, index) => {
                    const meta = [
                      skill.yearsOfExperience !== null ? `${skill.yearsOfExperience}y experience` : null,
                      skill.lastUsedYear !== null ? `last used ${skill.lastUsedYear}` : null,
                    ].filter(Boolean).join(' · ')
                    return (
                    <li key={skill.id} className="flex items-center justify-between gap-3 py-2">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="truncate text-sm font-medium">{names.nameOf(skill.skillId)}</span>
                          <ProficiencyBadge level={skill.proficiency} />
                        </div>
                        {meta ? <p className="mt-0.5 text-xs text-muted-foreground">{meta}</p> : null}
                      </div>
                      <RowActions
                        label={names.nameOf(skill.skillId)}
                        disabled={reorder.isPending}
                        onUp={
                          index > 0
                            ? () => reorder.mutate(swappedIds(skills, skill.id, group.items[index - 1].id))
                            : undefined
                        }
                        onDown={
                          index < group.items.length - 1
                            ? () => reorder.mutate(swappedIds(skills, skill.id, group.items[index + 1].id))
                            : undefined
                        }
                        onEdit={() => setDialog(skill)}
                        onDelete={() => setDeleting(skill)}
                      />
                    </li>
                    )
                  })}
                </ul>
              </div>
            ))}
          </div>
        )}
        {reorder.isError ? <ApiErrorAlert error={reorder.error} /> : null}
      </CardContent>

      <SkillDialog profileId={profileId} skill={dialog} onClose={() => setDialog(null)} />
      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) { setDeleting(null); remove.reset() }
        }}
        title={`Remove ${deleting ? names.nameOf(deleting.skillId) : 'skill'}?`}
        description="If any experience bullet still cites this skill the removal is refused, and the bullets in the way are listed here."
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
        }}
      />
    </Card>
  )
}

function SkillDialog({
  profileId,
  skill,
  onClose,
}: {
  profileId: number
  skill: ProfileSkill | 'new' | null
  onClose: () => void
}) {
  const [seeded, setSeeded] = useState<number | 'new' | null>(null)
  const [skillId, setSkillId] = useState<number | null>(null)
  const [proficiency, setProficiency] = useState<Proficiency>('WORKING')
  const [years, setYears] = useState('')
  const [lastUsed, setLastUsed] = useState('')

  const key = skill === 'new' ? 'new' : (skill?.id ?? null)
  if (skill !== null && seeded !== key) {
    setSeeded(key)
    setSkillId(skill === 'new' ? null : skill.skillId)
    setProficiency(skill === 'new' ? 'WORKING' : skill.proficiency)
    setYears(skill === 'new' || skill.yearsOfExperience === null ? '' : String(skill.yearsOfExperience))
    setLastUsed(skill === 'new' || skill.lastUsedYear === null ? '' : String(skill.lastUsedYear))
  }

  const create = useProfileEdit(
    profileId,
    (body: { skillId: number; proficiency: Proficiency; yearsOfExperience: number | null; lastUsedYear: number | null }) =>
      addSkill(profileId, body),
    'Skill added',
  )
  const update = useProfileEdit(
    profileId,
    (args: { id: number; proficiency: Proficiency; yearsOfExperience: number | null; lastUsedYear: number | null }) =>
      updateSkill(profileId, args.id, {
        proficiency: args.proficiency,
        yearsOfExperience: args.yearsOfExperience,
        lastUsedYear: args.lastUsedYear,
      }),
    'Skill saved',
  )
  const active = skill === 'new' ? create : update

  const numbers = {
    yearsOfExperience: years.trim() ? Number(years) : null,
    lastUsedYear: lastUsed.trim() ? Number(lastUsed) : null,
  }

  return (
    <Dialog open={skill !== null} onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{skill === 'new' ? 'Add skill' : 'Edit skill'}</DialogTitle>
          <DialogDescription>
            {skill === 'new'
              ? 'Skills come from the catalog so every gap report compares like with like.'
              : 'Which skill this is cannot be changed — that would strand any bullet citing it. Remove it and add the other one instead.'}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          {skill === 'new' ? (
            <div className="space-y-1.5">
              <Label>Skill</Label>
              <SkillCombobox value={skillId} onChange={setSkillId} className="w-full" allowCreate />
            </div>
          ) : null}

          <div className="space-y-1.5">
            <Label htmlFor="skill-proficiency">Proficiency</Label>
            <Select value={proficiency} onValueChange={(next) => setProficiency(next as Proficiency)}>
              <SelectTrigger id="skill-proficiency" className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {PROFICIENCIES.map((option) => (
                  <SelectItem key={option} value={option}>{option.toLowerCase()}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="skill-years">Years of experience</Label>
              <Input
                id="skill-years"
                inputMode="decimal"
                value={years}
                placeholder="3.5"
                onChange={(e) => setYears(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="skill-last-used">Last used (year)</Label>
              <Input
                id="skill-last-used"
                inputMode="numeric"
                value={lastUsed}
                placeholder="2026"
                onChange={(e) => setLastUsed(e.target.value)}
              />
            </div>
          </div>
        </div>

        {active.isError ? <ApiErrorAlert error={active.error} /> : null}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button
            disabled={active.isPending || (skill === 'new' && skillId === null)}
            onClick={() => {
              if (skill === 'new') {
                if (skillId === null) return
                create.mutate({ skillId, proficiency, ...numbers }, { onSuccess: onClose })
              } else if (skill) {
                update.mutate({ id: skill.id, proficiency, ...numbers }, { onSuccess: onClose })
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
