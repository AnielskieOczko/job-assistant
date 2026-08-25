# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## What this is

A personal job-search assistant. You paste a job offer, an AI agent extracts its requirements, the
app compares them against a verified profile and produces a transparent skill-gap report, an
improvement plan, and — on demand — a CV and cover letter tailored to that offer.

Single user, no authentication, bound to loopback. The full architecture decisions and milestone
plan live in `~/.claude/plans/grill-with-docs-ok-lets-start-sharded-oasis.md`; `docs/api-flow.md`
walks the whole HTTP flow end to end, `docs/profile-format.md` documents the profile document, and
`docs/frontend.md` covers the UI.

## The rule that governs the whole design

**The AI must never be able to invent experience the candidate does not have.**

- The profile is hand-authored ground truth in Postgres. No model writes to it.
- The gap diff is deterministic Kotlin over a curated skill catalog — not a model's judgement — so
  the same offer scores identically every run.
- A tailored CV may select, reorder and rephrase profile records. It may not introduce a
  technology absent from the profile, and a test enforces that rather than a prompt asking nicely.

When adding a feature, ask which side of that boundary it falls on. Generated prose is fine;
generated *facts* are not.

## Commands

```bash
docker compose up -d          # local Postgres (required for integration tests)
./mvnw clean install          # build + fast test tier
./mvnw test                   # fast tier only
./mvnw spring-boot:run        # run against local Postgres on :8080 (loopback only)
./mvnw test -Dtest=ClassName  # single test class

cd frontend && npm ci && npm run dev   # Vite on :5173, proxies /api to :8080
./mvnw -Pfrontend spring-boot:run      # build the SPA and serve everything from :8080
./mvnw -Pfrontend clean package        # jar with the SPA inside
```

`./mvnw test` is unchanged by the frontend and never invokes npm. A plain `./mvnw package`
produces a jar with **no** UI — any build meant to run standalone needs `-Pfrontend`.

Test tiers are Maven profiles, not `-Dgroups`: JUnit applies exclusions before inclusions, so the
groups have to be swapped rather than added to.

| Tier | How to run | Needs |
|---|---|---|
| fast (default) | `./mvnw test` | Docker, for Testcontainers Postgres |
| pdf | `./mvnw test -Ppdf` | Chromium (downloaded to `~/.cache/ms-playwright` on first run) |
| eval | `./mvnw test -Peval` | `OPENROUTER_API_KEY`; costs tokens, skips silently without it |

## Tech stack

- Kotlin 2.3.21 / Java 21, Maven, `spring-boot-starter-parent` 4.1.1
- Sources in `src/main/kotlin` and `src/test/kotlin` (set explicitly in `pom.xml`)
- Base package `com.jankowski.rafal.jobassistant`
- Kotlin compiler runs the `spring` plugin with `-Xjsr305=strict`, `javaParameters=true`
  (LangChain4j reads AI-service parameter names reflectively) and
  `-Xannotation-default-target=param-property`
- Spring Data JDBC + Flyway on Postgres. **Not JPA** — aggregates are explicit and entities are
  immutable Kotlin data classes.
- LangChain4j 1.19.0 for LLM work, used **without** its Spring Boot autoconfiguration
- Playwright 1.62.0 (headless Chromium) renders Thymeleaf HTML to PDF
- Spring Modulith 2.1.0 enforces module boundaries
- Virtual threads are on; the analysis pipeline still uses its own bounded pool (see below)
- Frontend in `frontend/`: Vite 8, React 19, TypeScript 6, Tailwind 4, shadcn/ui, TanStack Query 5,
  react-router 8. `typescript` is pinned to `~6` because the shadcn CLI depends on `ts-morph`,
  which needs the JavaScript compiler API that the TS 7 native port does not fully expose.
  Tailwind 4 is CSS-first: there is **no `tailwind.config.js` and no `postcss.config.js`**, and
  `components.json` carries an empty `"config"` on purpose. Import from `react-router`, not
  `react-router-dom` — the latter was never published for v8.

## Boot 4 gotchas that will bite

- Jackson is **3.x** — imports are `tools.jackson.*`, not `com.fasterxml.jackson.*`
- The web starter is `spring-boot-starter-webmvc`
- Testcontainers is **2.x**: `org.testcontainers.postgresql.PostgreSQLContainer`, non-generic, and
  artifacts are prefixed (`testcontainers-postgresql`)
