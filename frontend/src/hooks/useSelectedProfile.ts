import { useQuery } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import { listProfiles } from '@/api/profiles'
import { keys } from '@/api/keys'

const STORAGE_KEY = 'job-assistant:selectedProfileId'

type Listener = () => void

let current: number | null = readStored()
const listeners = new Set<Listener>()

function readStored(): number | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw ? Number(raw) : null
}

function setStored(id: number) {
  current = id
  localStorage.setItem(STORAGE_KEY, String(id))
  listeners.forEach((listener) => listener())
}

function subscribe(listener: Listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/**
 * The app-wide selected profile - shared by the switcher, `/profile`, the analysis and documents
 * tabs, and `StaleProfileNotice`.
 *
 * There is no React Context in this app for shared state; TanStack Query already owns the profile
 * list, so the selection itself lives in a tiny module-level store backed by `localStorage`,
 * synced through `useSyncExternalStore` rather than threaded down as props through the route tree.
 *
 * Falls back to the server's default profile when nothing is stored yet, or the stored id no
 * longer names a profile that exists (e.g. it was deleted in another tab).
 */
export function useSelectedProfile() {
  const profiles = useQuery({ queryKey: keys.profiles, queryFn: listProfiles })
  const storedId = useSyncExternalStore(subscribe, () => current)

  const list = profiles.data ?? []
  const valid = list.find((profile) => profile.id === storedId)
  const fallback = list.find((profile) => profile.isDefault) ?? list[0]
  const profileId = valid ? storedId! : (fallback?.id ?? null)

  return {
    profileId,
    profiles: list,
    isLoading: profiles.isPending,
    select: setStored,
  }
}
