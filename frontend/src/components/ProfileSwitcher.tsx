import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ChevronsUpDown, Plus, Star, Trash2 } from 'lucide-react'
import { createProfile, deleteProfile, setDefaultProfile } from '@/api/profiles'
import { keys } from '@/api/keys'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { ConfirmDelete } from '@/routes/profile/ConfirmDelete'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'

/**
 * The one place a persona is picked. Persisted in `localStorage` via `useSelectedProfile`, and
 * every profile-scoped screen reads that same selection - switching here is what makes /profile,
 * the analysis tab and the documents tab all start showing a different persona's data.
 */
export function ProfileSwitcher() {
  const { profileId, profiles, isLoading, select } = useSelectedProfile()
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<{ id: number; name: string } | null>(null)
  const queryClient = useQueryClient()

  const setDefault = useMutation({
    mutationFn: setDefaultProfile,
    onSuccess: () => {
      toast.success('Default profile updated')
      queryClient.invalidateQueries({ queryKey: keys.profiles })
    },
  })

  const remove = useMutation({
    mutationFn: deleteProfile,
    onSuccess: (_, id) => {
      toast.success('Profile deleted')
      queryClient.invalidateQueries({ queryKey: keys.profiles })
      setDeleteTarget(null)
      if (profileId === id) {
        const remaining = profiles.filter((profile) => profile.id !== id)
        if (remaining[0]) select(remaining[0].id)
      }
    },
  })

  if (isLoading) return <div className="h-9 w-full animate-pulse rounded-md bg-muted" />

  const current = profiles.find((profile) => profile.id === profileId)

  if (profiles.length === 0) {
    return (
      <>
        <Button variant="outline" size="sm" className="w-full justify-start gap-2" onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" />
          Create your first profile
        </Button>
        <CreateProfileDialog open={createOpen} onOpenChange={setCreateOpen} onCreated={select} />
      </>
    )
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm" className="w-full justify-between gap-2 font-normal">
            <span className="truncate">{current?.name ?? 'Select a profile'}</span>
            <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-64">
          {profiles.map((profile) => (
            <DropdownMenuItem
              key={profile.id}
              className="flex items-center justify-between gap-2"
              onSelect={() => select(profile.id)}
            >
              <span className={profile.id === profileId ? 'font-medium' : undefined}>{profile.name}</span>
              <span className="flex items-center gap-1">
                {profile.isDefault ? (
                  <Badge variant="secondary" className="text-[10px]">Default</Badge>
                ) : (
                  <button
                    type="button"
                    title="Set as default"
                    className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
                    onClick={(event) => {
                      event.stopPropagation()
                      setDefault.mutate(profile.id)
                    }}
                  >
                    <Star className="size-3.5" />
                  </button>
                )}
                <button
                  type="button"
                  title="Delete profile"
                  className="rounded p-1 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                  onClick={(event) => {
                    event.stopPropagation()
                    setDeleteTarget({ id: profile.id, name: profile.name })
                  }}
                >
                  <Trash2 className="size-3.5" />
                </button>
              </span>
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => setCreateOpen(true)}>
            <Plus className="size-4" />
            New profile…
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <CreateProfileDialog open={createOpen} onOpenChange={setCreateOpen} onCreated={select} />

      <ConfirmDelete
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title={`Delete ${deleteTarget?.name ?? 'this profile'}?`}
        description="Its skills, experience, education and languages are removed too. Analyses and documents already generated from it are kept for reference."
        onConfirm={() => deleteTarget && remove.mutate(deleteTarget.id)}
        pending={remove.isPending}
        error={remove.error}
      />
    </>
  )
}

function CreateProfileDialog({
  open,
  onOpenChange,
  onCreated,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreated: (id: number) => void
}) {
  const [name, setName] = useState('')
  const queryClient = useQueryClient()
  const create = useMutation({
    mutationFn: createProfile,
    onSuccess: (profile) => {
      toast.success(`${profile.name} created`)
      queryClient.invalidateQueries({ queryKey: keys.profiles })
      onCreated(profile.id)
      setName('')
      onOpenChange(false)
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New profile</DialogTitle>
          <DialogDescription>
            A persona to tailor CVs from — e.g. "Java developer" or "Cloud consultant". Skills and
            experience are added afterwards.
          </DialogDescription>
        </DialogHeader>
        <Input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Java developer"
          autoFocus
        />
        {create.isError ? <ApiErrorAlert error={create.error} /> : null}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={!name.trim() || create.isPending} onClick={() => create.mutate(name.trim())}>
            {create.isPending ? 'Creating…' : 'Create'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
