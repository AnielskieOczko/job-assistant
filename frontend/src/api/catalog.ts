import { json, query, request, requestOrNull } from './http'
import type { CanonicalSkill, CreateSkillRequest, UnmatchedTerm } from './types'

export const listSkills = () => request<CanonicalSkill[]>('/api/catalog/skills')

export const resolveSkill = (term: string) =>
  requestOrNull<CanonicalSkill>(`/api/catalog/skills/resolve${query({ term })}`)

export const listUnmatched = (limit = 100) =>
  request<UnmatchedTerm[]>(`/api/catalog/unmatched${query({ limit })}`)

/** Human-invoked only. The extractor can queue an unmatched term but never create a skill. */
export const createSkill = (body: CreateSkillRequest) =>
  request<CanonicalSkill>('/api/catalog/skills', { method: 'POST', ...json(body) })

/** Approving adds the term as an alias of the skill, so it resolves from then on. */
export const approveUnmatched = (termId: number, skillId: number) =>
  request<CanonicalSkill>(
    `/api/catalog/unmatched/${termId}/approve${query({ skillId })}`,
    { method: 'POST' },
  )

/** Returns an empty 200 - there is no body to parse. */
export const rejectUnmatched = (termId: number) =>
  request<void>(`/api/catalog/unmatched/${termId}/reject`, { method: 'POST' })
