import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, ChevronsUpDown, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { createSkill } from '@/api/catalog'
import { keys } from '@/api/keys'
import { SKILL_CATEGORIES, SKILL_CATEGORY_LABELS } from '@/api/types'
import type { SkillCategory } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Button } from '@/components/ui/button'
import {
  Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { useSkillNames } from '@/hooks/useSkillNames'
import { cn } from '@/lib/utils'

export function SkillCombobox({
  value,
  onChange,
  placeholder = 'Pick a skill…',
  className,
  allowCreate = false,
}: {
  value: number | null
  onChange: (skillId: number) => void
  placeholder?: string
  className?: string
  /** Offer to add an unknown term to the catalog rather than dead-ending on "No skill found". */
  allowCreate?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const skills = useSkillNames()
  const all = skills.data ?? []
  const grouped = SKILL_CATEGORIES.map((category) => ({
    category,
    items: all.filter((skill) => skill.category === category),
  })).filter((group) => group.items.length > 0)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          className={cn('w-56 justify-between font-normal', className)}
        >
          <span className={cn('truncate', value === null && 'text-muted-foreground')}>
            {value === null ? placeholder : skills.nameOf(value)}
          </span>
          <ChevronsUpDown className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-72 p-0" align="start">
        <Command>
          <CommandInput placeholder="Search the catalog…" value={search} onValueChange={setSearch} />
          <CommandList>
            <CommandEmpty>
              {allowCreate && search.trim() ? (
                <CreateSkillInline
                  term={search.trim()}
                  onCreated={(skillId) => {
                    onChange(skillId)
                    setSearch('')
                    setOpen(false)
                  }}
                />
              ) : (
                'No skill found.'
              )}
            </CommandEmpty>
            {grouped.map((group) => (
              <CommandGroup key={group.category} heading={SKILL_CATEGORY_LABELS[group.category]}>
                {group.items.map((skill) => (
                  <CommandItem
                    key={skill.id}
                    value={skill.name}
                    onSelect={() => {
                      onChange(skill.id)
                      setSearch('')
                      setOpen(false)
                    }}
                  >
                    <Check className={cn(value === skill.id ? 'opacity-100' : 'opacity-0')} />
                    <span className="truncate">{skill.name}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            ))}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

/**
 * Adding to the catalog from inside the picker.
 *
 * The profile refuses to invent a canonical skill on your behalf — that is what the review queue is
 * for — but a dead end here would mean leaving the page to add "Iceberg" before you could record
 * having used it. So the write still goes through the catalog's own endpoint, deliberately, on a
 * click; nothing is created as a side effect of saving a profile.
 */
function CreateSkillInline({
  term,
  onCreated,
}: {
  term: string
  onCreated: (skillId: number) => void
}) {
  const [category, setCategory] = useState<SkillCategory>('OTHER')
  const queryClient = useQueryClient()

  const create = useMutation({
    mutationFn: () => createSkill({ name: term, category, aliases: [] }),
    onSuccess: (skill) => {
      toast.success(`${skill.name} added to the catalog`)
      queryClient.invalidateQueries({ queryKey: keys.skills })
      queryClient.invalidateQueries({ queryKey: keys.unmatched })
      onCreated(skill.id)
    },
  })

  return (
    <div className="space-y-2 p-1 text-left">
      <p className="text-sm">
        <span className="font-medium text-foreground">{term}</span> is not in the catalog.
      </p>
      <Select value={category} onValueChange={(next) => setCategory(next as SkillCategory)}>
        <SelectTrigger className="w-full" size="sm">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {SKILL_CATEGORIES.map((option) => (
            <SelectItem key={option} value={option}>{option}</SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Button
        size="sm"
        className="w-full"
        disabled={create.isPending}
        onClick={() => create.mutate()}
      >
        <Plus /> {create.isPending ? 'Adding…' : 'Add to catalog'}
      </Button>
      {create.isError ? <ApiErrorAlert error={create.error} /> : null}
    </div>
  )
}
