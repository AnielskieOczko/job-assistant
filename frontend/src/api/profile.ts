import { json, request } from './http'
import type { CandidateProfile, ProfileImport } from './types'

/** Returns null when nothing has been imported: the endpoint answers 204, not 404. */
export const getProfile = () => request<CandidateProfile | null>('/api/profile')

/**
 * A full replace, not a merge - the document is the profile. Throws ApiError 400 carrying
 * `unresolvedSkills` and `undeclaredBulletSkills` rather than dropping anything silently.
 */
export const importProfile = (document: ProfileImport | unknown) =>
  request<CandidateProfile>('/api/profile/import', { method: 'POST', ...json(document) })
