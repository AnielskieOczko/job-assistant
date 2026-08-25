import { json, request } from './http'
import type { ProfileSummary } from './types'

/** Creating, listing, deleting and defaulting profiles themselves - not their contents. */
export const listProfiles = () => request<ProfileSummary[]>('/api/profiles')

export const createProfile = (name: string) =>
  request<ProfileSummary>('/api/profiles', { method: 'POST', ...json({ name }) })

export const setDefaultProfile = (profileId: number) =>
  request<ProfileSummary>(`/api/profiles/${profileId}/default`, { method: 'PUT' })

/** 409 (via ApiError) when this is the default profile and another one still exists. */
export const deleteProfile = (profileId: number) =>
  request<void>(`/api/profiles/${profileId}`, { method: 'DELETE' })
