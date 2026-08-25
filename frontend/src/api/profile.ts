import { json, request } from './http'
import type {
  BulletRequest,
  CandidateProfile,
  DetailsRequest,
  EducationRequest,
  ExperienceRequest,
  LanguageRequest,
  LinkRequest,
  ProfileImport,
  SkillRequest,
  SkillUpdateRequest,
} from './types'

/** Returns null when there is no profile yet: the endpoint answers 204, not 404. */
export const getProfile = () => request<CandidateProfile | null>('/api/profile')

/**
 * A full replace, not a merge - the document is the profile. Throws ApiError 400 carrying
 * `unresolvedSkills` and `undeclaredBulletSkills` rather than dropping anything silently.
 *
 * Every entity id is reassigned, so previously generated CVs stop matching the profile they cite.
 * The dialog says so before calling this.
 */
export const importProfile = (document: ProfileImport | unknown) =>
  request<CandidateProfile>('/api/profile/import', { method: 'POST', ...json(document) })

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

/** Also how a profile comes into existence - no import document required. */
export const putDetails = (body: DetailsRequest) => put('/api/profile/details', body)

export const addLink = (body: LinkRequest) => post('/api/profile/links', body)
export const updateLink = (id: number, body: LinkRequest) => put(`/api/profile/links/${id}`, body)
export const deleteLink = (id: number) => remove(`/api/profile/links/${id}`)
export const reorderLinks = (ids: number[]) => put('/api/profile/links/order', { ids })

export const addSkill = (body: SkillRequest) => post('/api/profile/skills', body)
export const updateSkill = (id: number, body: SkillUpdateRequest) => put(`/api/profile/skills/${id}`, body)
export const deleteSkill = (id: number) => remove(`/api/profile/skills/${id}`)
export const reorderSkills = (ids: number[]) => put('/api/profile/skills/order', { ids })

export const addExperience = (body: ExperienceRequest) => post('/api/profile/experiences', body)
export const updateExperience = (id: number, body: ExperienceRequest) =>
  put(`/api/profile/experiences/${id}`, body)
export const deleteExperience = (id: number) => remove(`/api/profile/experiences/${id}`)
export const reorderExperiences = (ids: number[]) => put('/api/profile/experiences/order', { ids })

export const addBullet = (experienceId: number, body: BulletRequest) =>
  post(`/api/profile/experiences/${experienceId}/bullets`, body)
export const updateBullet = (id: number, body: BulletRequest) => put(`/api/profile/bullets/${id}`, body)
export const deleteBullet = (id: number) => remove(`/api/profile/bullets/${id}`)
export const reorderBullets = (experienceId: number, ids: number[]) =>
  put(`/api/profile/experiences/${experienceId}/bullets/order`, { ids })

export const addEducation = (body: EducationRequest) => post('/api/profile/education', body)
export const updateEducation = (id: number, body: EducationRequest) =>
  put(`/api/profile/education/${id}`, body)
export const deleteEducation = (id: number) => remove(`/api/profile/education/${id}`)
export const reorderEducation = (ids: number[]) => put('/api/profile/education/order', { ids })

export const addLanguage = (body: LanguageRequest) => post('/api/profile/languages', body)
export const updateLanguage = (id: number, body: LanguageRequest) =>
  put(`/api/profile/languages/${id}`, body)
export const deleteLanguage = (id: number) => remove(`/api/profile/languages/${id}`)
export const reorderLanguages = (ids: number[]) => put('/api/profile/languages/order', { ids })
