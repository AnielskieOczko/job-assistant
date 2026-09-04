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
  /** Times seen in offers you analysed. This is what the review queue is ranked by. */
  occurrences: number
  /** Times seen in the ingested market corpus, counted separately so volume cannot outrank (1). */
  marketOccurrences: number
  firstSeenAt: string
  lastSeenAt: string
  status: UnmatchedTermStatus
  resolvedSkillId: number | null
}

/** Which signal breaks the tie once your own count has had its say. */
export const TRIAGE_RANKINGS = ['SCOPE', 'CORPUS'] as const
export type TriageRanking = (typeof TRIAGE_RANKINGS)[number]

/**
 * A catalog entry a queued term might mean.
 *
 * A candidate for you, never an answer: nothing resolves until you pick one and click Approve.
 */
export interface SkillSuggestion {
  skillId: number
  skillName: string
  category: SkillCategory
  /** The catalog spelling that matched — often an alias, which is the explanation for the chip. */
  matchedAlias: string
  /** 0-1, ordering only. Not a probability, and not confidence. */
  score: number
}

export interface TriageEntry {
  termId: number
  term: string
  /** Times seen in offers you analysed. Outranks both market numbers under either ranking. */
  occurrences: number
  /** Times seen anywhere in the ingested corpus, QA and BA roles included. */
  marketOccurrences: number
  /** Times seen on a corpus offer that also asks for a scope skill. */
  inScopeDemand: number
  firstSeenAt: string
  lastSeenAt: string
  /** By string similarity, best first. Clicking one fills the picker; it never submits. */
  suggestions: SkillSuggestion[]
  /**
   * Proposed by a model, with its reasoning. Separate from `suggestions` because provenance is the
   * point — one is arithmetic over spellings, the other is a model's reading.
   */
  modelSuggestions: ModelSuggestion[]
}

/**
 * A model's proposed reading of a term.
 *
 * Carries a rationale instead of a score: a model has no calibrated confidence to report, and a
 * number would invite trust it has not earned. A sentence you can check is the honest equivalent.
 */
export interface ModelSuggestion {
  skillId: number
  skillName: string
  category: SkillCategory
  rationale: string | null
  modelProfile: string | null
}

/** What one `POST /api/triage/suggest` run did. Counts, never a bare rate. */
export interface SuggestionRun {
  termsConsidered: number
  termsSent: number
  suggestionsStored: number
  /** Rows naming a skill the catalog could not resolve. Discarded, never queued. */
  droppedUnresolvable: number
  droppedUnrequested: number
}

