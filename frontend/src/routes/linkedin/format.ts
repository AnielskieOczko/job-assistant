import type { LinkedInSection } from '@/api/types'

const SECTION_LABELS: Record<LinkedInSection, string> = {
  HEADLINE: 'Headline',
  ABOUT: 'About summary',
  EXPERIENCE_ENTRY: 'Experience entry',
  PROJECT_ENTRY: 'Project entry',
  SKILL_OR_CREDENTIAL_POST: 'Skill or credential post',
}

const CHARACTER_LIMITS: Record<LinkedInSection, number> = {
  HEADLINE: 220,
  ABOUT: 2600,
  EXPERIENCE_ENTRY: 2000,
  PROJECT_ENTRY: 2000,
  SKILL_OR_CREDENTIAL_POST: 3000,
}

export const LINKEDIN_SECTIONS: LinkedInSection[] = [
  'HEADLINE',
  'ABOUT',
  'EXPERIENCE_ENTRY',
  'PROJECT_ENTRY',
  'SKILL_OR_CREDENTIAL_POST',
]

export const sectionLabel = (section: LinkedInSection): string => SECTION_LABELS[section]

export const sectionNeedsSubject = (section: LinkedInSection): boolean =>
  section === 'EXPERIENCE_ENTRY' || section === 'PROJECT_ENTRY'

export const characterBudgetHint = (section: LinkedInSection): number => CHARACTER_LIMITS[section]

export const formatCharCount = (text: string, section: LinkedInSection): string =>
  `${text.length} / ${characterBudgetHint(section)} chars`
