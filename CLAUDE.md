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

`docs/architecture-review-2026-08.md` is a dated snapshot review of where the codebase is *shallow* —
five deepening candidates, plus three things that look like candidates and are deliberately left
alone. It is an input to a conversation, not a decision: nothing in it has been agreed, and anything
acted on should move to `docs/roadmap.md` or an ADR as it is settled.

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
docker compose up -d          # local dev Postgres (required for integration tests)
./mvnw clean install          # build + fast test tier
./mvnw test                   # fast tier only
./mvnw spring-boot:run        # dev profile, :8080 (loopback only), dev database
./mvnw test -Dtest=ClassName  # single test class

cd frontend && npm ci && npm run dev   # Vite on :5173, proxies /api to :8080
./mvnw -Pfrontend spring-boot:run      # build the SPA and serve everything from :8080
./mvnw -Pfrontend clean package        # jar with the SPA inside
```

`./mvnw test` is unchanged by the frontend and never invokes npm. A plain `./mvnw package`
produces a jar with **no** UI — any build meant to run standalone needs `-Pfrontend`.

## Environments

There are two, and they share no data. **`docs/operations.md` is the runbook** — read it before
touching anything that starts a process or moves a row.

| | dev | prod |
|---|---|---|
| Start | `./mvnw spring-boot:run` | `scripts/run-prod.sh` |
| Spring profile | `dev` (the default) | `prod`, explicit only |
| App | `127.0.0.1:8080` | `127.0.0.1:8090` |
| Database | `:5432/jobassistant_dev` | `:5433/jobassistant` |
| Compose | `docker-compose.yml` | `docker-compose.prod.yml` |
| Market poll | off | on, daily |
| Data | disposable; `scripts/seed-dev.sh` refills it | the hand-authored profile, and the real application history |

The split exists because the profile is ground truth that no migration and no re-poll can rebuild.
Three mechanisms keep a development mistake away from it, and all three are load-bearing:

- **`spring.profiles.default: dev`.** Reaching production takes an explicit
  `--spring.profiles.active=prod`; nothing gets there by forgetting something.
- **`application-prod.yaml` gives `DB_PASSWORD` no default.** Every other property could be wrong
  and still start; this one stops a prod launch that never sourced `.env.prod`, rather than letting
  it fall through to the dev credentials and write into the wrong database. Do not add a default.
  `ProductionEnvironmentCheck` makes that failure legible: Boot's `Binder` *ignores* an unresolvable
  placeholder rather than throwing, so the raw symptom is a 30-second pool timeout that looks
  nothing like its cause.
- **The prod volume is declared `external: true`,** so `docker compose ... down -v` will not remove
  it. That declaration is the guardrail, not a formality.
- **Each compose file names its own project** (`job-assistant-dev` / `job-assistant-prod`). Both
  describe a service called `postgres`, and compose otherwise derives the project from the
  directory — so without those names, bringing prod up *recreates the dev container* rather than
  starting a second database.

The ports and database names are asymmetric on purpose — dev is `5432`/`jobassistant_dev`, prod is
`5433`/`jobassistant` — so a half-applied change misses rather than lands on the wrong data.
`EnvironmentConfigurationTest` pins exactly these properties in the fast tier, because their drift
is the one kind that is completely silent at runtime.

Backups are `scripts/db-backup.sh` (nightly via launchd, and before every prod start, since
start-up is when Flyway runs) into a directory **outside this repository** — a dump carries the
name, email and phone, and this repository is public. Each dump carries a `.counts` sidecar so
`scripts/db-verify-restore.sh` can check a restore against what the dump was supposed to hold; it
runs monthly, because a backup that has never been restored is a file, not a backup.

Test tiers are Maven profiles, not `-Dgroups`: JUnit applies exclusions before inclusions, so the
groups have to be swapped rather than added to.

| Tier | How to run | Needs |
|---|---|---|
| fast (default) | `./mvnw test` | Docker, for Testcontainers Postgres |
| pdf | `./mvnw test -Ppdf` | Chromium (downloaded to `~/.cache/ms-playwright` on first run) |
| eval | `./mvnw test -Peval` | `OPENROUTER_API_KEY`; costs tokens, skips silently without it |

There is a fourth profile, `coverage`, which is not a tier: it adds JaCoCo to whichever tier is
running and writes `target/site/jacoco/jacoco.xml` during `verify`. It exists for the CI Sonar job
and is off by default on the same principle as the tiers — a plain `./mvnw test` should not carry an
agent it has no use for.

## Continuous integration

Two workflows in `.github/workflows`. Job names are the check names, so renaming a job silently
un-requires it in the branch ruleset.

| Workflow | Job | Runs on | Required |
|---|---|---|---|
| `ci.yml` | `build` — fast tier, JaCoCo, SonarQube Cloud | every PR, push to `main` | **yes** |
| `ci.yml` | `frontend` — `npm ci`, `oxlint`, `tsc -b` | every PR, push to `main` | **yes** |
| `ci.yml` | `pdf` — `-Ppdf` with a cached Chromium | every PR, push to `main` | no, advisory |
| `ci.yml` | `package` — `-Pfrontend clean package -DskipTests` | push to `main` only | no |
| `eval.yml` | `eval` — `-Peval`, scorecard to artifact and job summary | `workflow_dispatch`, or the `run-eval` label | no |

`pdf` and the Sonar quality gate stay advisory until each has a run history behind it. A required
check that flakes is worse than no required check; promoting either later is one checkbox.

**`eval.yml` is the only workflow that may reference `OPENROUTER_API_KEY`**, and the key lives in
the `eval` GitHub Environment behind a required reviewer, not in plain repository secrets, so every
token spend is something a human approved. **`pull_request_target` is never used in this
repository** — it would run fork code with access to that secret. The repository is public, so a
fork PR receives no secrets at all; the Sonar step is written to skip rather than fail when
`SONAR_TOKEN` is absent, which is what that case looks like.

Sonar analysis is CI-based rather than Automatic. Automatic analysis supports Kotlin, but fully
covers only the default branch — and `main` here is merges-only, so a finding that arrives on `main`
arrives after the decision. It also cannot import coverage at all. The `sonar.*` properties live in
`pom.xml`, including two coverage exclusions that are honest rather than cosmetic: the frontend has
no test suite yet, and `PlaywrightDocumentRenderer` *is* tested, by the `pdf` tier that the coverage
job deliberately does not run.

Two Sonar traps, both of which fail silently rather than loudly:

- **`sonar.maven.scanAll` must be a command-line `-D`.** It is a `sonar.maven.*` property, read from
  Maven's *user* properties rather than the project's, so putting it in `pom.xml` does nothing and
  says nothing — the scanner just indexes the Java source roots and the frontend vanishes from the
  report. Ordinary `sonar.*` properties in the pom are read normally, which is what makes this
  look like it worked. Verify by grepping the scanner log for `Quality profile for ts`.
- **Setting `sonar.sources` disables `scanAll` entirely.** They are alternatives, not complements,
  which is why the pom sets neither.

Also: **Automatic Analysis and CI-based analysis cannot both be on.** If Automatic is enabled in the
SonarQube Cloud project, the CI analysis fails and takes the build with it. It is on by default when
a repository is first imported; turn it off at Project → Administration → Analysis Method.

`main` is protected: pull request required, zero approvals (GitHub does not let you approve your own
PR, so any higher number deadlocks a single-committer repo), no force-push, no admin bypass.

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

- Jackson is **3.x** — imports are `tools.jackson.*`, not `com.fasterxml.jackson.*`. Its
  *annotations* are the exception: `@JsonIgnoreProperties` and friends still come from
  `com.fasterxml.jackson.annotation`, because Jackson 3 depends on the 2.x `jackson-annotations`
  artifact unchanged. Both package roots are on the classpath and both are correct, for different
  things.
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
| `market` | Ingested board offers as a market corpus, the solid.jobs client, the scheduled poll |
| `triage` | The ranked, filtered review queue, plus model-assisted suggestions for it |

`catalog` is depended on by nearly everything; it has no dependencies of its own — and keeping it
that way is why `triage` exists.

**`triage` is a join, not a feature slice.** Ranking the review queue needs `unmatched_term` (owned
by `catalog`) *and* in-scope demand from `market_offer_skill` (owned by `market`). A `catalog →
market` edge would put an HTTP client and a scheduler into every module's transitive closure, so the
join lives in a module that may depend on both. `market` exposes `MarketDemand` for it — separate
from `MarketOfferService` because pulling offers in and answering questions about offers already
held are different jobs. **`triage` reads and never writes**: approve and reject stay on
`/api/catalog/unmatched/{id}`, because `unmatched_term` exists so that only a human decision can
grow the catalog and a second write path is a second place to forget it. When model-assisted
suggestions land, `triage` gains `llm` and `catalog` still depends on nothing.
`docs/adr/0003-triage-outside-the-catalog.md` records the reasoning. `triage` depends on `catalog`,
`market` and `llm`; `catalog` still depends on nothing, which was the point.

**Suggestions come from two mechanisms and are kept apart on purpose.** `SkillCatalog.suggest` is
deterministic trigram similarity, computed on read, and carries a score. `TriageSuggester` is a
model, **stored** rather than recomputed, and carries a *rationale* instead — a model has no
calibrated confidence to report and printing a number would invite trust it has not earned.
Provenance is why they are separate fields rather than one merged list: a reviewer weighs
arithmetic-over-spellings differently from a model's reading.

**`GET /api/triage/queue` never calls a model.** Suggestions are produced only by
`POST /api/triage/suggest`, one call per batch of at most 50 terms, because a page load that
silently spends tokens would contradict the approval-gated eval environment and the 2-thread
analysis pool. Every `catalogSkill` a model returns is re-resolved through `catalog.resolve` and
dropped if it does not exist — `CvSelection.from` transplanted — and a row naming a term that was
not sent is dropped too. `SuggestionRun` reports those counts rather than a bare total, on the same
principle as `dropped_skill_count`: `droppedUnresolvable` climbing is the first sign the model has
started naming skills the catalog cannot back.

Two rules the queue itself enforces, both instances of *never report a number without its
denominator*: the frequency filter applies to the **sum** of `occurrences` and `market_occurrences`
(every corpus term has `occurrences = 0`, so filtering the candidate's counter alone would hide the
whole market behind a control meant to cut the singleton tail), and the response carries `matching`
and `pending` alongside `entries`, so a 100-row page out of 1,540 cannot read as a finished queue.

`frontend/` sits outside the Modulith world entirely. `SpaWebConfiguration` lives in the **base
package**, next to `JobAssistantApplication`, because `ApplicationModules.of(...)` treats every
direct sub-package as a module — a `…jobassistant.web` package would be detected as a module in its
own right and stand alongside `analysis` and `document`.

## Skill resolution

Everything downstream refers to `canonical_skill.id`, never free text. `SkillNormalizer` collapses
spellings onto a lookup key (`React.js`, `react js`, `REACTJS` → `reactjs`), expanding `+` and `#`
first so `C`, `C++` and `C#` stay distinct.

