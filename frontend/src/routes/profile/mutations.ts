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