export interface TriageQueue {
  entries: TriageEntry[]
  /** Pending terms passing the threshold. The denominator for "showing X of Y". */
  matching: number
  /** Every pending term, filter or no filter. */
  pending: number
  minOccurrences: number
  ranking: TriageRanking
  /** What the in-scope numbers were measured against. Render it, or the column is unreadable. */
  scopeSkills: string[]
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

export const OFFER_ORIGINS = ['PASTED', 'MARKET'] as const
export type OfferOrigin = (typeof OFFER_ORIGINS)[number]

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
  /** Whether you found this offer or the market poll did. */
  origin: OfferOrigin
  /** The corpus listing it was promoted from, when `origin` is `MARKET`. */
  marketOfferId: number | null
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
  /**
   * The documents actually sent for this offer, if any. Null is the ordinary state — a document
   * can be generated and never sent, and an application made outside the tool has none to name.
   * Independent of `status`: neither field implies the other.
   */
  sentCvDocumentId: number | null
  sentCoverLetterDocumentId: number | null
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

/** What `POST /api/market/offers/{id}/promote` answers with. */
export interface PromotedOffer {
  offerId: number
  marketOfferId: number
  /** True when this listing's text was already stored; nothing new was created. */
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
  /**
   * Catalog category, or null when the phrasing resolved to nothing.
   *
   * Under `V2_SOFT_EXCLUDED` a `SOFT` finding is reported but sits outside the score.
   */
  category: SkillCategory | null
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
  /**
   * Which rule produced `matchScore`.
   *
   * Versioned rather than migrated: old analyses keep `V1_ALL_CATEGORIES` and explain themselves in
   * those terms, because the score is stored while its explanation is recomputed. Do not apply the
   * current rule to an old report.
   */
  scoringRule: ScoringRule
  /** Computed getters. The UI derives the must-have split itself; see `mustHaves()` below. */
  mustHaves?: RequirementFinding[]
  niceToHaves?: RequirementFinding[]
  missingMustHaves?: RequirementFinding[]
  /** Requirements shown in the report but deliberately left out of the score. Empty under V1. */
  reportedNotScored?: RequirementFinding[]
  /** How the score was arrived at, so the number is never a black box. Print it verbatim. */
  scoreExplanation?: string
}

/** How an analysis's score was computed. See `AnalysisReport.scoringRule`. */
export const SCORING_RULES = ['V1_ALL_CATEGORIES', 'V2_SOFT_EXCLUDED'] as const
export type ScoringRule = (typeof SCORING_RULES)[number]

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
  /** Lets this view separate a soft-skill row from a scored technical gap. */
  category: SkillCategory | null
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

export const CREDENTIAL_KINDS = ['COURSE', 'BOOTCAMP', 'CERTIFICATION', 'OTHER'] as const
export type CredentialKind = (typeof CREDENTIAL_KINDS)[number]

export interface ProfileDetails {
  fullName: string
  headline: string | null
  email: string | null
  phone: string | null
  location: string | null
  summary: string | null
  /** What the candidate is aiming at next, as distinct from `summary`'s account of what they've done. */
  careerGoal: string | null
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

export interface Credential {
  id: number
  title: string
  issuer: string
  kind: CredentialKind
  url: string | null
  credentialId: string | null
  issuedOn: string | null
  expiresOn: string | null
}

/**
 * Side-project evidence. `skillIds` is a project-level skill badge declared directly, distinct
 * from the tags on the project's own `bullets` - the same distinction `project_skill` and
 * `experience_bullet_skill` make in the database.
 */
export interface Project {
  id: number
  name: string
  url: string | null
  description: string | null
  startedOn: string | null
  endedOn: string | null
  skillIds: number[]
  bullets: ExperienceBullet[]
}

export interface LanguageSkill {
  id: number
  language: string
  level: LanguageLevel
}

/**
 * A data-processing consent clause for a CV rendered in `language`. Rendered from here straight
 * into the document and never sent to a model. `text` may contain `{{company}}`, substituted from
 * the offer at render time by plain string replacement - left unsubstituted, visibly, when the
 * offer has no company name rather than inventing an employer.
 */
export interface ConsentClause {
  id: number
  language: string
  text: string
}

export interface CandidateProfile {
  details: ProfileDetails
  links: ProfileLink[]
  skills: ProfileSkill[]
  experiences: WorkExperience[]
  education: Education[]
  credentials: Credential[]
  projects: Project[]
  consentClauses: ConsentClause[]
  languages: LanguageSkill[]
  /**
   * Whether a portrait is stored - never the image itself. Fetch the bytes from
   * `GET /api/profiles/{id}/portrait`; the profile document deliberately never carries them.
   */
  hasPortrait: boolean
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

export interface CredentialImport {
  title: string
  issuer: string
  kind: CredentialKind
  url?: string | null
  credentialId?: string | null
  issuedOn?: string | null
  expiresOn?: string | null
}

export interface ProjectImport {
  name: string
  url?: string | null
  description?: string | null
  startedOn?: string | null
  endedOn?: string | null
  skills: string[]
  bullets: BulletImport[]
}

export interface ConsentClauseImport { language: string; text: string }

export interface LanguageImport { language: string; level: LanguageLevel }

export interface ProfileImport {
  details: ProfileDetails
  links: LinkImport[]
  skills: SkillImport[]
  experiences: ExperienceImport[]
  education: EducationImport[]
  credentials: CredentialImport[]
  projects: ProjectImport[]
  consentClauses: ConsentClauseImport[]
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
  careerGoal?: string | null
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

export interface CredentialRequest {
  title: string
  issuer: string
  kind: CredentialKind
  url?: string | null
  credentialId?: string | null
  issuedOn?: string | null
  expiresOn?: string | null
}

export interface ProjectRequest {
  name: string
  url?: string | null
  description?: string | null
  startedOn?: string | null
  endedOn?: string | null
  skillIds: number[]
}

export interface ConsentClauseRequest { language: string; text: string }

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
  /**
   * The consent clause language rendered onto this document, or null when the profile had none for
   * `language`. Always null for a cover letter, which carries no clause.
   */
  consentClauseLanguage: string | null
}

/* ---------------------------------------------------------------------- llm */

export const LLM_TASKS = ['EXTRACTION', 'NARRATIVE', 'DOCUMENT', 'TRIAGE'] as const
export type LlmTaskName = (typeof LLM_TASKS)[number]

export interface LlmCall {
  id: number
  /** Serialized as a plain String on the Kotlin side, so not narrowed to LlmTaskName. */
  task: string
  modelProfile: string
  modelName: string | null
  /** Upstream provider behind a router (OpenRouter reports one); null for direct providers. */
  servingProvider: string | null
  /** The provider's own generation id — the join key to their billing dashboard. */
  providerCallId: string | null
  /**
   * What the account was charged, in the provider's billing unit.
   *
   * `null` is not `0`. It means the provider reported no price at all (a local model), which is
   * why every total built from these carries how many of its calls were priced.
   */
  costUsd: number | null
  inputTokens: number | null
  outputTokens: number | null
  /** The part of `inputTokens` served from a prompt cache, and billed at a discount. */
  cachedInputTokens: number | null
  /** The part of `outputTokens` spent reasoning — paid for, and absent from the response text. */
  reasoningOutputTokens: number | null
  /** Anything but `STOP` is worth a look; `LENGTH` means a truncated answer you still paid for. */
  finishReason: string | null
  latencyMs: number | null
  error: string | null
  /** What caused the call, as an opaque label — `'OFFER'` today. */
  subjectKind: string | null
  subjectId: number | null
  createdAt: string
}

export interface LlmCallDetail {
  call: LlmCall
  requestJson: string
  responseText: string | null
}

/**
 * Spend over some slice, and the counts that say how much of it is measured.
 *
 * `pricedCalls` below `calls` means `costUsd` is a **floor**, not a total: the remaining calls went
 * to a provider that reported no price at all. Rendering the money without that pair is the mistake
 * this shape exists to make awkward.
 */
export interface SpendTotal {
  costUsd: number
  calls: number
  pricedCalls: number
  failedCalls: number
  inputTokens: number
  outputTokens: number
  /** Part of `inputTokens`, billed at a discount. A cache that stopped working shows up here. */
  cachedInputTokens: number
  /** Part of `outputTokens`. Paid for, and never visible in any response text. */
  reasoningOutputTokens: number
}

/** A null limit is no cap at all, not a cap of zero. */
export interface BudgetStatus {
  dailyLimitUsd: number | null
  dailySpentUsd: number
  monthlyLimitUsd: number | null
  monthlySpentUsd: number
  /** A cap is set and already reached, so the next model call will be refused. */
  exhausted: boolean
}

export interface SpendSummary {
  today: SpendTotal
  last7Days: SpendTotal
  last30Days: SpendTotal
  lifetime: SpendTotal
  /**
   * The first day the rollup holds — `YYYY-MM-DD`.
   *
   * "Lifetime" means since this day. Spend before cost capture existed was never recorded and
   * cannot be recovered, and saying so is the difference between a total and an understatement.
   */
  recordedSince: string | null
  budget: BudgetStatus
}

export const SPEND_BUCKETS = ['DAY', 'WEEK', 'MONTH'] as const
export type SpendBucket = (typeof SPEND_BUCKETS)[number]

export interface SpendPoint {
  /** `YYYY-MM-DD` — the first day of the bucket. */
  periodStart: string
  total: SpendTotal
}

/** Empty buckets are present and zero, so a quiet fortnight cannot compress the axis. */
export interface SpendSeries {
  bucket: SpendBucket
  from: string
  to: string
  points: SpendPoint[]
}

export interface SpendGroup {
  key: string
  total: SpendTotal
}

export interface SpendReport {
  summary: SpendSummary
  series: SpendSeries
  windowDays: number
  byTask: SpendGroup[]
  byModel: SpendGroup[]
  byProfile: SpendGroup[]
  /** Spend inside `windowDays` — the denominator for every share the breakdowns imply. */
  windowTotal: SpendTotal
}

/**
 * What the provider says the key has spent, as opposed to what this application recorded.
 *
 * Shown beside our own total, because **the gap is the point**: our figure is an undercount by
 * construction — nothing from before cost capture existed, and nothing spent on the same key by
 * anything else. `available: false` with a reason is the ordinary answer for a local model, and
 * must read as "not applicable" rather than as a broken dashboard.
 */
export interface ProviderAccount {
  modelProfile: string | null
  usageUsd: number | null
  usageTodayUsd: number | null
  usageMonthUsd: number | null
  /** Null means unlimited, not zero. */
  limitUsd: number | null
  limitRemainingUsd: number | null
  /** When the figure was actually read, which is not when it was served. */
  checkedAt: string | null
  unavailableReason: string | null
  available: boolean
}

/* -------------------------------------------------------------------- market */

/**
 * Levels as solid.jobs states them. `NICE_TO_HAVE` is the odd one out: the source carries its only
 * importance signal on this field rather than a separate one, and it is rare — 409 of 9,318
 * mentions — which is why the market measure does not weight by it.
 */
export const MARKET_SKILL_LEVELS = ['BASIC', 'ADVANCED', 'EXPERT', 'NICE_TO_HAVE', 'UNKNOWN'] as const
export type MarketSkillLevel = (typeof MARKET_SKILL_LEVELS)[number]

/** Held or IMPLIES-reachable is MET, RELATED-reachable is PARTIAL, anything else MISSING. */
export const COVERAGE_STATUSES = ['MET', 'PARTIAL', 'MISSING'] as const
export type CoverageStatus = (typeof COVERAGE_STATUSES)[number]

export const DEMAND_RANKINGS = ['UNMET', 'TOTAL'] as const
export type DemandRanking = (typeof DEMAND_RANKINGS)[number]

/**
 * The population every other number on the dashboard is measured over.
 *
 * Render it above the statistics, not beneath them. A median with no source, window or size is
 * indistinguishable from an accident, and this object is the whole of that context.
 */
export interface MarketScopeReport {
  /** Boards the corpus came from. Label charts with these, never with "the market". */
  sources: string[]
  /** Configured scope skills the catalog resolved. What "in scope" actually meant. */
  scopeSkills: string[]
  /** Configured names the catalog could not resolve, and therefore silently ignored. Show them. */
  unresolvedScopeSkills: string[]
  offersInScope: number
  /** In-scope offers whose stated validity has passed. Excluded from every statistic. */
  expiredInScope: number
  corpusOffers: number
  firstSeenAt: string | null
  lastSeenAt: string | null
  skillMentions: number
  /** Mentions the catalog could not place. The ceiling on how complete any ranking can claim to be. */
  unresolvedMentions: number
  /** Computed server-side: whether the scope is large enough to show a salary figure at all. */
  meetsSalaryFloor: boolean
}

/**
 * One comparable slice of salaries — never pooled across contract types.
 *
 * 21,800 B2B and 21,800 UoP are different money. Quartiles are discrete percentiles, so every
 * figure here is one an employer actually stated rather than an interpolation between two.
 */
export interface SalaryGroup {
  employmentType: string | null
  currency: string | null
  period: string | null
  offers: number
  medianFrom: string | null
  medianTo: string | null
  p25From: string | null
  p75To: string | null
  /** False means render the count and the words, not a greyed-out tile that reads as loading. */
  meetsSampleFloor: boolean
}

export interface SalaryReport {
  groups: SalaryGroup[]
  offersInScope: number
  offersWithSalary: number
  coverage: number
  meetsCoverageFloor: boolean
}

/** The band for one demand row, over the employment type it names. */
export interface SalaryBand {
  offers: number
  medianFrom: string | null
  medianTo: string | null
  currency: string | null
  period: string | null
  employmentType: string | null
}

export interface DemandEntry {
  skillId: number
  skillName: string
  category: SkillCategory
  /** In-scope offers asking for it at any level. */
  offers: number
  /** Of those, offers asking for it as something other than nice-to-have. */
  requiredOffers: number
  status: CoverageStatus
  /** The held skill accounting for a MET or PARTIAL — "you have Quarkus", not an amber dot. */
  coveredBySkillId: number | null
  coveredBySkillName: string | null
  levelMix: Partial<Record<MarketSkillLevel, number>>
  /** Null below the five-offer floor. Null is "too few offers", never "pays nothing". */
  salary: SalaryBand | null
}

export interface DemandReport {
  entries: DemandEntry[]
  /** Embedded so no caller can render a ranking without its denominators. */
  scope: MarketScopeReport
  skillsInScope: number
  /** Skills the profile does not cover at all. The size of the actual gap. */
  unmetSkillsInScope: number
  ranking: DemandRanking
  limit: number
}

export interface MarketSalary {
  from: string | null
  to: string | null
  currency: string | null
  period: string | null
  employmentType: string | null
}

export interface MarketOfferSummary {
  id: number
  source: string
  title: string
  company: string | null
  url: string | null
  experienceLevel: string | null
  isRemote: boolean
  isHybrid: boolean
  locations: string[]
  salary: MarketSalary | null
  validTo: string | null
  lastSeenAt: string
  skillsResolved: number
  skillsCovered: number
  /**
   * Listed skills the catalog could not place — neither covered nor missing, but unknown.
   *
   * Always render it next to the covered count. "6 of 6" beside three unresolved terms is not a
   * covered offer: measured on the corpus, nine of ten apparently-covered offers were that way
   * only because these had been dropped.
   */
  skillsUnresolved: number
}

export interface MarketOfferPage {
  entries: MarketOfferSummary[]
  /** Offers matching the same filter, so a page can never read as the whole corpus. */
  total: number
  limit: number
  offset: number
}

/**
 * What one ingestion run did.
 *
 * Counts, never a bare rate. `skillResolutionRate` is deliberately null below a minimum sample —
 * 1 of 1 resolved is indistinguishable from 90 of 90 — so render the counts and let the rate be
 * absent rather than substituting a 1.0 that means nothing.
 */
export interface IngestionReport {
  source: string
  startedAt: string
  finishedAt: string
  pagesFetched: number
  offersSeen: number
  offersInserted: number
  offersUpdated: number
  skillMentions: number
  skillsResolved: number
  distinctUnresolvedTerms: number
  error: string | null
  skillsUnresolved: number
  /** Null below the sample floor, not zero. */
  skillResolutionRate: number | null
}

/**
 * Whether the corpus refreshes itself, and when it next will.
 *
 * A corpus polled nightly and a corpus that only moves when someone presses a button produce
 * identical numbers, but one of them goes stale unwatched. The dashboard says which it is showing.
 */
export interface IngestionSchedule {
  scheduled: boolean
  /** The scheduler's cron, verbatim. Null exactly when `scheduled` is false. */
  cron: string | null
  /** Derived from the cron on read, so it cannot drift from what the scheduler will do. */
  nextPollAt: string | null
  lastPolledAt: string | null
}

/** What the corpus holds per source. Bounds the window any statistic may claim. */
export interface CorpusSummary {
  source: string
  offers: number
  currentlyValid: number
  firstSeenAt: string | null
  lastSeenAt: string | null
}

/* ------------------------------------------------------------------ helpers */

export const mustHaves = (r: AnalysisReport): RequirementFinding[] =>
  r.requirements.filter((x) => x.importance === 'MUST_HAVE')

export const niceToHaves = (r: AnalysisReport): RequirementFinding[] =>
  r.requirements.filter((x) => x.importance === 'NICE_TO_HAVE')

export const isCurrentRole = (e: WorkExperience): boolean => e.endedOn === null