**Accented letters are folded onto their base letter, not deleted**, so a Polish alias can key the
same as its unaccented spelling — offers come from a Polish board, so this is the difference between
`Współpraca` resolving and never matching anything. The final step is still an ASCII allowlist, so
folding has to happen first. **`ł` is the trap**: `ą ć ę ń ó ś ź ż` all decompose under NFD and
survive the filter as their base letter, but `ł` (U+0142) has no decomposition at all, so NFD alone
looks correct and silently deletes it. `SkillNormalizer.NON_DECOMPOSING` is the explicit table that
covers it. The rule is provably unchanged on ASCII input, which is why the seed's 362 hand-written
`normalized_alias` values and the drift test that guards them needed no edit. The seed migration precomputes the same values;
`SkillCatalogIntegrationTest` fails if the two ever drift.

Terms the extractor can't place go to `unmatched_term` for human review. **Do not let code create
canonical skills automatically** — the review queue is the point.

**The catalog is English-canonical with original-language terms as aliases.** `V17` broadened it to
the QA, BA and PM vocabulary the ingested corpus actually shows demand for, and gave the ten
measured Polish terms English homes (`Komunikacja` → Communication, `Analiza wymagań` →
Requirements Analysis). Two measured terms — `Zarządzanie zespołem` and `Samodzielność` — were
deliberately *not* seeded: they are collapses rather than translations, and a reviewer with a
suggestion in front of them is a better decision surface than a migration author guessing.

