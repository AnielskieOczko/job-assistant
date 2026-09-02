import { json, request } from './http'
import type {
  BulletRequest,
  CandidateProfile,
  ConsentClauseRequest,
  CredentialRequest,
  DetailsRequest,
  EducationRequest,
  ExperienceRequest,
  LanguageRequest,
  LinkRequest,
  ProfileImport,
  ProjectRequest,
  SkillRequest,
  SkillUpdateRequest,
} from './types'

/** Returns null when the profile has no details yet: the endpoint answers 204, not 404. */
export const getProfile = (profileId: number) => request<CandidateProfile | null>(`/api/profiles/${profileId}`)

/**
 * A full replace, not a merge - the document is the profile. Throws ApiError 400 carrying
 * `unresolvedSkills` and `undeclaredBulletSkills` rather than dropping anything silently.
 *
 * Every entity id is reassigned, so previously generated CVs stop matching the profile they cite.
 * The dialog says so before calling this.
 */
export const importProfile = (profileId: number, document: ProfileImport | unknown) =>
  request<CandidateProfile>(`/api/profiles/${profileId}/import`, { method: 'POST', ...json(document) })

/**
 * Every mutation answers with the whole profile, so callers never reassemble one from a patch
 * response and there is a single query key to invalidate.
 *
 * Failure modes worth branching on: 409 when the edit cannot be reconciled with what is stored
 * (a skill already held, a language already listed, a skill still cited by `blockingBullets`),
 * 404 for an unknown id, 400 with `fieldErrors` for a blank required field.
 */
const put = (path: string, body: unknown) =>
  request<CandidateProfile>(path, { method: 'PUT', ...json(body) })

const post = (path: string, body: unknown) =>
  request<CandidateProfile>(path, { method: 'POST', ...json(body) })

const remove = (path: string) => request<CandidateProfile>(path, { method: 'DELETE' })

/** Fills in an already-created profile's details. The profile itself must exist first - see `createProfile`. */
export const putDetails = (profileId: number, body: DetailsRequest) =>
  put(`/api/profiles/${profileId}/details`, body)

/**
 * The one upload in the application. Sent as multipart rather than base64 in the details body: a
 * photo does not belong in a form the UI submits on every edit. The server sniffs the real media
 * type from the bytes, so a mislabelled file is refused (415) rather than stored wrong.
 */
export const putPortrait = (profileId: number, file: File) => {
  const body = new FormData()
  body.append('file', file)
  return request<CandidateProfile>(`/api/profiles/${profileId}/portrait`, { method: 'PUT', body })
}

export const deletePortrait = (profileId: number) => remove(`/api/profiles/${profileId}/portrait`)

/**
 * The image itself is never in the profile document - only `hasPortrait` is - so it is fetched by
 * URL. The revision is a cache-buster: the endpoint sends no-store, but a replaced photo must not
 * survive in the img element's own memory cache either.
 */
export const portraitUrl = (profileId: number, revision: number) =>
  `/api/profiles/${profileId}/portrait?v=${revision}`

export const addLink = (profileId: number, body: LinkRequest) => post(`/api/profiles/${profileId}/links`, body)
export const updateLink = (profileId: number, id: number, body: LinkRequest) =>
  put(`/api/profiles/${profileId}/links/${id}`, body)
export const deleteLink = (profileId: number, id: number) => remove(`/api/profiles/${profileId}/links/${id}`)
export const reorderLinks = (profileId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/links/order`, { ids })

export const addSkill = (profileId: number, body: SkillRequest) => post(`/api/profiles/${profileId}/skills`, body)
export const updateSkill = (profileId: number, id: number, body: SkillUpdateRequest) =>
  put(`/api/profiles/${profileId}/skills/${id}`, body)
export const deleteSkill = (profileId: number, id: number) => remove(`/api/profiles/${profileId}/skills/${id}`)
export const reorderSkills = (profileId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/skills/order`, { ids })

export const addExperience = (profileId: number, body: ExperienceRequest) =>
  post(`/api/profiles/${profileId}/experiences`, body)
export const updateExperience = (profileId: number, id: number, body: ExperienceRequest) =>
  put(`/api/profiles/${profileId}/experiences/${id}`, body)
export const deleteExperience = (profileId: number, id: number) =>
  remove(`/api/profiles/${profileId}/experiences/${id}`)
export const reorderExperiences = (profileId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/experiences/order`, { ids })

export const addBullet = (profileId: number, experienceId: number, body: BulletRequest) =>
  post(`/api/profiles/${profileId}/experiences/${experienceId}/bullets`, body)
export const updateBullet = (profileId: number, id: number, body: BulletRequest) =>
  put(`/api/profiles/${profileId}/bullets/${id}`, body)
export const deleteBullet = (profileId: number, id: number) => remove(`/api/profiles/${profileId}/bullets/${id}`)
export const reorderBullets = (profileId: number, experienceId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/experiences/${experienceId}/bullets/order`, { ids })

export const addEducation = (profileId: number, body: EducationRequest) =>
  post(`/api/profiles/${profileId}/education`, body)
export const updateEducation = (profileId: number, id: number, body: EducationRequest) =>
  put(`/api/profiles/${profileId}/education/${id}`, body)
export const deleteEducation = (profileId: number, id: number) =>
  remove(`/api/profiles/${profileId}/education/${id}`)
export const reorderEducation = (profileId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/education/order`, { ids })

export const addCredential = (profileId: number, body: CredentialRequest) =>
  post(`/api/profiles/${profileId}/credentials`, body)
export const updateCredential = (profileId: number, id: number, body: CredentialRequest) =>
  put(`/api/profiles/${profileId}/credentials/${id}`, body)
export const deleteCredential = (profileId: number, id: number) =>
  remove(`/api/profiles/${profileId}/credentials/${id}`)
export const reorderCredentials = (profileId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/credentials/order`, { ids })

export const addProject = (profileId: number, body: ProjectRequest) => post(`/api/profiles/${profileId}/projects`, body)
export const updateProject = (profileId: number, id: number, body: ProjectRequest) =>
  put(`/api/profiles/${profileId}/projects/${id}`, body)
export const deleteProject = (profileId: number, id: number) => remove(`/api/profiles/${profileId}/projects/${id}`)
export const reorderProjects = (profileId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/projects/order`, { ids })

export const addProjectBullet = (profileId: number, projectId: number, body: BulletRequest) =>
  post(`/api/profiles/${profileId}/projects/${projectId}/bullets`, body)
export const reorderProjectBullets = (profileId: number, projectId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/projects/${projectId}/bullets/order`, { ids })

export const addConsentClause = (profileId: number, body: ConsentClauseRequest) =>
  post(`/api/profiles/${profileId}/consent-clauses`, body)
export const updateConsentClause = (profileId: number, id: number, body: ConsentClauseRequest) =>
  put(`/api/profiles/${profileId}/consent-clauses/${id}`, body)
export const deleteConsentClause = (profileId: number, id: number) =>
  remove(`/api/profiles/${profileId}/consent-clauses/${id}`)

export const addLanguage = (profileId: number, body: LanguageRequest) =>
  post(`/api/profiles/${profileId}/languages`, body)
export const updateLanguage = (profileId: number, id: number, body: LanguageRequest) =>
  put(`/api/profiles/${profileId}/languages/${id}`, body)
export const deleteLanguage = (profileId: number, id: number) =>
  remove(`/api/profiles/${profileId}/languages/${id}`)
export const reorderLanguages = (profileId: number, ids: number[]) =>
  put(`/api/profiles/${profileId}/languages/order`, { ids })