- Spring Modulith is not managed by the Boot BOM; its version is pinned in `<properties>`

## Module structure

Feature slices under the base package, each a Spring Modulith module. Anything in a module's
`internal` package is private to it; `ModularityTest` fails the build on a violation.

| Module | Owns |
|---|---|
| `catalog` | Canonical skills, aliases, IMPLIES/RELATED relations, unmatched-term review queue |
| `profile` | Candidate profile: skills, experience + bullets, education, languages |
| `offer` | Job offers (raw text + content hash), application lifecycle |
| `analysis` | Async analysis job, extraction, deterministic diff, narrative, learning plan |
| `document` | CV and cover letter generation, templates, PDF rendering, invariant enforcement |
| `llm` | Model profiles, `ChatModel` factory, AI services, call audit, parse-repair |

`catalog` is depended on by nearly everything; it has no dependencies of its own.

`frontend/` sits outside the Modulith world entirely. `SpaWebConfiguration` lives in the **base
package**, next to `JobAssistantApplication`, because `ApplicationModules.of(...)` treats every
direct sub-package as a module — a `…jobassistant.web` package would be detected as a seventh
module and stand alongside `analysis` and `document`.

## Skill resolution

Everything downstream refers to `canonical_skill.id`, never free text. `SkillNormalizer` collapses
spellings onto a lookup key (`React.js`, `react js`, `REACTJS` → `reactjs`), expanding `+` and `#`
first so `C`, `C++` and `C#` stay distinct. The seed migration precomputes the same values;
`SkillCatalogIntegrationTest` fails if the two ever drift.

Terms the extractor can't place go to `unmatched_term` for human review. **Do not let code create
canonical skills automatically** — the review queue is the point.

`SkillCoverage` expands the held skill set once through the relation graph and is the sole source
of a verdict: held **or** IMPLIES-reachable is `MET`, RELATED-reachable is `PARTIAL`, anything else
is `MISSING`. It carries provenance (`impliedBy` / `relatedBy`), not just a status, so the report
can say "you have Quarkus, they want Spring Boot" instead of showing an unexplained amber light.

## The analysis pipeline

`AnalysisRunner` runs one job end to end on the `analysisExecutor` pool: extract → match →
narrate. **Only steps 1 and 3 call a model.** Step 2 is `RequirementMatcher`, plain Kotlin over
`SkillCoverage`, so the verdict is reproducible and the model never gets a vote on whether the
candidate has a skill.

- States: `PENDING → EXTRACTING → MATCHING → NARRATING → DONE`, or `FAILED` with an error message.
- The pool is deliberately 2 threads / queue 20 — each job costs two model calls, so unbounded
  concurrency means unbounded spend.
- A job orphaned by a restart is marked `FAILED` at startup (`failOrphanedAnalyses`) rather than
  left for a client to poll forever. Keep that property if you touch the lifecycle.

## Model configuration

Providers are configured, not coded. OpenRouter, Requesty, Ollama and LM Studio are all
OpenAI-compatible, so a "profile" under `job-assistant.llm.profiles` is just a base URL, key, model
name and a `strict-schema` flag — there is no provider SPI, and adding one would be a mistake.

`job-assistant.llm.tasks` routes each `LlmTask` (`EXTRACTION`, `NARRATIVE`, `DOCUMENT`) to a profile
by name, so extraction can run on a different provider from narrative writing with no code change.
A task pointing at an undefined profile fails loudly at first use.

Environment: `OPENROUTER_API_KEY` / `REQUESTY_API_KEY` for models, `DB_URL` / `DB_USER` /
`DB_PASSWORD` for Postgres (defaults match `docker-compose.yml`). Local dev uses the compose
Postgres; deployment targets Neon, which is why the Hikari pool is tiny and tolerant of cold starts.

## Writing an AI service

Declare the interface in the module that owns the concept (`analysis` owns extraction, `document`
owns CV tailoring), then build it with `AiServiceFactory.create(MyService::class.java, task)`.
The `llm` module never learns about job offers or CVs. Prompts live as Markdown in
`src/main/resources/prompts/`, one system/user pair per task.

