import { json, query, request, requestOrNull } from './http'
import type { CanonicalSkill, CreateSkillRequest, UnmatchedTerm, UpdateSkillRequest } from './types'

export const listSkills = () => request<CanonicalSkill[]>('/api/catalog/skills')

export const resolveSkill = (term: string) =>
  requestOrNull<CanonicalSkill>(`/api/catalog/skills/resolve${query({ term })}`)

export const listUnmatched = (limit = 100) =>
  request<UnmatchedTerm[]>(`/api/catalog/unmatched${query({ limit })}`)

/** Human-invoked only. The extractor can queue an unmatched term but never create a skill. */
export const createSkill = (body: CreateSkillRequest) =>
  request<CanonicalSkill>('/api/catalog/skills', { method: 'POST', ...json(body) })

/** Renaming keeps the old name resolvable too - it registers the new one as an alias. */
export const updateSkill = (id: number, body: UpdateSkillRequest) =>
  request<CanonicalSkill>(`/api/catalog/skills/${id}`, { method: 'PUT', ...json(body) })

/** 409 when a profile still holds the skill or a bullet still cites it. */
export const deleteSkill = (id: number) =>
  request<void>(`/api/catalog/skills/${id}`, { method: 'DELETE' })

/** Approving adds the term as an alias of the skill, so it resolves from then on. */
export const approveUnmatched = (termId: number, skillId: number) =>
  request<CanonicalSkill>(
    `/api/catalog/unmatched/${termId}/approve${query({ skillId })}`,
    { method: 'POST' },
  )

/** Returns an empty 200 - there is no body to parse. */
export const rejectUnmatched = (termId: number) =>
  request<void>(`/api/catalog/unmatched/${termId}/reject`, { method: 'POST' })
