# Frontend

A Vite + React + TypeScript SPA in `frontend/`. It exists because judging whether an analysis
report or a generated CV is any *good* cannot be done by reading `curl` output — quality is a
visual, comparative judgement.

Astro was the original idea (milestone M8 still names it). It was reconsidered because Astro's
advantage is shipping zero JS on static pages, and every screen here is a live dashboard: almost
everything would have been `client:load`, i.e. an SPA wearing an Astro shell.

## Running it

```bash
docker compose up -d
export OPENROUTER_API_KEY=sk-or-...
./mvnw spring-boot:run                    # backend on 127.0.0.1:8080
cd frontend && npm ci && npm run dev      # UI on 127.0.0.1:5173
```

Open <http://127.0.0.1:5173>. Vite is pinned to `host: '127.0.0.1'` because Vite 8 otherwise binds
`[::1]` only, and `127.0.0.1:5173` would refuse connections.

For a production-shaped run — SPA served by Spring from a single jar:

```bash
./mvnw -Pfrontend spring-boot:run         # everything on :8080
./mvnw -Pfrontend clean package           # jar with the SPA inside
```

`./mvnw test` is unchanged and never touches npm.

## Tests

```bash
cd frontend && npm test      # vitest run, the whole suite, no browser
npm run test:watch           # vitest in watch mode
```

Vitest reads `vite.config.ts`, so the suite resolves `@/` and everything else exactly the way the
application does. The `test` block there pins `TZ=UTC`, because half of what is under test is date
formatting and a run in Warsaw would otherwise disagree with a run on a UTC CI box about which day
an ISO instant falls on. `npm test` runs in the `frontend` job in `ci.yml`, after `oxlint` and
`tsc -b`.

**The environment is `node`, and everything tested is a pure function.** There is no jsdom, no
React Testing Library and no component rendering — the suite finishes in well under a second, which
is the property that keeps anyone from skipping it. What is under test today is the logic that was
already extracted out of components for exactly this purpose:

| Module | What it decides |
|---|---|
| `routes/profile/mutations.ts` | `movedIds`, `swappedIds`, `blankToNull` |
| `lib/format.ts` | dates, durations, and the two money formatters |
| `routes/llm/format.ts` | spend bucket labels, priced-call coverage, shares |
| `routes/market/format.ts` | salary bands, demand level mix, plurals, coverage provenance |
| `lib/sentDocuments.ts` | which documents an application recorded as sent, as one list-row label |
| `lib/shortlist.ts` | the offer list's two orderings, the score label, and the scored-of-total caveat |

Two conventions worth keeping. **Test files sit beside their subject as `*.test.ts`** — the Vitest
default, needing no configuration, and picked up by `tsc -b` because `tsconfig.app.json` includes
all of `src`. They never reach the production bundle: nothing imports them, so `vite build` output
is byte-identical with and without them.

And **a test name is a sentence stating the rule**, matching `SkillNormalizerTest` and
`SkillCoverageTest` on the Kotlin side — `a swap by ids that are not both present leaves the order
unchanged`, not `test swappedIds 3`.

The reorder helpers are the ones that carry a real invariant rather than a formatting preference.
A reorder request must name every id of the collection exactly once; the backend answers 409 to
anything else, and `ProfileCrudHttpTest`'s *a partial reorder is a 409 rather than a silent partial
move* is the server-side statement of the same rule. `mutations.test.ts` asserts the permutation
property on every case rather than only the resulting order, so a bug that today surfaces as an
unexplained error toast fails a test instead.

`sentDocumentsLabel` returns `null` rather than a dash when nothing was recorded, and the rule is
worth stating because it is the same one the backend keeps: an application made outside the tool has
no document to name, which is not the same fact as one sent with neither a CV nor a letter. The
caller decides how the absence renders; the helper refuses to turn it into a claim.

There is **no coverage number** for the frontend. Sonar's `frontend/**` coverage exclusion stays in
place: no lcov report is produced or imported, so retiring the exclusion on the strength of four
tested modules would publish a figure rather than a measure.

## Why there is no CORS configuration

Every request path in the client is relative (`/api/...`). There is no base URL and no
`VITE_API_BASE` anywhere:

- **In development** the Vite dev server proxies `/api` and `/actuator` to `http://127.0.0.1:8080`.
  The proxy runs server-side, from the Node process on the same machine, so the browser only ever
  talks to one origin and `server.address: 127.0.0.1` on the backend is untouched.
