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
| `/offers` | List, filters, paste-offer dialog |
| `/offers/:id` | Raw offer text and the application status editor |
| `/offers/:id/analysis` | The gap report — start, poll, read |
| `/offers/:id/documents` | Generate CV / cover letter, preview, PDF |
| `/profile` | View and import the profile document |
| `/gaps` | Cross-offer aggregate gap report |
| `/catalog` | Unmatched-term review queue and skill browser |
| `/llm`, `/llm/:id` | Model call audit — prompt, raw response, tokens, latency |

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

## Editing the profile

`/profile` is an editor, not a read-only view. It lives in `src/routes/profile/`, one component per
card, and every mutation goes through `useProfileEdit` — which seeds the query cache from the
response rather than refetching, because every profile endpoint answers with the whole
`CandidateProfile`.

Forms are `useState` plus the seed-during-render idiom used elsewhere in the app; there is no form
library, and per-entity endpoints keep each dialog small enough that adding one would not pay for
itself. Reordering is arrow buttons rather than drag-and-drop, for the same reason.

`StaleProfileNotice` compares a stored `profileRevision` against `CandidateProfile.revision` and is
what makes an out-of-date analysis or CV visible rather than silently wrong.

## Response codes the UI has to handle

| Code | Where | Meaning |
|---|---|---|
| 204 | `GET /api/profile` | No profile yet. **Not** 404 — the wrapper must skip `res.json()` on an empty body. |
| 404 | `…/analyses/latest`, `…/documents/latest` | Normal empty state, not an error. `requestOrNull` maps it to `null`. |
| 409 | `POST …/analyses` | No profile yet. The first thing a new user hits. |
| 409 | `POST …/documents` | No completed analysis to tailor against. |
| 422 | `POST …/documents` | `fabricatedClaims` — the model tried to claim a skill the profile lacks. Nothing was stored. |
| 400 | `POST /api/profile/import` | `unresolvedSkills` and `undeclaredBulletSkills`. |
| 400 | any profile edit | `fieldErrors` — field name to message, for inline form errors. |
| 404 | any profile edit | An id not on the profile. |
| 409 | any profile edit | Skill already held, language already listed, role ending before it starts, partial reorder. |
| 409 | `DELETE /api/profile/skills/{id}` | `blockingBullets` — the bullets still citing it. Rendered in the confirm dialog rather than closing it. |

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
