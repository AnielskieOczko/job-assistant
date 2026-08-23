# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

A personal job-search assistant. You paste a job offer, an AI agent extracts its requirements, the
app compares them against a verified profile and produces a transparent skill-gap report, an
improvement plan, and — on demand — a CV and cover letter tailored to that offer.

The full architecture decisions and milestone plan live in
`~/.claude/plans/grill-with-docs-ok-lets-start-sharded-oasis.md`.

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
```

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
- Kotlin compiler runs the `spring` plugin with `-Xjsr305=strict` and `javaParameters=true`
  (LangChain4j reads AI-service parameter names reflectively)
- Spring Data JDBC + Flyway on Postgres. **Not JPA** — aggregates are explicit and entities are
  immutable Kotlin data classes.
- LangChain4j 1.19.0 for LLM work, used **without** its Spring Boot autoconfiguration
- Playwright 1.62.0 (headless Chromium) renders Thymeleaf HTML to PDF
- Spring Modulith 2.1.0 enforces module boundaries

## Boot 4 gotchas that will bite

- Jackson is **3.x** — imports are `tools.jackson.*`, not `com.fasterxml.jackson.*`
- The web starter is `spring-boot-starter-webmvc`
- Testcontainers is **2.x**: `org.testcontainers.postgresql.PostgreSQLContainer`, non-generic, and
  artifacts are prefixed (`testcontainers-postgresql`)
- Spring Modulith is not managed by the Boot BOM; its version is pinned in `<properties>`

## Writing an AI service

Declare the interface in the module that owns the concept (`analysis` owns extraction, `document`
owns CV tailoring), then build it with `AiServiceFactory.create(MyService::class.java, task)`.
The `llm` module never learns about job offers or CVs.

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
single reprompt. Both round trips are audited.

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

## Skill resolution

Everything downstream refers to `canonical_skill.id`, never free text. `SkillNormalizer` collapses
spellings onto a lookup key (`React.js`, `react js`, `REACTJS` → `reactjs`), expanding `+` and `#`
first so `C`, `C++` and `C#` stay distinct. The seed migration precomputes the same values;
`SkillCatalogIntegrationTest` fails if the two ever drift.

Terms the extractor can't place go to `unmatched_term` for human review. **Do not let code create
canonical skills automatically** — the review queue is the point.

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
reject every honest CV.

**The cover letter prompt must never invite naming an absent technology**, even in an honest
negative ("I have not used Kubernetes"). The invariant has no notion of negation, so such a letter
is rejected outright — the prompt and the guard have to agree.

## Adding a migration

Flyway, `src/main/resources/db/migration`, Postgres-specific. Never test against H2. The catalog
seed (`V2`) is generated from a compact definition — if you edit it, keep `normalized_alias`
consistent with `SkillNormalizer` or the drift test will catch you.