- **In production** Spring serves the built SPA from `classpath:/static`, so it is same-origin by
  construction.

Introducing a base URL would break both halves and force real CORS configuration. Don't.

The proxy sets a 10-minute timeout on `/api`, because the first PDF request renders through
Playwright, which downloads Chromium on first use.

## Routes

| Route | Screen |
|---|---|
| `/offers` | The shortlist: every offer with its match score, ranked; filters, paste-offer dialog |
| `/offers/:id` | Raw offer text and the application status editor |
| `/offers/:id/analysis` | The gap report — start, poll, read |
| `/offers/:id/documents` | Generate CV / cover letter, preview, PDF |
| `/profile` | View and import the *selected* profile's document |
| `/gaps` | Cross-offer aggregate gap report |
| `/market` | The ingested corpus: scope, salary bands, demand table, offers behind it |
| `/catalog` | Unmatched-term review queue and skill browser |
| `/llm` | Model call audit — prompt, raw response, tokens, cost, latency |
| `/llm/spend` | What the models cost: hero, KPI row, one chart, breakdowns by task/model/profile |
| `/llm/calls/:id` | One call in full. Under `calls/` so it can never compete with `spend` |

## The offer shortlist

`/offers` is a ranking, not a log. `GET /api/analyses/shortlist?profileId=` returns every saved
offer with the score of its latest completed analysis against that persona, so the cross-offer
question — *which of these should I apply to first* — is answered without opening each offer in
turn. It stays a single request: the join is the endpoint's, and resolving an analysis per row from
the browser is exactly what it exists to avoid.

Three rules on that screen are the repository's usual ones, applied to a list:

- **Null is not zero.** An unanalysed offer renders as `—`, never `0%`, and sorts *below* every
  scored one rather than alongside a measured zero. Never measured and measured badly are different
  facts, and a column you are about to sort on is the last place to blur them.
- **A ranking needs a total order.** Both comparators in `lib/shortlist.ts` fall through to the
  offer id, so equally scored offers hold a fixed position instead of reshuffling between renders.
  The server's `ShortlistOrder` applies the same rule, and the two are deliberately identical — the
  client re-sorts rows it already holds so the toggle costs no request.
- **The rows carry their denominator.** `scored` and `total` come back alongside the entries, and
  the page states the shortfall: ten rows built from three analyses is a ranking of three.

A score from `V1_ALL_CATEGORIES` is marked in the cell rather than quietly compared. Historical
scores are never recomputed, so the shortlist is the one screen where the two rules sit side by side.

`keys.shortlist(profileId)` is nested under `keys.offers` on purpose: five places already invalidate
the offer list after a paste, a promotion, a status change or a sent-document mark, and a sibling
key would need every one of them to remember a second one.

## The market dashboard

`/market` (`src/routes/market/`) renders the read side of the `market` module. It is the one screen
whose layout is an argument rather than a preference, and the order is load-bearing: the **scope
line** first, then a single **hero figure**, then the **KPI row**, then exactly **one chart**, then
the **demand table** as the primary surface. A reader who meets a median before they meet its
population has already been misled.

Three rules it enforces, all of them the same rule:

- **Every figure carries its denominator.** `StatTile` requires a caption for this reason — a median
  over eleven offers and a median over four hundred render identically without one.
- **A statistic below its honesty floor renders as words, not as absence.** Under 30 offers in a
  salary group and the tile says "20 offers · too few for a median — the floor is 30"; under 5
  offers behind a per-skill band and the cell says so. Never a greyed-out tile, which reads as
  *still loading* rather than *too few to say*. The floors themselves live on `MarketInsights` in
  Kotlin, so "did this clear the bar" is computed once rather than reimplemented in TypeScript
  where nothing tests it.
- **The unplaced share is stated above everything else.** Roughly a third of what in-scope offers
  ask for is vocabulary the catalog cannot place, which is the ceiling on how complete any ranking
  below can claim to be. `ScopeLine` links it to `/catalog`, where those terms are already queued.

The hero states an **observation** — "48 of 192 in-scope offers ask for CI/CD and you do not have
it" — never a counterfactual like "48 offers you would win". Issue #47 decision 1 records the
measurement that kept the counterfactual out of v1.

