import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { keys } from '@/api/keys'
import type { CandidateProfile } from '@/api/types'

/**
 * One shape for every profile edit.
 *
 * The endpoints answer with the whole profile rather than the entity they touched, so the response
 * seeds the cache directly and no refetch is needed. The aggregate gap report is invalidated
 * because it is derived from the profile; stored analyses and documents are not refetched, they
 * simply start rendering as stale once `revision` moves past the one they recorded.
 */
export function useProfileEdit<TArgs>(
  profileId: number,
  mutationFn: (args: TArgs) => Promise<CandidateProfile>,
  success: string,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (profile) => {
      toast.success(success)
      queryClient.setQueryData(keys.profile(profileId), profile)
      queryClient.invalidateQueries({ queryKey: keys.aggregate(profileId) })
    },
  })
}

/**
 * The id list for a collection with one entry moved. Reorder endpoints demand every id exactly
 * once, so this returns the whole list rather than a delta.
 */
export function movedIds<T extends { id: number }>(items: T[], from: number, to: number): number[] {
  const ids = items.map((item) => item.id)
  if (to < 0 || to >= ids.length) return ids
  const [moved] = ids.splice(from, 1)
  ids.splice(to, 0, moved)
  return ids
}

/**
 * The id list for a collection with two entries swapped, by id rather than flat index.
 *
 * Used for reordering within a displayed sub-group (e.g. skills grouped by category): the neighbor
 * in the group is not necessarily the neighbor in the underlying flat list `movedIds` assumes.
 */
export function swappedIds<T extends { id: number }>(items: T[], idA: number, idB: number): number[] {
  const ids = items.map((item) => item.id)
  const i = ids.indexOf(idA)
  const j = ids.indexOf(idB)
  if (i === -1 || j === -1) return ids
  ;[ids[i], ids[j]] = [ids[j], ids[i]]
  return ids
}

/** An empty input means "no value", not an empty string - the column is nullable. */
export const blankToNull = (value: string) => (value.trim() ? value.trim() : null)

/**
 * The delete-confirm dance every collection repeats: track which row is pending deletion, wire
 * `ConfirmDelete`'s open/close and confirm callbacks to it, and reset the mutation's error state
 * on close so a stale 409 does not reappear against the next row.
 */
export function useDeleteConfirm<T extends { id: number }>(
  profileId: number,
  removeFn: (id: number) => Promise<CandidateProfile>,
  successMessage: string,
) {
  const [deleting, setDeleting] = useState<T | null>(null)
  const remove = useProfileEdit(profileId, removeFn, successMessage)
  return {
    deleting,
    requestDelete: setDeleting,
    remove,
    confirmDeleteProps: (title: string, description: string) => ({
      open: deleting !== null,
      onOpenChange: (open: boolean) => {
        if (!open) { setDeleting(null); remove.reset() }
      },
      title,
      description,
      pending: remove.isPending,
      error: remove.error,
      onConfirm: () => {
        if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
      },
    }),
  }
}

/**
 * Reorder-by-flat-index plus delete-confirm for one collection - the shape every card with both
 * repeats. Skills reorders within a displayed subgroup via `swappedIds` instead, and consent
 * clauses never reorder at all; both use `useDeleteConfirm` alone.
 */
export function useReorderableRows<T extends { id: number }>(
  profileId: number,
  items: T[],
  config: {
    reorder: (ids: number[]) => Promise<CandidateProfile>
    remove: (id: number) => Promise<CandidateProfile>
    reorderSuccess: string
    removeSuccess: string
  },
) {
  const { deleting, requestDelete, confirmDeleteProps } = useDeleteConfirm<T>(profileId, config.remove, config.removeSuccess)
  const reorder = useProfileEdit(profileId, config.reorder, config.reorderSuccess)
  return {
    reorder,
    deleting,
    rowActions: (index: number, opts: { label: string; onEdit?: () => void }) => ({
      label: opts.label,
      disabled: reorder.isPending,
      onUp: index > 0 ? () => reorder.mutate(movedIds(items, index, index - 1)) : undefined,
      onDown: index < items.length - 1 ? () => reorder.mutate(movedIds(items, index, index + 1)) : undefined,
      onEdit: opts.onEdit,
      onDelete: () => requestDelete(items[index]),
    }),
    confirmDeleteProps,
  }
}
