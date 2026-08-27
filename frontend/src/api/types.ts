/**
 * Hand-written mirrors of the Kotlin wire DTOs.
 *
 * Kept by hand rather than generated: the two most important shapes in the API - the
 * ProblemDetail extensions (`fabricatedClaims`, `unresolvedSkills`, `undeclaredBulletSkills`) and
 * the `{analysisId, state}` body - are built at runtime and are invisible to schema generation,
 * so they would be hand-written either way. `ApiContractTest` on the Kotlin side fails the build
 * if a field is renamed without this file being updated in the same commit.
 *
 * Conventions:
 *  - Kotlin `Instant` / `LocalDate` -> `string` (ISO-8601 / `YYYY-MM-DD`)
 *  - Kotlin `Long` / `Int` / `BigDecimal` / `Double` -> `number`
 *  - Kotlin `T?` -> `T | null`, never `T | undefined`. Jackson emits explicit nulls.
 *  - Computed getters are marked `?` and treated as convenience only: their serialization is an
 *    implementation detail, so nothing in the UI may depend on them being present.
 *  - `erasableSyntaxOnly` is on under TypeScript 6, so enums are const-object + union types.
 */

/* ------------------------------------------------------------------ catalog */

export const SKILL_CATEGORIES = [
  'LANGUAGE', 'FRAMEWORK', 'DATABASE', 'MESSAGING', 'CLOUD', 'DEVOPS',
  'TESTING', 'FRONTEND', 'AI', 'PRACTICE', 'TOOL', 'SOFT', 'OTHER',
] as const
export type SkillCategory = (typeof SKILL_CATEGORIES)[number]

export const SKILL_CATEGORY_LABELS: Record<SkillCategory, string> = {
  LANGUAGE: 'Languages', FRAMEWORK: 'Frameworks', DATABASE: 'Databases',
  MESSAGING: 'Messaging', CLOUD: 'Cloud', DEVOPS: 'DevOps', TESTING: 'Testing',
  FRONTEND: 'Frontend', AI: 'AI', PRACTICE: 'Practices', TOOL: 'Tools',
  SOFT: 'Soft skills', OTHER: 'Other',
}

export interface CanonicalSkill {
  id: number
  name: string
  category: SkillCategory
}

export const UNMATCHED_TERM_STATUSES = ['PENDING', 'APPROVED', 'REJECTED'] as const
export type UnmatchedTermStatus = (typeof UNMATCHED_TERM_STATUSES)[number]

export interface UnmatchedTerm {
  id: number
  term: string
  occurrences: number
  firstSeenAt: string
  lastSeenAt: string
  status: UnmatchedTermStatus
  resolvedSkillId: number | null
}

export interface CreateSkillRequest {
  name: string
  category: SkillCategory
  aliases: string[]
}

export interface UpdateSkillRequest {
  name: string
  category: SkillCategory
}

/* -------------------------------------------------------------------- offer */

export const APPLICATION_STATUSES = [
  'SAVED', 'ANALYZED', 'APPLIED', 'INTERVIEWING', 'REJECTED', 'OFFER',
] as const
export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number]

export interface JobOffer {
  id: number
  contentHash: string
  rawText: string
  sourceUrl: string | null
  title: string | null
  company: string | null
  seniority: string | null
  detectedLanguage: string | null
  createdAt: string
  /** Computed getter. Falls back to `title` or the first non-blank line. */
  displayTitle?: string
}

export interface Application {
  id: number
  offerId: number
  status: ApplicationStatus
  statusChangedAt: string
  appliedOn: string | null
  notes: string | null
}

export interface OfferSummary {
  offer: JobOffer
  application: Application
}

export interface PastedOffer {
  offer: JobOffer
  /** True when the text matched an offer already stored; nothing new was created. */
  deduplicated: boolean
}

export interface PasteOfferRequest {
  text: string
  sourceUrl?: string | null
}

export interface UpdateStatusRequest {
  status: ApplicationStatus
  appliedOn?: string | null
  notes?: string | null
}

/* ----------------------------------------------------------------- analysis */

export const ANALYSIS_STATES = [
  'PENDING', 'EXTRACTING', 'MATCHING', 'NARRATING', 'DONE', 'FAILED',
] as const
export type AnalysisState = (typeof ANALYSIS_STATES)[number]

/** Mirrors `AnalysisState.isTerminal`, which serializes as a bare string so cannot be read. */
export const TERMINAL_ANALYSIS_STATES: ReadonlySet<AnalysisState> = new Set(['DONE', 'FAILED'])

export const isTerminal = (state: AnalysisState): boolean => TERMINAL_ANALYSIS_STATES.has(state)

export const IMPORTANCES = ['MUST_HAVE', 'NICE_TO_HAVE'] as const
export type Importance = (typeof IMPORTANCES)[number]

export const REQUIREMENT_STATUSES = ['MET', 'PARTIAL', 'MISSING', 'UNRESOLVED'] as const
export type RequirementStatus = (typeof REQUIREMENT_STATUSES)[number]