**Category choice is a CvInvariant decision, not just a label.** It scans `TESTING` and `TOOL` but
not `PRACTICE`, `OTHER` or `SOFT`, so activity vocabulary (`Test Cases`, `Manual Testing`) is
`PRACTICE`: a tailored CV describing honest work as "wrote test cases" must not be rejected as a
fabricated claim. Named products (`TestRail`, `Salesforce`) stay `TOOL`, where a false claim is
exactly what should be caught. Ask which side a new skill falls on before picking its category.

`SkillCatalog.suggest` / `suggestAll` answer *"what might this be"* and are the one place the
catalog does anything other than a lookup. **Suggestions are candidates for a human and are never
consulted by `resolve`**: `SkillSimilarity` is a pure trigram Dice score over *normalised keys*,
with a containment signal pinned to exactly the threshold so containment earns a hearing and never a
verdict. Scoring the same keys `SkillNormalizer` produces is why `pg_trgm` was rejected — it
tokenises raw text with its own rules, which would give the application two notions of "nearly the
same string", one deciding suggestions and another deciding resolution. Terms under four characters
get no suggestions at all, because `AI` and `Go` match half the catalog. The UI's chips fill the
skill picker and never submit.

`SkillCoverage` expands the held skill set once through the relation graph and is the sole source
of a verdict: held **or** IMPLIES-reachable is `MET`, RELATED-reachable is `PARTIAL`, anything else
is `MISSING`. It carries provenance (`impliedBy` / `relatedBy`), not just a status, so the report
can say "you have Quarkus, they want Spring Boot" instead of showing an unexplained amber light.

