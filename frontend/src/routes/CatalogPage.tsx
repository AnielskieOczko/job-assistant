import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Inbox, Plus, X } from 'lucide-react'
import { toast } from 'sonner'
import {
  approveUnmatched, createSkill, listUnmatched, rejectUnmatched,
} from '@/api/catalog'
import { keys } from '@/api/keys'
import { SKILL_CATEGORIES } from '@/api/types'
import type { SkillCategory } from '@/api/types'
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

function ReviewQueue() {
  const queryClient = useQueryClient()
  const unmatched = useQuery({ queryKey: keys.unmatched, queryFn: () => listUnmatched(100) })
  const [selection, setSelection] = useState<Record<number, number>>({})
  const [createFor, setCreateFor] = useState<string | null>(null)

  function invalidate() {
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

  if (unmatched.isPending) return <Skeleton className="h-48 w-full" />
  if (unmatched.isError) return <ApiErrorAlert error={unmatched.error} />
  if (unmatched.data.length === 0) {
    return (
      <EmptyState
        icon={Inbox}
        title="Nothing to review"
        description="Requirement phrases the catalog cannot place will collect here as you analyse offers."
      />
    )
  }

  return (
    <>
      {approve.isError ? <ApiErrorAlert error={approve.error} /> : null}
      {reject.isError ? <ApiErrorAlert error={reject.error} /> : null}

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Term</TableHead>
              <TableHead className="w-24">Seen</TableHead>
              <TableHead className="w-32">Last seen</TableHead>
              <TableHead className="w-[26rem]">Resolve to</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {unmatched.data.map((term) => (
              <TableRow key={term.id}>
                <TableCell className="font-medium">{term.term}</TableCell>
                <TableCell className="text-muted-foreground">{term.occurrences}×</TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatRelative(term.lastSeenAt)}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <SkillCombobox
                      value={selection[term.id] ?? null}
                      onChange={(skillId) => setSelection((s) => ({ ...s, [term.id]: skillId }))}
                    />
                    <Button
                      size="sm"
                      disabled={selection[term.id] === undefined || approve.isPending}
                      onClick={() =>
                        approve.mutate({ termId: term.id, skillId: selection[term.id] })
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
                      onClick={() => reject.mutate(term.id)}
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
  const skills = useSkillNames()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState<string>('__all__')

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (skills.data ?? [])
      .filter((s) => category === '__all__' || s.category === category)
      .filter((s) => !term || s.name.toLowerCase().includes(term))
  }, [skills.data, search, category])

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
      </div>
      <div className="flex flex-wrap gap-1.5">
        {rows.map((skill) => (
          <Badge key={skill.id} variant="outline" className="gap-1.5">
            {skill.name}
            <span className="text-muted-foreground">{skill.category}</span>
          </Badge>
        ))}
      </div>
    </>
  )
}