export interface RequirementFinding {
  id: number
  rawText: string
  skillId: number | null
  skillName: string | null
  importance: Importance
  status: RequirementStatus
  /** Which profile record backs a MET/PARTIAL verdict. Null when nothing does. */
  evidence: string | null
  rationale: string | null
}

export interface LanguageFinding {
  language: string
  requiredLevel: LanguageLevel
  heldLevel: LanguageLevel | null
  status: RequirementStatus
}

export interface LearningPlanItem {
  skillId: number | null
  skillName: string
  why: string
  practiceProject: string | null
  effortEstimate: string | null
  priority: number
}

export interface AnalysisReport {
  id: number
  offerId: number
  /** Which profile this analysis was run against. */
  profileId: number
  state: AnalysisState
  error: string | null
  /** 0.0-1.0 over must-haves only. Null means nothing was scoreable - render that, not 0%. */
  matchScore: number | null
  summaryMarkdown: string | null
  requirements: RequirementFinding[]
  languageRequirements: LanguageFinding[]
  learningPlan: LearningPlanItem[]
  createdAt: string
  completedAt: string | null
  /**
   * Profile revision this ran against. Null for analyses that predate the counter. When it trails
   * `CandidateProfile.revision` the findings have been overtaken by a profile edit.
   */
  profileRevision: number | null
  /** Computed getters. The UI derives the must-have split itself; see `mustHaves()` below. */
  mustHaves?: RequirementFinding[]
  niceToHaves?: RequirementFinding[]
  missingMustHaves?: RequirementFinding[]
  /** How the score was arrived at, so the number is never a black box. Print it verbatim. */
  scoreExplanation?: string
}

/** Response of `POST /api/offers/{id}/analyses`. Built as a raw map server-side. */
export interface StartedAnalysis {
  analysisId: number
  state: AnalysisState
}

export interface AggregateGapEntry {
  skillId: number
  skillName: string
  demandCount: number
  gapCount: number
  mustHaveGapCount: number
  /** Computed getter: gapCount / demandCount. */
  gapRatio?: number
}

export interface AggregateGapReport {
  analysedOffers: number
  entries: AggregateGapEntry[]
}

/* ------------------------------------------------------------------ profile */

/** A profile's identity, without its contents - what the switcher and /api/profiles list show. */
export interface ProfileSummary {
  id: number
  name: string
  isDefault: boolean
}

export interface CreateProfileRequest {
  name: string
}

export const PROFICIENCIES = ['BEGINNER', 'WORKING', 'PROFICIENT', 'EXPERT'] as const
export type Proficiency = (typeof PROFICIENCIES)[number]

export const LANGUAGE_LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'NATIVE'] as const
export type LanguageLevel = (typeof LANGUAGE_LEVELS)[number]

export interface ProfileDetails {
  fullName: string
  headline: string | null
  email: string | null
  phone: string | null
  location: string | null
  summary: string | null
}

export interface ProfileLink {
  id: number
  label: string
  url: string
}

export interface ProfileSkill {
  id: number
  /** Id only - join against `/api/catalog/skills` for a name. See `useSkillNames`. */
  skillId: number
  proficiency: Proficiency
  yearsOfExperience: number | null
  lastUsedYear: number | null
}

export interface ExperienceBullet {
  id: number
  text: string
  skillIds: number[]
}

export interface WorkExperience {
  id: number
  company: string
  roleTitle: string
  location: string | null
  startedOn: string
  /** Null means the role is current. */
  endedOn: string | null
  summary: string | null
  bullets: ExperienceBullet[]
  /**
   * Computed getter. Verified on the wire as `isCurrent` - Jackson 3 with the Kotlin module keeps
   * the `is` prefix rather than stripping it. Prefer `isCurrentRole()`, which reads `endedOn`.
   */
  isCurrent?: boolean
}

export interface Education {
  id: number
  institution: string
  degree: string
  fieldOfStudy: string | null
  startedOn: string | null
  endedOn: string | null
}

export interface LanguageSkill {
  id: number
  language: string
  level: LanguageLevel
}

export interface CandidateProfile {
  details: ProfileDetails
  links: ProfileLink[]
  skills: ProfileSkill[]
  experiences: WorkExperience[]
  education: Education[]
  languages: LanguageSkill[]
  /**
   * Bumped by every write to the profile. Compare against an analysis's or a document's
   * `profileRevision` to tell output that still reflects the profile from output an edit has
   * overtaken.
   */
  revision: number
  /** Computed. Serialized as an array despite being a Kotlin Set. */
  heldSkillIds?: number[]
  /**
   * Computed: every bullet flattened. Confirmed present on the wire and duplicating
   * `experiences[].bullets` - the UI reads the nested copy and ignores this one.
   */
  bullets?: ExperienceBullet[]
}