The chart is **emphasis, not categorical**: one hue, gaps in the accent and covered skills in gray,
because every bar is the same measure and per-skill hues would encode identity the row label
already carries. There is no legend box — a single series needs none — and status travels as a
labelled badge with an icon in the tooltip and in the table, so identity is never colour alone.

Two orderings are in play in the demand table and the screen says which is which: the **ranking**
runs server-side over every in-scope skill and decides which rows are on the page, while a **column
sort** only reorders the page already fetched. Sorting by offer count under the unmet ranking shows
the most-asked *of the unmet ones*, not the most-asked overall.

`GET /api/market/scope` and `/salary` compare nothing against the profile, so their query keys carry
no profile id; `/demand` and `/offers` do, and theirs do.

`IngestionControls` is the one thing on the screen — in the whole application — that reaches
solid.jobs, so it is a **button and never a page load**: a dashboard that silently polled a third
party on every visit would spend someone else's rate limit to redraw a number that had not moved. It
reads `GET /api/market/ingestion` for the schedule and says which of two states the corpus is in,
because numbers from a corpus polled nightly and numbers from one nobody has touched since March are
indistinguishable on the page. When the schedule is on it gives the next run *and* the cron verbatim
— the time is what a reader wants, the cron is what the scheduler actually runs, and printing only
the interpretation would put a claim on the page that nothing on the page could be checked against.
When it is off it says so and dates the last run, because then staleness is the whole message.

A finished poll reports counts rather than a bare total (`1,493 offers seen, 0 new · 4,160 of 9,318
mentions resolved (45%) · 900 terms for review`) — a re-poll of an unchanged board legitimately
inserts nothing, and "1,493 offers" alone cannot be told apart from a first ingest. **A failed run
still answers 200** carrying its `error`, because the poll is a batch rather than a request a status
code can describe; reading only the HTTP status would report a failure as a success. Success
invalidates the whole `market` prefix plus the unmatched-term queue and `triage`, since ingestion
writes unplaced terms under `market_occurrences` and the triage ranking is ordered by in-scope
demand.

## Polling

There is no SSE or WebSocket endpoint; polling is the only mechanism the API offers.
`useAnalysisPolling` runs a fixed 1.5s `refetchInterval` that returns `false` once the report
reaches `DONE` or `FAILED`. That return value is the *only* stop condition — deliberately, because
an effect clearing an interval gives the bug two places to hide.

The analysis id is pushed into the URL as `?id=` the moment the POST returns, so refreshing
mid-run resumes polling the same job instead of falling back to "not analysed yet".

Reaching `DONE` invalidates `offers` (the server flips the application status to `ANALYZED` inside
`start()`), the offer, the latest analysis, the unmatched-term queue (extraction may have queued
new terms) and the aggregate report.

## The profile switcher

The sidebar carries a profile switcher above the nav (`ProfileSwitcher.tsx`) — one persona per
target role, e.g. "Java developer" or "Cloud consultant". The selection is app-wide: `/profile`,
the analysis tab, the documents tab and `/gaps` all read whichever profile is currently selected,
and every profile-scoped React Query key includes its id so switching never shows cached data for
the wrong persona.

The selection itself lives outside React Query, in a small module-level store backed by
`localStorage` and synced through `useSyncExternalStore` (`hooks/useSelectedProfile.ts`) — there is
no Context provider in this app, and this is the one piece of shared state every screen needs
without prop-drilling it through the route tree. It falls back to the server's default profile
(`ProfileSummary.isDefault`) when nothing is stored yet, or the stored id no longer names a profile
that exists.

Creating, renaming-by-recreating, defaulting and deleting profiles all happen from the switcher
itself against `/api/profiles`; per-entity editing of *one* profile's contents stays on `/profile`
against `/api/profiles/{profileId}/...`.

## Editing the profile

`/profile` is an editor, not a read-only view. It lives in `src/routes/profile/`, one component per
card, and every mutation goes through `useProfileEdit` — which seeds the query cache from the
response rather than refetching, because every profile endpoint answers with the whole
`CandidateProfile`. Every card and dialog takes the selected `profileId` as a prop rather than
reading the switcher itself, so `ProfilePage` is the only place that has to know it changed.

Forms are `useState` plus the seed-during-render idiom used elsewhere in the app; there is no form
library, and per-entity endpoints keep each dialog small enough that adding one would not pay for
itself. Reordering is arrow buttons rather than drag-and-drop, for the same reason.

