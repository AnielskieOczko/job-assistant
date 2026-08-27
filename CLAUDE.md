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
`docs/frontend.md` covers the UI. Decisions already taken for work not yet started live in
`docs/roadmap.md`; `docs/adr/` records the ones that were hard to reverse.

## What is being decided right now

The next phase of the application is being planned as a wayfinder map, and
**`docs/roadmap-wayfinding.md` is where a new session picks that up.** Read it before proposing a
feature: it records what the effort is aiming at, what has already been ruled out and why, and four
things that look missing but already ship — application lifecycle tracking, the unmatched-term
triage UI, output-language parameterisation, and cross-offer skill aggregation.

The live map is GitHub issue #9 with its child issues; `docs/roadmap-wayfinding.md` is a checked-in
snapshot of it and says so. Findings from resolved research tickets live in
`docs/research/`, one file per ticket, each citing a source against every claim. Continue the effort with `/mattpocock-skills:wayfinder 9`, and refresh
the snapshot in the same commit that resolves a ticket. When the two disagree, GitHub is right.

`docs/roadmap.md` is a different file with a different job: it holds decisions already taken for
work not yet started. Rewriting it is the last act of the current map, not an input to it.

## The rule that governs the whole design

**The AI must never be able to invent experience the candidate does not have.**

- The profile is hand-authored ground truth in Postgres. No model writes to it.
- The gap diff is deterministic Kotlin over a curated skill catalog — not a model's judgement — so
  the same offer scores identically every run.
- A tailored CV may select, reorder and rephrase profile records. It may not introduce a
  technology absent from the profile, and a test enforces that rather than a prompt asking nicely.

When adding a feature, ask which side of that boundary it falls on. Generated prose is fine;
generated *facts* are not.

## The second rule: no personal data leaves the machine

**A direct identifier must never reach a model provider.** The first rule is about what comes back
from a model; this one is about what goes out. Prompts go to OpenRouter, so every byte in one is
disclosed to a third party.

Name, email, phone and profile links are never sent. Employers, schools, dates and bullet text still
are — tailoring is worthless without them — so the boundary is *direct identifiers*, not "anything
personal".

Three layers, in order:

1. **Minimize.** Prompt builders leave identifiers out. `ProfileBriefing` sends no name: nothing in
   the prompts references it, `TailoredCv`/`CoverLetter` have no contact fields, and the rendered
   header is rebuilt from the database afterwards. Sending it bought nothing.
2. **Scrub.** `OfferTextScrubber` strips recruiter emails and phone numbers out of pasted offer text
   before extraction — that is a third party's data and the extractor has no use for it.
3. **Assert.** `PromptPrivacyInvariant`, wired in via `InspectingChatModel`, refuses any outgoing
   request carrying an identifier. Like `CvInvariant` it is a floor, not a ceiling: it deliberately
   ignores `location` and single name tokens, because a hard refusal on "Poland" or a common surname
   would break every Polish job offer.

If the guard ever fires, **a prompt builder is the bug** — do not loosen the check. And note
`SensitiveDataInPromptException` reports field *names*, never values: its message is persisted to
`analysis.error` and served over HTTP, so echoing the value would leak what the refusal prevented.

When adding a prompt, ask what profile fields it interpolates. `PromptPrivacyIntegrationTest` runs
every flow with sentinel values and fails if one reaches a model.

## Before you change code

**Never commit to `main`.** Before starting any code change, bring `main` up to date
(`git checkout main && git pull`) and branch from it (`git checkout -b <type>/<short-description>`).
Do the work on that branch, and leave `main` for merges only.

If you notice you have already started editing on `main`, branch before committing rather than
after — `git checkout -b <branch>` carries uncommitted changes across.

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
| `profile` | Candidate profile: details, links, skills, experience + bullets, education, languages |
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
- HTTP-level assertions (status codes, `ProblemDetail` extension names) go in a test that builds
  `MockMvc` from the `WebApplicationContext` — Boot 4 moved the MockMvc autoconfiguration out of
  `spring-boot-test-autoconfigure`, so `@AutoConfigureMockMvc` is not on the classpath.
  `ProfileCrudHttpTest` is the pattern.
