import { useQuery } from '@tanstack/react-query'
import { getPrivacyManifest } from '@/api/privacy'
import { keys } from '@/api/keys'
import type { PrivacyField } from '@/api/types'

/**
 * The manifest, indexed by field name. Static and profile-independent - the mechanism a field is
 * subject to does not change with the persona - so it is cached indefinitely, the same way the
 * skill catalog is.
 */
export function usePrivacyManifest() {
  const query = useQuery({
    queryKey: keys.privacyManifest,
    queryFn: getPrivacyManifest,
    staleTime: Infinity,
  })

  const byName = new Map<string, PrivacyField>()
  for (const field of query.data?.fields ?? []) byName.set(field.name, field)

  return { ...query, byName }
}
