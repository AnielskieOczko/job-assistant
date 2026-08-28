import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Inbox, Plus, X } from 'lucide-react'
import { toast } from 'sonner'
import {
  approveUnmatched, createSkill, deleteSkill, rejectUnmatched, updateSkill,
} from '@/api/catalog'
import { fetchTriageQueue } from '@/api/triage'
import { keys } from '@/api/keys'
import { SKILL_CATEGORIES, SKILL_CATEGORY_LABELS } from '@/api/types'
import type { CanonicalSkill, SkillCategory, TriageRanking } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { SkillCombobox } from '@/components/SkillCombobox'
import { useSkillNames } from '@/hooks/useSkillNames'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatRelative } from '@/lib/format'
import { ConfirmDelete } from './profile/ConfirmDelete'

export function CatalogPage() {
  return (
    <>
      <PageHeader
        title="Catalog"
        description="Everything downstream refers to a canonical skill id, never free text. Terms the extractor cannot place wait here for review — code is never allowed to create a skill on its own."
      />
      <Tabs defaultValue="queue">
        <TabsList className="mb-4">
          <TabsTrigger value="queue">Review queue</TabsTrigger>
          <TabsTrigger value="skills">All skills</TabsTrigger>
        </TabsList>
        <TabsContent value="queue"><ReviewQueue /></TabsContent>
        <TabsContent value="skills"><SkillBrowser /></TabsContent>
      </Tabs>
    </>
  )
}

/** How many rows one page of the queue shows. The response says how many it left out. */
const QUEUE_LIMIT = 100

/**
 * Thresholds offered in the UI, applied to your count plus the market's.
 *
 * 3 is the default because 56% of distinct corpus names appear exactly once and those are what
 * make the queue unopenable. 1 is kept so the tail stays reachable - the filter hides terms, it
 * never deletes them, and there has to be a way to see that for yourself.
 */
const THRESHOLDS = [1, 2, 3, 5, 10] as const

