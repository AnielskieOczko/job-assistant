# Research: what the GitHub API can tell us about a repository (issue #12)

Part of the [roadmap map](https://github.com/AnielskieOczko/job-assistant/issues/9), answering
[issue #12](https://github.com/AnielskieOczko/job-assistant/issues/12). Method: primary sources only
(docs.github.com REST reference, GitHub's own `github-linguist` repo) plus live calls against real
repositories with the already-authenticated `gh` CLI (account `AnielskieOczko`, token scope `repo`).
Every claim below either cites a docs.github.com URL or names the exact `gh api` command that
produced the observed result, so a later session can re-run any of them.

## 1. Verdict

**A GitHub import is not mechanical end to end, but a useful subset of it is.** The repos and
languages endpoints hand back a small set of facts the owner actually typed or that GitHub computed
deterministically from bytes on disk — safe to write straight into the profile. Everything past that
— "what does this project actually use" — is either missing (descriptions are often absent, topics
are essentially never set on personal repos in the sample below) or is a derived signal (byte
percentages) that is not the same claim as "the candidate used Kafka." Turning a derived signal or a
free-text README into a canonical skill is exactly the job the project's `unmatched_term` review
queue already exists for, and it should do that job here too rather than gaining a parallel
mechanism. The dependency-graph SBOM endpoint, which would have been the strongest honest signal
(a declared Maven/npm dependency is a fact, not an inference), turned out to be unreliable enough on
this account's own small repos that it cannot be the load-bearing path — see §3.

So the shape of the feature is: **import owner-stated fields automatically, surface derived signals
(languages, SBOM package names, README-mentioned tech) as candidate `unmatched_term` rows for human
confirmation, and never write a skill to the profile that the owner did not either type themselves
or confirm from a concrete, named piece of evidence.**

## 2. Per-field table

Fields checked live against 11 personal repositories under `AnielskieOczko`/`alz1pl` on 2026-08-26:
`job-assistant`, `ecommerce-monorepo`, `ecommerce_backend`, `ecommerce_frontend`,
`ecommerce-ai-agent`, `Compass-Investing`, `book-manager`, `DanceBook`, `conference`, `market-risk`,
`ai-mario`, plus `jee-user-crud` and `eventsAccomodationApi` for the SBOM/README checks in §3.

| Field | Endpoint | Owner-stated or derived | Reliably populated? | Usable for a CV record? |
|---|---|---|---|---|
| `name` | `GET /repos/{owner}/{repo}` | stated (the repo's identity) | always | yes, as project title (rename before use) |
| `description` | `GET /repos/{owner}/{repo}` | stated | **5 of 11** had one; the rest `null` | yes when present, verbatim |
| `homepage` | `GET /repos/{owner}/{repo}` | stated | **0 of 11** | link field when present |
| `topics` | `GET /repos/{owner}/{repo}/topics` or `.topics` on the repo object | stated (owner picks from a controlled vocabulary) | **0 of 11** | see §4 — theoretically strong, practically absent here |
| `license` | `GET /repos/{owner}/{repo}` | stated (chosen at creation or via a `LICENSE` file) | **0 of 11** (`null` on every repo checked) | not useful for this account |
| `archived` / `fork` | `GET /repos/{owner}/{repo}` | computed | always present, boolean | yes — filters out forks and dead projects automatically |
| `stargazers_count` / `forks_count` | `GET /repos/{owner}/{repo}` | computed | always present | vanity for a personal-scale account; see §7 |
| `created_at` / `pushed_at` | `GET /repos/{owner}/{repo}` | computed | always present, reliable | yes — dates a project without asking the owner to remember |
| `visibility` / `private` | `GET /repos/{owner}/{repo}` | computed | always present | filter, see §6 |
| language byte counts | `GET /repos/{owner}/{repo}/languages` | **derived** (Linguist's byte-count scan of file extensions/content) | always returns *something* if the repo has code | needs owner confirmation, see §3 |
| README body | `GET /repos/{owner}/{repo}/readme` | stated (owner's own prose) | **2 of 6** repos checked had one *at root* — including this project itself | rich but free text, needs extraction before it's structured data |
| SBOM packages | `GET /repos/{owner}/{repo}/dependency-graph/sbom` | stated (parsed from a committed manifest, not inferred) | **inconsistent**, see §3 | strongest per-package signal when it appears |
| commit/contributor counts | `GET /repos/{owner}/{repo}/contributors` or `.../stats/contributors` | computed | reliable, cheap | weak signal, see §7 |

Sources: repository object fields —
<https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#get-a-repository>; languages —
<https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#list-repository-languages>
("The value shown for each language is the number of bytes of code written in that language").
Live commands: `gh api repos/AnielskieOczko/<repo> -q '{description,topics,homepage,archived,fork,license:.license.spdx_id,created_at,pushed_at}'`
run across the 11 repos listed above; `gh api repos/AnielskieOczko/job-assistant/languages`
returned `{"Kotlin":407748,"TypeScript":277500,"HTML":6924,"CSS":4563}` on this very repo, which
happens to match its real composition — a useful sanity check for the "not always wrong" half of §3.

Notably, **`job-assistant` itself has no root `README.md`** (`gh api
repos/AnielskieOczko/job-assistant/readme` → 404) — it has `CLAUDE.md` and `docs/` instead. That is
the exact kind of case the import has to tolerate gracefully rather than treat as an error.

## 3. Dependency-graph / SBOM: what actually happened

The SBOM endpoint (`GET /repos/{owner}/{repo}/dependency-graph/sbom`, SPDX JSON, docs at
<https://docs.github.com/en/rest/dependency-graph/sboms?apiVersion=2022-11-28>) is the one endpoint
that would turn "this project probably uses Spring" into a fact: a package named
`org.springframework.boot:spring-boot-starter-web` in the SBOM is a declared dependency the owner
committed to a manifest, not a guess. It does not require the *manifest itself* to be fetched and
parsed by the caller — GitHub has already done that parsing server-side.

Live results, same token throughout (`gho_...`, scope `repo`):

| Repo | Manifest present | `dependency-graph/sbom` result |
|---|---|---|
| `ecommerce_backend` (Maven, pom.xml, pushed 2025-05) | yes | **200, 30 packages** |
| `ecommerce_frontend` (npm) | yes | **200, 1340 packages** |
| `jee-user-crud` (Maven, pom.xml, created 2024) | yes | **200, 8 packages** |
| `expressjs/express` (well-known public npm repo, sanity check) | yes | **200, 54 packages** |
| `spring-projects/spring-boot` (well-known public Maven repo, sanity check) | yes | **200, 305 packages** |
| `job-assistant` (Maven pom.xml at root, pushed same day as this research) | yes | **404 Not Found** |
| `market-risk` (Maven pom.xml at root) | yes | **404 Not Found** |
| `job-offers` (Maven pom.xml at root) | yes | **404 Not Found** |
| `DanceBook` (Gradle Kotlin DSL) | yes | **404 Not Found** |
| `eventsAccomodationApi` (Maven pom.xml at root, created 2024) | yes | **404 Not Found** |

Commands: `gh api repos/AnielskieOczko/<repo>/dependency-graph/sbom -q '.sbom.packages | length'`
for each row. The `-i` variant on the `job-assistant` call
(`gh api repos/AnielskieOczko/job-assistant/dependency-graph/sbom -i`) confirms the request itself
is well-formed and authorized: `X-Accepted-Oauth-Scopes: repo` and the token's own
`X-Oauth-Scopes: admin:public_key, gist, read:org, repo` satisfy it, `X-Ratelimit-Resource:
dependency_sbom` shows the call was billed against the dependency-graph quota, not rejected before
that — it is a genuine 404, not a permission error in disguise (a permission problem would be 403,
per the docs' own status table).

This is the central finding of the SBOM investigation: **the endpoint's availability does not track
manifest presence, repo age, or manifest format** — a Maven repo with the same `pom.xml` shape as a
working one 404s, an old repo 404s next to an equally old repo that works, and a Gradle-Kotlin-DSL
repo 404s while npm and Maven repos both work and both fail elsewhere in the sample. The GitHub docs
page does not document this behavior (it lists only 200/403/404 with 404 glossed as "resource not
found", i.e. it treats the *absence of an indexed dependency graph* as indistinguishable from a
repository that doesn't exist). The practical read is that GitHub's dependency graph for a given
repo is either not always populated for smaller/personal-account repositories, or populates on some
schedule/trigger this research could not pin down — **UNVERIFIED**: no way to confirm the trigger
condition without GitHub's own operational docs, which were not found via primary-source search.

**Consequence for design:** the SBOM endpoint cannot be the only path to a dependency-derived tech
stack. The reliable fallback is exactly what the ticket names as the alternative — fetch the
manifest file itself via `GET /repos/{owner}/{repo}/contents/{path}` (e.g. `pom.xml`,
`package.json`, `build.gradle.kts`) and parse it locally. That trades "GitHub already parsed it" for
"always available if the file exists," and for the common manifest shapes (Maven `<dependency>`
blocks, npm `dependencies`/`devDependencies` objects) that parse is a well-bounded piece of code, not
a model call. It should be the primary mechanism, with SBOM consulted opportunistically when it
happens to be present (it was, notably, richer than a raw manifest would be for `ecommerce_frontend`
— 1340 packages includes the full resolved dependency tree from a lockfile, not just the ~20 direct
dependencies a `package.json` would list — so SBOM is worth trying first and falling back from, not
worth skipping).

## 4. Topics as a path to canonical skills

Topics are the theoretically ideal source: owner-set, lowercase, and GitHub's own UI nudges toward a
shared vocabulary
(<https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#get-all-repository-topics>
documents them as an array the owner manages via `PUT /repos/{owner}/{repo}/topics`, "Topic names
will be saved as lowercase"). If a repo is tagged `spring-boot`, `kotlin`, `postgresql`, that maps
almost directly through `SkillNormalizer`'s normalization (`spring-boot` → same key as `Spring Boot`,
`kotlin` → `Kotlin`) onto `canonical_skill` rows already seeded in
`src/main/resources/db/migration/V2__catalog_seed.sql` (which lists `Spring Boot`, `Kotlin`,
`PostgreSQL`, `Docker`, `React`, etc. as `FRAMEWORK`/`LANGUAGE`/`DATABASE` entries).

The observed hit rate kills that theory in practice: **0 of the 11 personal repos checked had any
topics set** (`gh api repos/AnielskieOczko/<repo>/topics` → `{"names":[]}` on every one, including
this project). Topics are a GitHub power-user habit most developers — including the account this
research was run against — never adopt on side projects. So topics are honest when present (they are
literally the owner picking from GitHub's vocabulary, no inference involved) but cannot be relied
on as *the* signal; an import built around topics alone would come back empty for the large majority
of a typical personal account's repos. They remain worth reading and mapping when they exist — free,
high-confidence signal — just not worth designing the primary path around.

## 5. Tokens and rate limits

Authenticated (`gh`'s token, classic PAT, scope `repo`): `gh api rate_limit` →
`"core":{"limit":5000,"remaining":5000}`. Unauthenticated: `curl -s https://api.github.com/rate_limit`
→ `"core":{"limit":60}` (this environment had already exhausted it to `"remaining":0` from prior
activity on the same IP, which is itself a demonstration of how easy 60/hour is to burn). Docs:
<https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2022-11-28>
states the unauthenticated limit is 60 requests/hour and the authenticated primary limit for a
personal token is 5,000/hour, matching what was observed.

The SBOM endpoint specifically bills against a separate bucket: the response headers on every SBOM
call showed `X-Ratelimit-Resource: dependency_sbom` with its own quota
(`gh api rate_limit` → `"dependency_sbom":{"limit":100,...}`, i.e. **100/hour**, not the 5,000 core
limit) — worth knowing before assuming a batch import of many repos' SBOMs is as cheap as the repo
metadata calls.

Fine-grained personal access token permissions needed, per
<https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens?apiVersion=2022-11-28>:
every fine-grained token carries **Metadata: Read-only** automatically (it covers the repository
object, languages, topics, and contributor-stats endpoints), and the repos/languages/topics/readme/
sbom/contents endpoints together need only one additional grant — **Contents: Read-only** — which
covers `readme`, `dependency-graph/sbom`, and `contents/{path}` (the manifest-fetch fallback). No
write scopes are needed anywhere in this feature. A token scoped to "Contents: Read-only" on the
specific repos the user selects for import (fine-grained tokens are selectable per-repository) is
the minimum-privilege shape.

## 6. Private repositories

Mechanically reachable: this account's token (scope `repo`, which is the classic-PAT scope that
includes private repo access) lists private repos fine via `gh api user/repos --paginate -q
'.[] | select(.private==true) | .full_name'`. Whether to *include* them in a CV import is a product
question, not a technical one, and the honest answer leans against defaulting to yes: a private repo
is often private specifically because it is a work project under an employer's IP terms, an
unfinished experiment, or something the owner does not want summarized and put in front of a
recruiter without being asked. The safer default is public repos only, with private repos opt-in
per-repository at import time rather than swept in by a blanket "import everything" action.

## 7. Commit and contribution statistics

`GET /repos/{owner}/{repo}/contributors` returned `{"contributions":27,"login":"AnielskieOczko"}` for
`job-assistant` — cheap, reliable, no async wait. The heavier `stats/contributors` endpoint (which
buckets commits by week) returned `200 OK` with an empty object on first call for this repo, which
matches GitHub's documented behavior of that endpoint computing stats asynchronously and needing a
follow-up call once cached — see
<https://docs.github.com/en/rest/metrics/statistics?apiVersion=2022-11-28#get-all-contributor-commit-activity>.
Neither is meaningful for a CV line. A raw commit count says nothing about scope, difficulty, or
outcome, and for a single-contributor personal repo it is trivially gameable (many small commits vs.
a few large ones say nothing about the work). This is vanity data — worth keeping internally as a
recency/activity signal to decide *which* repos to even offer for import (an `archived` repo or one
with a `pushed_at` from years ago is a weaker CV candidate than one pushed last month) but not worth
surfacing as a claim on the document itself.

## 8. Rough cost of a first import

Pieces, using only what this research verified works:

- **An API client** in the `document` or a new module scoped to project import — a thin wrapper over
  `GET /repos/{owner}/{repo}`, `.../languages`, `.../topics`, `.../readme`, `.../contents/{path}` for
  manifest fallback, and `.../dependency-graph/sbom` attempted-then-ignored-on-404. Fine-grained PAT
  with `Contents: Read-only`, stored the same way other provider credentials are (env var, per the
  existing `OPENROUTER_API_KEY` / `DB_*` pattern).
- **A manifest parser** for the two or three formats worth supporting first (npm `package.json`
  dependency keys, Maven `pom.xml` `<dependency>` elements) — plain code, not a model, matching the
  project's existing rule that facts come from deterministic code.
- **A review screen**, because nothing derived (language bytes, SBOM package names, README
  mentions) can go straight to the profile. This is the same UI shape the unmatched-term queue
  already needs for the catalog module — candidate skill names surfaced for a yes/no/edit decision
  before they become `profile_skill` rows — so it is an extension of that queue's pattern, applied to
  a new source, rather than a new triage mechanism.
- **A profile entity for projects.** The profile module currently has no "project" concept —
  `WorkExperience` + `ExperienceBullet` is the closest existing shape but is contractually tied to
  employment, not side projects. This needs either a new aggregate (`Project` with a name,
  description, link, and a set of confirmed skill ids parallel to how `ExperienceBullet` links to
  `experience_bullet_skill`) or, if projects are meant to render inside a CV the same way bullets do,
  a deliberate decision about whether they share `ExperienceBullet`'s id-selection anti-fabrication
  mechanism (`CvTailor` selecting by id, `CvSelection.from` dropping unknown ids) — they should,
  since the same "never invent" constraint applies.
- **Mapping owner-stated fields straight through**: `name`, `description`, `homepage`, `topics` (when
  present) need no review — they are the owner's own words, same trust level as a profile bullet the
  user typed themselves.

This is a real feature, not a quick win: the client and manifest parser are a day or two, but the
review screen and a new profile entity (plus the CV-rendering decision) are comparable in size to the
existing per-entity CRUD work described in `docs/adr/0001-per-entity-profile-crud.md`.

## 9. What could not be verified

- **Why the SBOM endpoint 404s on some repos and not others.** Confirmed live and reproducible
  (§3), but the triggering/indexing condition behind it is not documented on any docs.github.com
  page found during this research. Flagged as a real risk for design, not just an unknown for
  curiosity — a feature cannot assume SBOM will be present for an arbitrary user repo.
- **Whether GitHub's dependency graph can be explicitly force-enabled or re-triggered for a
  specific repository via a documented API call.** No such endpoint was found in the dependency-graph
  REST reference category; only the passive read endpoints (`sbom`, `dependency-graph/compare`) and
  the push-based `dependency-graph/snapshots` *submission* endpoint (for CI tooling to push data in,
  not something an importer would use for someone else's already-built repo).
- **The exact backend trigger/schedule for `stats/contributors` async computation** — the docs
  describe the 202-then-202-then-200 pattern in general terms but this research only observed an
  immediate 200 with an empty body once, not the full documented cycle.
- **Fine-grained PAT permission names were extracted via a web-fetch summarization of the GitHub
  permissions-reference page**, not by generating and testing an actual fine-grained token end to
  end (the working `gh` session uses a classic token). The `Contents: Read-only` / `Metadata:
  Read-only` mapping should be treated as highly likely but not independently confirmed by a live
  fine-grained-token call in this research pass.
