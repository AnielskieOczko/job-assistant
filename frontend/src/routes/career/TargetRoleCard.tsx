import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Crosshair, Pencil, Trash2, X } from 'lucide-react'
import { toast } from 'sonner'
import { keys } from '@/api/keys'
import { deleteTargetRole, getTargetRole, putTargetRole } from '@/api/profile'
import type { SeniorityLevel } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { SkillCombobox } from '@/components/SkillCombobox'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useSkillNames } from '@/hooks/useSkillNames'
import { formatSeniority } from './format'

const SENIORITIES: SeniorityLevel[] = ['INTERN', 'JUNIOR', 'REGULAR', 'SENIOR']

export function TargetRoleCard({ profileId }: { profileId: number }) {
  const qc = useQueryClient()
  const skillsQuery = useSkillNames()
  const roleQuery = useQuery({
    queryKey: keys.targetRole(profileId),
    queryFn: () => getTargetRole(profileId),
  })

  const [editing, setEditing] = useState(false)
  const [keywordsText, setKeywordsText] = useState('')
  const [targetLevel, setTargetLevel] = useState<SeniorityLevel>('SENIOR')
  const [skillIds, setSkillIds] = useState<number[]>([])

  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: keys.targetRole(profileId) })
    void qc.invalidateQueries({ queryKey: ['market', 'demand'] })
    void qc.invalidateQueries({ queryKey: ['market', 'offers'] })
  }

  const save = useMutation({
    mutationFn: () => {
      const titleKeywords = keywordsText
        .split(',')
        .map((k) => k.trim())
        .filter(Boolean)
      return putTargetRole(profileId, { targetLevel, skillIds, titleKeywords })
    },
    onSuccess: () => {
      toast.success('Target role saved')
      setEditing(false)
      invalidate()
    },
  })

  const clear = useMutation({
    mutationFn: () => deleteTargetRole(profileId),
    onSuccess: () => {
      toast.success('Target role cleared')
      setEditing(false)
      invalidate()
    },
  })

  const startEdit = () => {
    const current = roleQuery.data
    setKeywordsText(current?.titleKeywords.join(', ') ?? '')
    setTargetLevel(current?.targetLevel ?? 'SENIOR')
    setSkillIds(current?.skills.map((s) => s.id) ?? [])
    setEditing(true)
  }

  const skillNameById = new Map((skillsQuery.data ?? []).map((s) => [s.id, s.name]))
  const current = roleQuery.data

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Crosshair className="size-4" />
          Target Role
        </CardTitle>
        <CardDescription>
          Defines the market scope for demand analytics and career planning.
        </CardDescription>
        {!editing ? (
          <CardAction className="flex gap-2">
            <Button size="sm" variant="outline" onClick={startEdit}>
              <Pencil className="size-3.5" /> {current ? 'Edit' : 'Set target role'}
            </Button>
            {current ? (
              <Button
                size="sm"
                variant="ghost"
                disabled={clear.isPending}
                onClick={() => clear.mutate()}
              >
                <Trash2 className="size-3.5" />
              </Button>
            ) : null}
          </CardAction>
        ) : null}
      </CardHeader>

      <CardContent className="space-y-4">
        {roleQuery.isError ? <ApiErrorAlert error={roleQuery.error} /> : null}
        {save.isError ? <ApiErrorAlert error={save.error} /> : null}

        {editing ? (
          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="target-role-keywords">Title keywords (comma-separated)</Label>
                <Input
                  id="target-role-keywords"
                  value={keywordsText}
                  onChange={(e) => setKeywordsText(e.target.value)}
                  placeholder="e.g. Backend, Java, Kotlin"
                />
              </div>
              <div className="space-y-1.5">
                <Label>Target seniority</Label>
                <Select value={targetLevel} onValueChange={(v) => setTargetLevel(v as SeniorityLevel)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SENIORITIES.map((s) => (
                      <SelectItem key={s} value={s}>
                        {formatSeniority(s)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <Label>Scope skills (offers matching any of these are in scope)</Label>
              <SkillCombobox
                value={null}
                allowCreate={false}
                placeholder="Add a skill to target scope…"
                onChange={(id) => {
                  if (!skillIds.includes(id)) setSkillIds([...skillIds, id])
                }}
              />
              {skillIds.length > 0 ? (
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {skillIds.map((id) => (
                    <Badge key={id} variant="secondary" className="gap-1">
                      {skillNameById.get(id) ?? `#${id}`}
                      <button
                        type="button"
                        aria-label="Remove skill"
                        onClick={() => setSkillIds(skillIds.filter((x) => x !== id))}
                        className="ml-0.5 rounded-full hover:text-destructive"
                      >
                        <X className="size-3" />
                      </button>
                    </Badge>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="flex gap-2">
              <Button disabled={save.isPending} onClick={() => save.mutate()}>
                {save.isPending ? 'Saving…' : 'Save target role'}
              </Button>
              <Button variant="ghost" onClick={() => setEditing(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : current ? (
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              {current.targetLevel ? (
                <Badge variant="outline">{formatSeniority(current.targetLevel)}</Badge>
              ) : null}
              {current.titleKeywords.length > 0 ? (
                <span className="text-sm font-medium">{current.titleKeywords.join(', ')}</span>
              ) : null}
            </div>
            {current.skills.length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {current.skills.map((s) => (
                  <Badge key={s.id} variant="secondary">
                    {s.name}
                  </Badge>
                ))}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">
                No specific scope skills pinned; falling back to default market scope.
              </p>
            )}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            No target role set yet. Using application default market scope.
          </p>
        )}
      </CardContent>
    </Card>
  )
}
