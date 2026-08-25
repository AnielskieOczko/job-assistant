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
  mutationFn: (args: TArgs) => Promise<CandidateProfile>,
  success: string,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (profile) => {
      toast.success(success)
      queryClient.setQueryData(keys.profile, profile)
      queryClient.invalidateQueries({ queryKey: keys.aggregate })
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

/** An empty input means "no value", not an empty string - the column is nullable. */
export const blankToNull = (value: string) => (value.trim() ? value.trim() : null)