## The analysis pipeline

`AnalysisRunner` runs one job end to end on the `analysisExecutor` pool: extract → match →
narrate. **Only steps 1 and 3 call a model.** Step 2 is `RequirementMatcher`, plain Kotlin over
`SkillCoverage`, so the verdict is reproducible and the model never gets a vote on whether the
candidate has a skill.

**Soft skills are reported, not scored.** `RequirementMatcher.scoreable` excludes `SOFT` from the
denominator: a `Communication` must-have the profile lacks belongs in the gap report, but counting it
makes `matchScore` answer a question no catalog lookup can answer. It stays in `requirements`, and
`AnalysisReport.reportedNotScored` names what was left out.

The trap this had to avoid: **`matchScore` is stored while `scoreExplanation` recomputes its
denominator**, so changing the rule silently would make every existing report contradict itself.
The rule is therefore *versioned*, not migrated — `analysis.scoring_rule` holds
`V1_ALL_CATEGORIES` or `V2_SOFT_EXCLUDED`, old rows keep V1 and explain themselves in V1's terms,
and the UI labels them. Historical scores are never recomputed: that would rewrite a number past
decisions were made on, and could not rewrite `summary_md`, leaving prose narrating the old
percentage. `AnalysisRow.scoringRule` defaults to `ScoringRule.CURRENT` because that default only
applies to rows Kotlin constructs — a row loaded from the database keeps what it was scored under.

- States: `PENDING → EXTRACTING → MATCHING → NARRATING → DONE`, or `FAILED` with an error message.
- The pool is deliberately 2 threads / queue 20 — each job costs two model calls, so unbounded
  concurrency means unbounded spend.