**Every property of a service return type must have a default value.** LangChain4j deserialises
return types reflectively without the Jackson Kotlin module, so a data class with required
constructor parameters compiles fine and fails at runtime:

```kotlin
data class Extracted(
    val title: String = "",            // correct
    val requirements: List<Req> = emptyList(),
)
```

Non-JSON responses are handled by `JsonOutputGuardrail`: markdown fences and surrounding
commentary are stripped in place at no cost, and only a genuinely JSON-free response triggers a
single reprompt. Both round trips are audited — every model call lands in `llm_call` with its
prompt, response, token usage and latency.

## Testing conventions

- `@IntegrationTest` boots the full app against Testcontainers Postgres **and** swaps every model
  for a `ScriptedChatModel`. No integration test can reach a real provider, whether or not an API
  key is in the environment. Tests queue the exact JSON a model would return and assert on
  everything downstream.
- `ScriptedChatModel` overrides only `doChat`, so LangChain4j's listener pipeline still runs and
  the audit trail is exercised exactly as in production.
- Pure-logic tests (`SkillNormalizerTest`, `SkillCoverageTest`, `LanguageLevelTest`) must **not**
  use `@IntegrationTest` — they belong in the fast tier with no container at all.
- Tier membership comes from `@Tag("pdf")` / `@Tag("eval")`; untagged means fast.
- **The API types in `frontend/src/api/types.ts` are hand-written.** If you change a wire DTO in
  Kotlin, change that file in the same commit. `ApiContractTest` pins the JSON key set of every
  DTO that crosses the wire and fails the fast tier if you forget. Watch for Kotlin's `is` prefix
  surviving serialization: `WorkExperience.isCurrent` is emitted as `isCurrent`, not `current`.
- The eval tier scores extraction against labelled fixture pairs in `src/test/resources/eval/offers`
  (`NN-name.txt` plus its expected `NN-name.json`).

## Spring Data JDBC gotchas

**A `default now()` column will not work with a nullable Kotlin property.** Spring Data JDBC
writes every mapped property, so `val createdAt: Instant? = null` sends an explicit `NULL` and
violates the not-null constraint. Set the value in Kotlin instead:

```kotlin
val createdAt: Instant = Instant.now()   // not Instant? = null
```

Composite-key join tables (`skill_relation`, `experience_bullet_skill`) are handled either as an
owned `@MappedCollection` inside an aggregate or with `JdbcClient` directly — not as repositories.

## Generating a document

`document` is the only module allowed to put words in front of an employer, so it carries two
guards:

1. **Selection by id.** `CvTailor` returns bullet *ids* and skill *names*; `CvSelection.from`
   drops anything not present in the profile, so a hallucinated bullet has no text to render.
2. **`CvInvariant`.** Scans the finished document for any catalog skill the candidate does not
   hold and throws `FabricatedClaimException` if it finds one. It runs over
   `HtmlText.visibleText(html)`, never the raw markup — a CV page contains the words "HTML" and
   "CSS" in its own doctype and stylesheet, and both are catalog skills.

The invariant only scans concrete technical categories, skipping `PRACTICE`, `SOFT` and `OTHER`
plus short ambiguous names (`Go`, `C`, `REST`). It is a floor, not a ceiling: it reliably catches
Kubernetes, Kafka and Terraform, and knowingly ignores vocabulary where a false positive would
reject every honest CV. A rejected generation stores nothing and surfaces as HTTP 422 with
`fabricatedClaims`.

**The cover letter prompt must never invite naming an absent technology**, even in an honest
negative ("I have not used Kubernetes"). The invariant has no notion of negation, so such a letter
is rejected outright — the prompt and the guard have to agree.

## Profile import

`POST /api/profile/import` is a **full replace**, not a merge — the document is the profile. It
rejects with HTTP 400 listing every skill name the catalog cannot resolve rather than dropping it
silently (a dropped skill would vanish from every future gap report), and it rejects a bullet
tagged with a skill the profile does not declare — otherwise that skill could reach a CV with
nothing behind it.

## Adding a migration

Flyway, `src/main/resources/db/migration`, Postgres-specific. Never test against H2. The catalog
seed (`V2`) is generated from a compact definition — if you edit it, keep `normalized_alias`
consistent with `SkillNormalizer` or the drift test will catch you.
