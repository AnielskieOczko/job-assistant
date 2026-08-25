import { useState } from 'react'
import { Check, ChevronsUpDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useSkillNames } from '@/hooks/useSkillNames'
import { cn } from '@/lib/utils'

export function SkillCombobox({
  value,
  onChange,
  placeholder = 'Pick a skill…',
}: {
  value: number | null
  onChange: (skillId: number) => void
  placeholder?: string
}) {
  const [open, setOpen] = useState(false)
  const skills = useSkillNames()
  const all = skills.data ?? []

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" role="combobox" className="w-56 justify-between font-normal">
          <span className={cn('truncate', value === null && 'text-muted-foreground')}>
            {value === null ? placeholder : skills.nameOf(value)}
          </span>
          <ChevronsUpDown className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-64 p-0" align="start">
        <Command>
          <CommandInput placeholder="Search the catalog…" />
          <CommandList>
            <CommandEmpty>No skill found.</CommandEmpty>
            <CommandGroup>
              {all.map((skill) => (
                <CommandItem
                  key={skill.id}
                  value={skill.name}
                  onSelect={() => {
                    onChange(skill.id)
                    setOpen(false)
                  }}
                >
                  <Check className={cn(value === skill.id ? 'opacity-100' : 'opacity-0')} />
                  <span className="truncate">{skill.name}</span>
                  <span className="ml-auto text-xs text-muted-foreground">{skill.category}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
