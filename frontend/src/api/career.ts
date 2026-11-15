import { json, query, request } from './http'
import type {
  CareerPlan,
  CareerPlanSummary,
  SkillProgress,
  UpdateSkillProgressRequest,
} from './types'

export const fetchCurrentCareerPlan = (profileId?: number) =>
  request<CareerPlan | null>(`/api/career/plan${query({ profileId })}`)

export const fetchCareerPlanHistory = (profileId?: number) =>
  request<CareerPlanSummary[]>(`/api/career/plan/history${query({ profileId })}`)

export const fetchCareerPlanById = (planId: number) =>
  request<CareerPlan>(`/api/career/plan/${planId}`)

export const generateCareerPlan = (profileId?: number) =>
  request<CareerPlan>(`/api/career/plan${query({ profileId })}`, { method: 'POST' })

export const updateSkillProgress = (
  skillId: number,
  body: UpdateSkillProgressRequest,
  profileId?: number,
) =>
  request<SkillProgress>(`/api/career/progress/${skillId}${query({ profileId })}`, {
    method: 'PUT',
    ...json(body),
  })
