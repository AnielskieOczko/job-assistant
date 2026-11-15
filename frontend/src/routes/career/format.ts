import type { CareerPlan, SeniorityLevel, SkillProgressState } from '@/api/types'

const SENIORITY_LABELS: Record<SeniorityLevel, string> = {
  INTERN: 'Intern',
  JUNIOR: 'Junior',
  REGULAR: 'Regular',
  SENIOR: 'Senior',
}

const PROGRESS_LABELS: Record<SkillProgressState, string> = {
  NOT_STARTED: 'Not started',
  LEARNING: 'Learning',
  READY_TO_DECLARE: 'Ready to declare',
}

const NEXT_PROGRESS: Record<SkillProgressState, SkillProgressState> = {
  NOT_STARTED: 'LEARNING',
  LEARNING: 'READY_TO_DECLARE',
  READY_TO_DECLARE: 'NOT_STARTED',
}

export const formatSeniority = (level: SeniorityLevel): string => SENIORITY_LABELS[level]

export const progressLabel = (state: SkillProgressState): string => PROGRESS_LABELS[state]

export const nextProgressState = (state: SkillProgressState): SkillProgressState =>
  NEXT_PROGRESS[state]

export const countReadyTopics = (plan: CareerPlan): number =>
  plan.topics.filter((t) => t.progressState === 'READY_TO_DECLARE').length