function ReviewQueue() {
  const queryClient = useQueryClient()
  const [minOccurrences, setMinOccurrences] = useState<number>(3)
  const [ranking, setRanking] = useState<TriageRanking>('SCOPE')
  const queue = useQuery({
    queryKey: keys.triageQueue(minOccurrences, ranking, QUEUE_LIMIT),
    queryFn: () => fetchTriageQueue(minOccurrences, ranking, QUEUE_LIMIT),
  })
  const [selection, setSelection] = useState<Record<number, number>>({})
  const [createFor, setCreateFor] = useState<string | null>(null)

  function invalidate() {
    // Every threshold and ranking combination is a separate cache entry, so invalidate the whole
    // triage prefix rather than the one currently on screen - an approval removes the term from
    // all of them.
    queryClient.invalidateQueries({ queryKey: ['triage'] })
    queryClient.invalidateQueries({ queryKey: keys.unmatched })
    queryClient.invalidateQueries({ queryKey: keys.skills })
  }

  const approve = useMutation({
    mutationFn: ({ termId, skillId }: { termId: number; skillId: number }) =>
      approveUnmatched(termId, skillId),
    onSuccess: (skill) => {
      toast.success(`Approved as “${skill.name}” — it will resolve from now on`)
      invalidate()
    },
  })

  const reject = useMutation({
    mutationFn: (termId: number) => rejectUnmatched(termId),
    onSuccess: () => {
      toast.success('Term rejected')
      invalidate()
    },
  })

  if (queue.isPending) return <Skeleton className="h-48 w-full" />
  if (queue.isError) return <ApiErrorAlert error={queue.error} />

  const { entries, matching, pending, scopeSkills } = queue.data
  const scopeLabel = scopeSkills.length > 0 ? scopeSkills.join(', ') : 'nothing configured'

  const controls = (
    <div className="mb-4 flex flex-wrap items-center gap-x-6 gap-y-3">
      <div className="flex items-center gap-2">
        <Label htmlFor="queue-threshold" className="text-sm font-normal text-muted-foreground">
          Seen at least
        </Label>
        <Select
          value={String(minOccurrences)}
          onValueChange={(value) => setMinOccurrences(Number(value))}
        >
          <SelectTrigger id="queue-threshold" className="w-20"><SelectValue /></SelectTrigger>
          <SelectContent>
            {THRESHOLDS.map((n) => (
              <SelectItem key={n} value={String(n)}>{n}×</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center gap-2">
        <Label htmlFor="queue-ranking" className="text-sm font-normal text-muted-foreground">
          Rank by
        </Label>
        <Select value={ranking} onValueChange={(value) => setRanking(value as TriageRanking)}>
          <SelectTrigger id="queue-ranking" className="w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="SCOPE">Your part of the market</SelectItem>
            <SelectItem value="CORPUS">The whole corpus</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/*
        The denominator, always. With 1,500 queued terms and a 100-row page, a short list with no
        counts reads as a finished queue — the same failure as a rate printed without its
        denominator. Say what was returned, what matched, and what is queued in total.
      */}
      <p className="text-sm text-muted-foreground">
        Showing <strong className="font-medium text-foreground">{entries.length}</strong> of{' '}
        {matching.toLocaleString()} matching · {pending.toLocaleString()} queued
      </p>
    </div>
  )

  if (entries.length === 0) {
    return (
      <>
        {controls}
        <EmptyState
          icon={Inbox}
          title={pending === 0 ? 'Nothing to review' : 'Nothing above this threshold'}
          description={
            pending === 0
              ? 'Skill names the catalog cannot place collect here — from offers you analyse, and from the ingested market corpus.'
              : `${pending.toLocaleString()} terms are queued, none seen ${minOccurrences} times or more. Nothing was deleted — lower the threshold to see them.`
          }
        />
      </>
    )
  }

  return (
    <>
      {approve.isError ? <ApiErrorAlert error={approve.error} /> : null}
      {reject.isError ? <ApiErrorAlert error={reject.error} /> : null}

      {controls}

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Term</TableHead>
              <TableHead className="w-24">Your offers</TableHead>
              <TableHead className="w-24" title={`Corpus offers that also ask for: ${scopeLabel}`}>
                In scope
              </TableHead>
              <TableHead className="w-24">Market</TableHead>
              <TableHead className="w-32">Last seen</TableHead>
              <TableHead className="w-[29rem]">Resolve to</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.map((term) => (
              <TableRow key={term.termId}>
                <TableCell className="max-w-0 truncate font-medium" title={term.term}>{term.term}</TableCell>
                {/*
                  Three counters, never summed in the UI. What you read outranks both market
                  numbers; in-scope demand is the market you are actually applying to; the corpus
                  column is the whole IT division including the QA and BA roles you are not.
                */}
                <TableCell className="text-muted-foreground">{term.occurrences}×</TableCell>
                <TableCell className={term.inScopeDemand > 0 ? 'font-medium' : 'text-muted-foreground'}>
                  {term.inScopeDemand > 0 ? `${term.inScopeDemand}×` : '—'}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {term.marketOccurrences > 0 ? `${term.marketOccurrences}×` : '—'}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatRelative(term.lastSeenAt)}
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap items-center gap-2">
                    <SkillCombobox
                      value={selection[term.termId] ?? null}
                      onChange={(skillId) => setSelection((s) => ({ ...s, [term.termId]: skillId }))}
                      className="w-44"
                    />
                    <Button
                      size="sm"
                      disabled={selection[term.termId] === undefined || approve.isPending}
                      onClick={() =>
                        approve.mutate({ termId: term.termId, skillId: selection[term.termId] })
                      }
                    >
                      <Check /> Approve
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setCreateFor(term.term)}>
                      <Plus /> New
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={reject.isPending}
                      onClick={() => reject.mutate(term.termId)}
                    >
                      <X /> Reject
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <CreateSkillDialog
        term={createFor}
        onClose={() => setCreateFor(null)}
        onCreated={invalidate}
      />
    </>
  )
}

function CreateSkillDialog({
  term,
  onClose,
  onCreated,
}: {
  term: string | null
  onClose: () => void
  onCreated: () => void
}) {
  const [name, setName] = useState('')
  const [category, setCategory] = useState<SkillCategory>('OTHER')
  const [aliases, setAliases] = useState('')

  // Reset the form each time a different term opens the dialog.
  const [seeded, setSeeded] = useState<string | null>(null)
  if (term !== null && seeded !== term) {
    setSeeded(term)
    setName(term)
    setAliases(term)
    setCategory('OTHER')
  }

  const create = useMutation({
    mutationFn: () =>
      createSkill({
        name: name.trim(),
        category,
        aliases: aliases.split(',').map((a) => a.trim()).filter(Boolean),
      }),
    onSuccess: (skill) => {
      toast.success(`Created “${skill.name}”`)
      onCreated()
      onClose()
    },
  })

  return (
    <Dialog open={term !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a skill to the catalog</DialogTitle>
          <DialogDescription>
            Idempotent: if the name already exists, the existing skill is returned.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="skill-name">Name</Label>
            <Input id="skill-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label>Category</Label>
            <Select value={category} onValueChange={(v) => setCategory(v as SkillCategory)}>
              <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {SKILL_CATEGORIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="skill-aliases">Aliases (comma separated)</Label>
            <Input
              id="skill-aliases"
              value={aliases}
              onChange={(e) => setAliases(e.target.value)}
              placeholder="Iceberg, apache-iceberg"
            />
          </div>
          {create.isError ? <ApiErrorAlert error={create.error} /> : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={() => create.mutate()} disabled={!name.trim() || create.isPending}>
            {create.isPending ? 'Creating…' : 'Create skill'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function SkillBrowser() {
  const queryClient = useQueryClient()
  const skills = useSkillNames()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState<string>('__all__')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<CanonicalSkill | null>(null)
  const [deleting, setDeleting] = useState<CanonicalSkill | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: keys.skills })
  }

  const remove = useMutation({
    mutationFn: (id: number) => deleteSkill(id),
    onSuccess: () => {
      toast.success('Skill deleted')
      invalidate()
      setDeleting(null)
    },
  })

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (skills.data ?? [])
      .filter((s) => category === '__all__' || s.category === category)
      .filter((s) => !term || s.name.toLowerCase().includes(term))
  }, [skills.data, search, category])

  const groups = useMemo(
    () => SKILL_CATEGORIES
      .map((c) => ({ category: c, items: rows.filter((s) => s.category === c) }))
      .filter((group) => group.items.length > 0),
    [rows],
  )

  if (skills.isPending) return <Skeleton className="h-48 w-full" />
  if (skills.isError) return <ApiErrorAlert error={skills.error} />

  return (
    <>
      <div className="mb-4 flex items-center gap-2">
        <Input
          placeholder="Search skills…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="max-w-xs"
        />
        <Select value={category} onValueChange={setCategory}>
          <SelectTrigger className="w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All categories</SelectItem>
            {SKILL_CATEGORIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
          </SelectContent>
        </Select>
        <span className="ml-auto text-sm text-muted-foreground">{rows.length} skills</span>
        <Button size="sm" variant="outline" onClick={() => setCreating(true)}>
          <Plus /> Add skill
        </Button>
      </div>
      <div className="space-y-4">
        {groups.map((group) => (
          <div key={group.category}>
            <h4 className="mb-1.5 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
              {SKILL_CATEGORY_LABELS[group.category]}
            </h4>
            <div className="flex flex-wrap gap-1.5">
              {group.items.map((skill) => (
                <Badge
                  key={skill.id}
                  variant="outline"
                  className="cursor-pointer hover:bg-muted"
                  onClick={() => setEditing(skill)}
                >
                  {skill.name}
                </Badge>
              ))}
            </div>
          </div>
        ))}
      </div>

      <CreateSkillDialog term={creating ? '' : null} onClose={() => setCreating(false)} onCreated={invalidate} />

      <EditSkillDialog
        skill={editing}
        onClose={() => setEditing(null)}
        onSaved={invalidate}
        onRequestDelete={() => { setDeleting(editing); setEditing(null) }}
      />

      <ConfirmDelete
        open={deleting !== null}
        onOpenChange={(open) => { if (!open) { setDeleting(null); remove.reset() } }}
        title={`Remove ${deleting?.name ?? 'this skill'} from the catalog?`}
        description="Refused while a profile still holds it or a bullet is still tagged with it - untag or remove it there first."
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => { if (deleting) remove.mutate(deleting.id) }}
      />
    </>
  )
}

function EditSkillDialog({
  skill,
  onClose,
  onSaved,
  onRequestDelete,
}: {
  skill: CanonicalSkill | null
  onClose: () => void
  onSaved: () => void
  onRequestDelete: () => void
}) {
  const [name, setName] = useState('')
  const [category, setCategory] = useState<SkillCategory>('OTHER')

  // Reset the form each time a different skill opens the dialog.
  const [seeded, setSeeded] = useState<number | null>(null)
  if (skill !== null && seeded !== skill.id) {
    setSeeded(skill.id)
    setName(skill.name)
    setCategory(skill.category)
  }

  const update = useMutation({
    mutationFn: () => updateSkill(skill!.id, { name: name.trim(), category }),
    onSuccess: (updated) => {
      toast.success(`Saved “${updated.name}”`)
      onSaved()
      onClose()
    },
  })

  return (
    <Dialog open={skill !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit skill</DialogTitle>
          <DialogDescription>
            Renaming adds the new name as another alias rather than replacing the old one, so
            nothing that already resolved to this skill stops working.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="edit-skill-name">Name</Label>
            <Input id="edit-skill-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label>Category</Label>
            <Select value={category} onValueChange={(v) => setCategory(v as SkillCategory)}>
              <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {SKILL_CATEGORIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          {update.isError ? <ApiErrorAlert error={update.error} /> : null}
        </div>

        <DialogFooter>
          <Button type="button" variant="destructive" className="sm:mr-auto" onClick={onRequestDelete}>
            Delete
          </Button>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={() => update.mutate()} disabled={!name.trim() || update.isPending}>
            {update.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