- The eval tier scores a **live** model against labelled fixture pairs in
  `src/test/resources/eval/offers` (`NN-name.txt` plus its expected `NN-name.json`), shared
  through `EvalFixtures`. `OfferExtractionEvalTest` scores extraction; `CvTailoringEvalTest` scores
  tailoring by counting what `CvSelection` had to drop, which needs no rubric because a bullet id
  or skill the profile cannot back is an objective fabrication. The tailoring fixture profile
  (`src/test/resources/eval/profile.json`) deliberately lacks Kubernetes, Kafka and Terraform so
  the offers asking for them create a real opportunity to invent.
- **Every eval writes to `EvalScorecard`, not to stdout.** The tier exists to be comparable across
  runs, and a number that only ever reached the terminal cannot be compared with one from last
  week. Each run leaves `target/eval-report.json` and `target/eval-report.md`, stamped with the
  model profile that produced it. The write is a JVM shutdown hook armed on first use, so the fast
  tier leaves no report behind; `ScorecardTest` covers the rendering in the fast tier, because the
  tier it serves is run by hand and would not notice a scorecard that silently wrote nothing.
- Assertions in the eval tier are regression floors, not quality targets. The number that matters
  is in the scorecard; the assertion only exists to fail a run that has fallen off a cliff.

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

`CvSelection` counts what it drops, and those counts are persisted on `generated_document`
(`dropped_bullet_count`, `dropped_skill_count`, added in `V12`) and served on `GeneratedDocument`.
They are not a warning about any one document — selection already removed the offending choices, so
what rendered is backed by the profile either way. They are a **fabrication rate measured on real
offers**, which fixtures cannot give you: a count that climbs after a prompt or model change is the
first sign tailoring has started guessing. Query the distribution, don't read rows:

```sql
select type, count(*), avg(dropped_skill_count) from generated_document group by type;
```

**The cover letter prompt must never invite naming an absent technology**, even in an honest
negative ("I have not used Kubernetes"). The invariant has no notion of negation, so such a letter
is rejected outright — the prompt and the guard have to agree.

## Profile editing

Two write paths, one set of rules. `POST /api/profile/import` is a **full replace** — the document
is the profile — and everything else is per-entity CRUD under `/api/profile/{collection}`, with
`PUT /api/profile/details` doubling as the way a profile comes into existence at all.

Both reject a skill the catalog cannot resolve rather than dropping it silently (a dropped skill
would vanish from every future gap report), and both reject a bullet tagged with a skill the profile
does not declare — otherwise that skill could reach a CV with nothing behind it. The shared part of
that lives in `ProfileInvariants`; import expresses it over names because that is what its rejection
message must name, CRUD over ids. Deleting a declared skill is the same rule from the other side and
returns 409 with `blockingBullets`; there is **no** FK between `profile_skill` and
`experience_bullet_skill`, so nothing else enforces it.

**`ExperienceBullet.id` must survive edits that are not its own.** `CvTailor` selects bullets by id
and `CvSelection.from` drops ids the profile lacks — that is the anti-fabrication mechanism. Spring
Data JDBC deletes and reinserts a whole `@MappedCollection` on every save of its owner, so
`experience_bullet` is its own aggregate root rather than a collection on `WorkExperienceRow`. Do
not move it back.

Updates are `PUT` with full-entity bodies, never `PATCH`: `endedOn = null` is what makes a role
current, and Kotlin data classes cannot tell an absent field from an explicit null without wrapping
every property. Writes take `skillId`, not a name — the picker resolved it already.

`ProfileService` stays `current() / require() / replace() / revision()`. **CRUD is internal**; no
other module has any business writing a single skill or bullet. `revision()` is a counter bumped by
every profile write, stamped onto `analysis` and `generated_document` so output an edit has overtaken
can be shown as stale rather than current.

The reasoning behind all of this is in `docs/adr/0001-per-entity-profile-crud.md`.

## Adding a migration

Flyway, `src/main/resources/db/migration`, Postgres-specific. Never test against H2. The catalog
seed (`V2`) is generated from a compact definition — if you edit it, keep `normalized_alias`
consistent with `SkillNormalizer` or the drift test will catch you.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).