- A job orphaned by a restart is marked `FAILED` at startup (`failOrphanedAnalyses`) rather than
  left for a client to poll forever. Keep that property if you touch the lifecycle.
- **An extraction that found no requirements is a failure, not a result.** The gap report is the
  product, so an empty one does not read as "something went wrong" — it reads as *"this offer asks
  for nothing"* or *"you match everything"*, and neither is a claim the application can back.
  Asserting an *absence* of gaps is the same unfounded assertion as inventing experience, just from
  the other side. `EmptyExtractionException` turns it into a `FAILED` run carrying a reason.

  Watch for this shape generally: **an empty denominator reading as success.** Zero claimed bullets
  scored a *perfect* fabrication rate in the eval tier for the same reason, and `CvSelection`'s
  fallback to the whole profile — correct in itself, since an untailored CV beats none — is still
  silent about having happened.

## Market ingestion

`market` pulls job offers from [solid.jobs](https://solid.jobs/api-ofert-pracy) into a corpus that
exists to be counted, not applied to. **No model is involved anywhere in it**: the source states
salary, skills, experience level and validity as structured data, so there is nothing to extract and
nothing to hallucinate. The reasoning is in `docs/research/13-offer-market-dashboard.md`; the source
survey that picked solid.jobs is in `docs/research/10-offer-ingestion-sources.md`.

**A `market_offer` is not a `JobOffer` and must not become one.** A `JobOffer` is something the
candidate might apply to: it carries an `application` lifecycle row, a profile revision, analyses and
generated documents. A market offer is a row in a sample — there are thousands, and folding them
together would put thousands of `SAVED` applications in the offer list and silently change what
`AggregateGapReport.analysedOffers` counts. Saving one for real is an explicit copy.

`market` depends on `catalog` and — on the read side only — `profile`. Ingestion touches neither the
candidate nor a persona: it resolves skill names through `catalog` and nothing else. The `profile`
edge belongs to `MarketInsightsService` alone, which overlays the candidate's `SkillCoverage` onto
the demand table so a row can read MET, PARTIAL or MISSING; an absent persona yields
`SkillCoverage.EMPTY` rather than an error, because a corpus with no profile behind it still has a
meaningful demand table. Keep the edge that narrow — the moment ingestion needs a profile, the
corpus has stopped being a sample and started being about one person.

It must **not** depend on `analysis`: the market-side measure is
plain `SkillCoverage` over an offer's listed skills, deliberately a different number from
`matchScore`, because solid.jobs's only importance signal is a `NiceToHave` value on the skill *level*
field and it appears on 3.4% of mentions. Reusing `matchScore` would mean two numbers with one name.

Skill names resolve through `SkillCatalog.resolveAll`; **unresolved is the normal case**, not an
error — 900 distinct names per 500 offers against a catalog seeded for JVM backend work, many of
them Polish soft skills. They go to `unmatched_term` under `market_occurrences`, a **separate counter** from
`occurrences`, because the queue is ranked by occurrences and one poll would otherwise bury every
term that came from an offer the candidate actually read.

**`market_occurrences` is set from the corpus, never incremented.** After each poll `market` counts
`market_offer_skill` rows with no `canonical_skill_id` — the table is keyed
`(market_offer_id, skill_name)`, so one row is one employer asking — and hands the whole map to
`SkillCatalog.recordUnmatchedFromMarket`, which writes it as the new value. Offers are upserted by
key and the corpus is never pruned, so a daily poll re-serves the same listings: accumulating would
multiply a term's demand by the number of times we happened to look and rank the queue by how long
a term had been listed. Because the number is derived rather than accrued, an unchanged re-poll is
a no-op and `V16` is literally the same computation run once over rows already stored.

The poll is daily and gated by `job-assistant.market.enabled`, which controls the *schedule* only —
`POST /api/market/ingest` works either way. `GET /api/market/ingestion` reports that gate and the
next run the cron implies, so the dashboard can say whether the corpus refreshes itself or only
moves when someone presses the button: the two produce identical numbers and one of them is going
stale unwatched. Every integration test gets a `ScriptedSolidJobsClient`
through `@IntegrationTest`, on the same principle as `ScriptedChatModel`: no test may reach a third
party, whether or not the network is up.

## Model configuration

Providers are configured, not coded. OpenRouter, Requesty, Ollama and LM Studio are all
OpenAI-compatible, so a "profile" under `job-assistant.llm.profiles` is just a base URL, key, model
name and a `strict-schema` flag — there is no provider SPI, and adding one would be a mistake.

`job-assistant.llm.tasks` routes each `LlmTask` (`EXTRACTION`, `NARRATIVE`, `DOCUMENT`) to a profile
by name, so extraction can run on a different provider from narrative writing with no code change.
A task pointing at an undefined profile fails loudly at first use.

**A router's model name is not a model.** OpenRouter serves one slug from many upstream providers
whose capabilities genuinely differ — `minimax/minimax-m3` had 3 of 11 implementing structured
outputs in August 2026, the first-party Minimax endpoint among the eight that did not. And
`response_format` is only a *soft preference*: a request routes to a provider that cannot honour a
JSON schema, which then silently ignores it. `strict-schema: true` therefore means nothing on its
own; `custom-parameters.provider.require_parameters` is what makes it real, by restricting routing
to providers supporting every parameter sent.

Symptoms of getting this wrong are not obviously schema-related, which is why it cost a day: empty
but well-formed responses, prompt-mode identifiers arriving as data, `AiMessage.text()` coming back
null, and the same fixture scoring 1.00 then 0.00 on identical configuration — that last one being
routing non-determinism, not model non-determinism. **`llm_call.serving_provider` records who
actually answered**, so this is now a query rather than an argument.

`custom-parameters` is an opaque `Map<String, Any>` merged into the request body verbatim. Keep it
opaque: typing one router's options would be the first step toward the provider SPI this design
deliberately does not have.

Environment: `OPENROUTER_API_KEY` / `REQUESTY_API_KEY` for models, `DB_URL` / `DB_USER` /
`DB_PASSWORD` for Postgres (defaults match `docker-compose.yml`). Local dev uses the compose
Postgres; deployment targets Neon, which is why the Hikari pool is tiny and tolerant of cold starts.

## What a model call costs

**The provider already tells us, and LangChain4j already hands us the answer.** OpenRouter and
Requesty both return `usage.cost` inline on every non-streaming completion, with no request flag.
LangChain4j's parsed types drop it — they are provider-neutral on purpose, so `Usage` has no `cost`
field for Jackson to fill — but `OpenAiChatResponseMetadata.rawHttpResponse()` is populated
unconditionally, and `AuditingChatModelListener` has been reading that body since `serving_provider`
landed. `completionMetadataIn` parses it once for the provider, the generation id, the cost and the
cached/reasoning token split. It is best-effort throughout: absent is the normal case, and a
malformed body must never cost the audit row, because a missing cost is worth far less than a
missing prompt. `docs/research/11-model-call-cost.md` records the verification.

**`llm_call` cannot answer "what have I spent".** `LlmCallRetention` deletes its rows after thirty
days and `V11` cascade-deletes them with their profile, so `sum(cost_usd)` over it is a
thirty-day, surviving-personas-only figure that would render under a label saying "total". The
total therefore lives in `llm_spend_daily` (`V25`), a bucket per day/task/profile/model written in
the same transaction as each audit row and never purged. **Nothing on the read side may consult
`llm_call`** — an integration test deletes every row and asserts the lifetime total is unchanged.

Three properties of that table are decisions, not details:

- **No `profile_id`, no cascade.** It is the one profile-derived table that deliberately outlives
  `V11`'s erasure rule. Deleting a persona does not un-spend the money, and a row of counters holds
  no identifier to erase. The per-call rows, which hold prompt text, are still erased.
- **Accrued, not derived** — the mirror image of `market_occurrences`, which is *set* from the
  corpus precisely because a re-poll re-observes the same offers. A model call happens exactly once
  and can never be re-observed, so incrementing is correct here, and after a purge it is the only
  operation still possible.
- **`priced_calls` is the denominator.** A provider reporting no price still produces a row.
  `$0.40` over 100 calls and `$0.40` over the 60 that were priced are a floor and a total, and only
  one of them is what the label says. Every surface renders the pair.

**The spend cap is an `OutboundPromptInspector`** (`BudgetGuardInspector`), which is that
interface's stated purpose and the one seam every model call passes through — a check at each
pipeline entry point would be three call sites for a fourth caller to forget. It ignores the prompt
entirely and sums the period out of the rollup. It is accurate to within one call by construction,
since a price is only known once the answer is back; overshooting by one call is the honest cost of
refusing to estimate prices before the fact. Both limits default to unset.

`llm_call.subject_kind`/`subject_id` name the offer that caused a call (`LlmCallScope.SUBJECT_OFFER`),
so an application can be priced end to end — analysis, both documents, and any `JsonOutputGuardrail`
repair in between, which shows up as more rows than the pipeline has steps. That pair carries **no
foreign key**, unlike `profile_id`: cost history must survive the deletion of the analysis it paid
for, and a constraint naming another module's table would be the first half of a dependency this
module does not have.

`GET /api/llm/spend/account` asks OpenRouter what the key actually spent, via `GET /api/v1/key` —
`/credits` and `/activity` need a *management* key and answer 403 to an inference key. It carries
the API key and nothing else, so it sits outside the second rule rather than being an exception to
it. It is its own endpoint because a dashboard must render when a third party is down, and the two
figures are shown side by side because **the gap is the feature**: ours is an undercount by
construction.

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

**A default covers a missing key, not an explicit null.** The same reflective deserialisation that
makes defaults necessary also means the constructor is never called, so Kotlin's intrinsic null
checks never run: a model emitting `"requirements": null` produces a genuinely null `List` inside a
property typed non-null. **At the boundary where a service return type is first read, the Kotlin
type is a claim rather than a guarantee** — `?: emptyList()` there is not dead code, and needs
`@Suppress("USELESS_ELVIS")` plus a comment saying so. `CvSelection.from` is the worked example.

**An empty model response must be normalised, not passed on.** A model that emits only `thinking`
returns an `AiMessage` whose `text()` is null. `JsonOutputGuardrail` reprompts correctly, but
LangChain4j 1.19's `OutputGuardrailExecutor.rewriteResult` then compares the reprompted text against
the null original without a null check, throws, and **discards the reprompt that had just succeeded**.
`InspectingChatModel` substitutes `""` on the way back to stop that. Remove it when upstream adds
the check; keep it until then, because no scripted test can produce the case that needs it.

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
- **Never assert a rate without checking the denominator.** A model that claimed one skill and had
  it dropped scores 1.00, identical to one that claimed twenty and lost all twenty — no single-claim
  response can pass any threshold below 1.0, however well the system behaved. Gate a ratio on a
  minimum sample (`MIN_CLAIMS_FOR_A_RATE`) and **record the raw counts alongside every rate**, or a
  future reader cannot tell `1/1` from `9/9` and will misread the failure as fabrication.

## Spring Data JDBC gotchas

**A `default now()` column will not work with a nullable Kotlin property.** Spring Data JDBC
writes every mapped property, so `val createdAt: Instant? = null` sends an explicit `NULL` and
violates the not-null constraint. Set the value in Kotlin instead:

```kotlin
val createdAt: Instant = Instant.now()   // not Instant? = null
```

Composite-key join tables (`skill_relation`, `experience_bullet_skill`, `market_offer_skill`) are
handled either as an owned `@MappedCollection` inside an aggregate or with `JdbcClient` directly —
not as repositories.

**`JdbcClient` is raw JDBC and does not carry Spring Data's converters.** Three things Spring Data
JDBC does silently have to be done by hand there, and all three fail at runtime rather than at
compile time:

- An `Instant` cannot be bound — pgjdbc asks for an explicit SQL type. Pass
  `instant.atOffset(ZoneOffset.UTC)`.
- `jsonb` and `text[]` need a cast in the SQL (`cast(:payload as jsonb)`,
  `array(select jsonb_array_elements_text(cast(:locations as jsonb)))`). `PGobject` is not an
  option: the Postgres driver is `runtime` scope and is deliberately absent from the compile
  classpath.
- A nullable `bigint` read back as `rs.getLong(...)` is `0`, not null. Use
  `rs.getLong(c).takeUnless { rs.wasNull() }`.

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

**The CV layout is "Register", chosen in issue #14 and folded into `cv.html` in #73.** Three of its
rules are load-bearing rather than decorative. Fonts are **base64-embedded with the `latin-ext`
subset** in `templates/fragments/fonts.html`: Chromium embeds whatever it resolved *at render time*,
`PlaywrightDocumentRenderer` calls `setContent` with no base URL so a relative `url(...)` cannot
resolve at all, and `latin` alone carries no ł, ą, ę, ś or ż — a Polish CV falls back mid-word
without it. Skills are grouped by `SkillCategory` in an order declared in `DocumentViews`, never
`SkillCategory.ordinal`. And a role's **skill badges are the union over the bullets that actually
render** — a skill whose only evidence tailoring dropped must not survive into the badge row, which
is `CvSelection`'s rule restated for presentation. Badges are real `<span>` text nodes and never
`::before` content, because `CvInvariant` scans `HtmlText.visibleText`: a skill name that exists
only in CSS is a claim the fabrication guard cannot see.

**A portrait is a direct identifier and takes the same route as the name.** `profile_portrait` is
its own table keyed by `profile_id`, so the blob stays out of `profile_details` — which every
profile read and every CRUD response loads — and cascades away with the persona. `CandidateProfile`
exposes only `hasPortrait`, because that type is what prompt builders read and a boolean cannot leak
a face. The renderer inlines it as a `data:` URI *after* the model has answered, for the same reason
the fonts are embedded: `setContent` has no base URL, so `/api/profiles/1/portrait` would render as
a broken image in the PDF and nowhere else. `ProfilePortraitHttpTest` pins the endpoints and the
cascade; `PromptPrivacyIntegrationTest` asserts no prompt grows by a byte when a portrait exists.
The upload is the application's only multipart endpoint, bounded in `application.yaml`, and its
media type is sniffed from the bytes rather than trusted from the request.

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

**The nine collections are one concept, written once.** Links, skills, experiences, bullets,
education, credentials, projects, consent clauses and languages are all instances of *an ordered,
profile-owned collection whose writes bump the profile revision and answer with the whole profile*.
`ProfileWriteService` implements `add` / `update` / `delete` / `reorder` for all of them; a
`ProfileCollection` descriptor in `ProfileCollections` supplies only what differs — the repository,
the `display_order` table, how a request becomes a row, and the hooks carrying that entity's own
refusals. **`ProfileCollections` is the inventory**: what the profile contains is that file's list
of nine, and a tenth is a descriptor plus its routes rather than another copy of four methods.

Three things follow that are easy to undo by accident. `CollectionOwner` is what a collection's
rows hang from — the profile for eight of them, a work experience or a project for bullets, whose
`display_order` restarts inside each owner. The table and owner-column names are interpolated into
SQL and must stay literals declared on the descriptor, never values reachable from a request. And
which hook a rule sits in decides whether 404 or 409 wins when an unknown id arrives with an invalid
body: `checkUpdate` runs before the lookup and answers 409, `onUpdate` after it and answers 404.
Collections currently disagree about that, and the hooks preserve each one's existing answer rather
than quietly unifying them.

`putDetails` is deliberately outside the mechanism: it is an upsert of a single row, with no id, no
ordering and nothing to name in a 404.

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