`StaleProfileNotice` compares a stored `profileRevision` against the *selected* profile's
`CandidateProfile.revision` and is what makes an out-of-date analysis or CV visible rather than
silently wrong.

## Response codes the UI has to handle

| Code | Where | Meaning |
|---|---|---|
| 204 | `GET /api/profiles/{id}` | This profile has no details yet. **Not** 404 — the wrapper must skip `res.json()` on an empty body. |
| 404 | `…/analyses/latest`, `…/documents/latest` | Normal empty state, not an error. `requestOrNull` maps it to `null`. |
| 409 | `POST …/analyses` | The selected profile has no details yet. The first thing a new persona hits. |
| 409 | `POST …/documents` | No completed analysis of this profile to tailor against. |
| 422 | `POST …/documents` | `fabricatedClaims` — the model tried to claim a skill the profile lacks. Nothing was stored. |
| 400 | `POST /api/profiles/{id}/import` | `unresolvedSkills` and `undeclaredBulletSkills`. |
| 400 | any profile edit | `fieldErrors` — field name to message, for inline form errors. |
| 404 | any profile edit | An id not on this profile. |
| 409 | any profile edit | Skill already held, language already listed, role ending before it starts, partial reorder. |
| 409 | `DELETE /api/profiles/{id}/skills/{id}` | `blockingBullets` — the bullets still citing it. Rendered in the confirm dialog rather than closing it. |
| 409 | `DELETE /api/profiles/{id}` | This is the default profile and another one still exists. Set a different default first. |

**Exception handlers in this backend are per-controller, not a `@ControllerAdvice`.** The catalog
and llm controllers have none, so an invalid id there surfaces as a bare 500 with no
`ProblemDetail`. `ApiErrorAlert` degrades to the raw body rather than rendering nothing. Adding a
small `@ExceptionHandler` to `CatalogController` would fix that properly.

## Types

`frontend/src/api/types.ts` is hand-written, not generated. springdoc *does* have a Boot 4 release,
but its Kotlin model converter keys off Jackson **2**'s `jackson-module-kotlin` while this project
uses Jackson 3 — so nullability, the very thing codegen would be for, would likely come out wrong.
The two shapes the UI most needs to get right are invisible to schema generation anyway: the
`{analysisId, state}` body is a raw `Map`, and the ProblemDetail extensions are set at runtime.

`ApiContractTest` (fast tier, no Docker) pins the JSON key set of every wire DTO. **If it fails,
update `types.ts` in the same commit.** It caught one thing worth remembering: Kotlin's `is` prefix
survives serialization, so `WorkExperience.isCurrent` is emitted as `isCurrent`, not `current`.

## Serving the SPA from Spring

`SpaWebConfiguration` lives in the base package, next to `JobAssistantApplication`, because
`ApplicationModules.of(...)` treats every direct sub-package as a module — a `…jobassistant.web`
package would be detected as a seventh module and stand alongside `analysis` and `document`.

It registers a `PathResourceResolver` that falls back to `index.html`, rather than the usual
forwarding controller: a `@RequestMapping` over a dot-free segment plus a catch-all also matches
`/assets/index-a1b2c3.js`, and annotated handlers beat resource handlers, so it would swallow the
app's own bundle. Paths beginning `api/` or `actuator/` return `null` so a mistyped API path 404s
instead of quietly returning HTML that `JSON.parse` will choke on much later.

There is no automated test for this. It can only pass when `src/main/resources/static/index.html`
exists, which is gitignored build output, so the test would be green or red depending on whether
someone ran `-Pfrontend` — worse than no test. Verify by hand after `-Pfrontend clean package`:

```bash
curl -i 127.0.0.1:8080/offers/1/documents   # 200 text/html  (fallback)
curl -i 127.0.0.1:8080/api/nope             # 404 application/json  (not the SPA)
```

## Build output

`vite build` writes to `src/main/resources/static/`, which is **gitignored**. It is build output:
committing it would mean a diff of minified JS on every UI tweak, and `emptyOutDir: true`
guarantees conflicts. The consequence is that a plain `./mvnw package` produces a jar with no UI —
any build meant to run standalone needs `-Pfrontend`.

The bundle is ~640 kB (197 kB gzipped) in one chunk. Code-splitting it would be pointless for a
loopback single-user tool loading over the loopback interface.