/* --- import document (names, not ids; array order becomes CV display order) --- */

export interface LinkImport { label: string; url: string }

export interface SkillImport {
  skill: string
  proficiency: Proficiency
  yearsOfExperience?: number | null
  lastUsedYear?: number | null
}

export interface BulletImport { text: string; skills: string[] }

export interface ExperienceImport {
  company: string
  roleTitle: string
  location?: string | null
  startedOn: string
  endedOn?: string | null
  summary?: string | null
  bullets: BulletImport[]
}

export interface EducationImport {
  institution: string
  degree: string
  fieldOfStudy?: string | null
  startedOn?: string | null
  endedOn?: string | null
}

export interface LanguageImport { language: string; level: LanguageLevel }

export interface ProfileImport {
  details: ProfileDetails
  links: LinkImport[]
  skills: SkillImport[]
  experiences: ExperienceImport[]
  education: EducationImport[]
  languages: LanguageImport[]
}

/* --- per-entity editing (ids, not names; every update is a full-entity PUT) --- */

/**
 * Unlike the import document these carry catalog ids: the picker resolved the name already, so
 * re-resolving it server-side would only add a way to fail. And every update sends the whole
 * entity, because `endedOn: null` is what makes a role current - a patch could not tell that from
 * a field the client simply left out.
 */
export interface DetailsRequest {
  fullName: string
  headline?: string | null
  email?: string | null
  phone?: string | null
  location?: string | null
  summary?: string | null
}

export interface LinkRequest { label: string; url: string }

export interface SkillRequest {
  skillId: number
  proficiency: Proficiency
  yearsOfExperience?: number | null
  lastUsedYear?: number | null
}

/** No `skillId`: swapping which skill a row is would strand every bullet citing the old one. */
export interface SkillUpdateRequest {
  proficiency: Proficiency
  yearsOfExperience?: number | null
  lastUsedYear?: number | null
}

export interface ExperienceRequest {
  company: string
  roleTitle: string
  location?: string | null
  startedOn: string
  endedOn?: string | null
  summary?: string | null
}

export interface BulletRequest { text: string; skillIds: number[] }

export interface EducationRequest {
  institution: string
  degree: string
  fieldOfStudy?: string | null
  startedOn?: string | null
  endedOn?: string | null
}

export interface LanguageRequest { language: string; level: LanguageLevel }

/** Must name every id in the collection exactly once; a partial list is rejected with 409. */
export interface ReorderRequest { ids: number[] }

/** A bullet standing in the way of deleting a skill. Carried on the 409 as `blockingBullets`. */
export interface BlockingBullet { id: number; text: string }

/* ----------------------------------------------------------------- document */

export const DOCUMENT_TYPES = ['CV', 'COVER_LETTER'] as const
export type DocumentType = (typeof DOCUMENT_TYPES)[number]

export interface GeneratedDocument {
  id: number
  offerId: number
  /** Which profile this document was tailored to. */
  profileId: number
  analysisId: number | null
  type: DocumentType
  language: string
  /** The full document markup. Large - prefer the `/html` endpoint for display. */
  html: string
  createdAt: string
  /**
   * Profile revision this was built from. Null for documents that predate the counter. The stored
   * HTML was true when written, so a trailing revision means out of date, not wrong.
   */
  profileRevision: number | null
  /**
   * How many of the model's tailoring choices had nothing behind them and were discarded.
   *
   * Not a defect in this document - the selection dropped them, so everything rendered is backed
   * by a profile record. It is the fabrication rate measured on real offers, and a number that
   * climbs after a prompt or model change is the first sign tailoring has started guessing.
   * Always 0 for a cover letter, which selects nothing by id.
   */
  droppedBulletCount: number
  droppedSkillCount: number
}

/* ---------------------------------------------------------------------- llm */

export const LLM_TASKS = ['EXTRACTION', 'NARRATIVE', 'DOCUMENT'] as const
export type LlmTaskName = (typeof LLM_TASKS)[number]

export interface LlmCall {
  id: number
  /** Serialized as a plain String on the Kotlin side, so not narrowed to LlmTaskName. */
  task: string
  modelProfile: string
  modelName: string | null
  /** Upstream provider behind a router (OpenRouter reports one); null for direct providers. */
  servingProvider: string | null
  inputTokens: number | null
  outputTokens: number | null
  latencyMs: number | null
  error: string | null
  createdAt: string
}

export interface LlmCallDetail {
  call: LlmCall
  requestJson: string
  responseText: string | null
}

/* ------------------------------------------------------------------ helpers */

export const mustHaves = (r: AnalysisReport): RequirementFinding[] =>
  r.requirements.filter((x) => x.importance === 'MUST_HAVE')

export const niceToHaves = (r: AnalysisReport): RequirementFinding[] =>
  r.requirements.filter((x) => x.importance === 'NICE_TO_HAVE')

export const isCurrentRole = (e: WorkExperience): boolean => e.endedOn === null
