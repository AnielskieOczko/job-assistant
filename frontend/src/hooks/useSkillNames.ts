import { useQuery } from '@tanstack/react-query'
import { listSkills } from '@/api/catalog'
import { keys } from '@/api/keys'
import type { CanonicalSkill } from '@/api/types'

/**
 * The catalog, indexed by id.
 *
 * The profile references skills by `skillId` only - names never cross a module boundary on the
 * Kotlin side - so every skill chip in the UI has to join against this. The catalog only changes
 * when a human edits it, so it is cached indefinitely and invalidated explicitly.
 */
export function useSkillNames() {
  const query = useQuery({
    queryKey: keys.skills,
    queryFn: listSkills,
    staleTime: Infinity,
  })

  const byId = new Map<number, CanonicalSkill>()
  for (const skill of query.data ?? []) byId.set(skill.id, skill)

  return {
    ...query,
    byId,
    /** Falls back to a visible placeholder rather than an empty chip while the catalog loads. */
    nameOf: (id: number) => byId.get(id)?.name ?? `#${id}`,
  }
}
