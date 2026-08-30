# End-to-end flow

> All of this is also available in the UI: `cd frontend && npm run dev`, then
> <http://127.0.0.1:5173>. See `docs/frontend.md`.

Start Postgres and the app:

```bash
docker compose up -d
export OPENROUTER_API_KEY=sk-or-...
./mvnw spring-boot:run
```

The server binds to `127.0.0.1:8080` only — it is a single-user tool with no authentication.

## 1. Build a profile

Profiles are plural: one persona per target role, e.g. "Java developer" or "Cloud consultant".
Create one first, then fill it in a piece at a time or import a document wholesale.

```bash
# Creates a persona. The first one created becomes the default.
curl -X POST localhost:8080/api/profiles -H 'Content-Type: application/json' \
  -d '{"name":"Java developer"}'
# -> {"id":1,"name":"Java developer","isDefault":true}
```

```bash
# Fills in that profile's details. profileId below is the id from the create response.
curl -X PUT localhost:8080/api/profiles/1/details -H 'Content-Type: application/json' \
  -d '{"fullName":"Alex Novak","headline":"Backend Engineer"}'
```

```bash
# Or seed it in bulk. A full replace: reassigns every id, so stored CVs and analyses read as stale.
curl -X POST localhost:8080/api/profiles/1/import \
  -H 'Content-Type: application/json' --data @docs/sample-profile.json
```

Import rejects with HTTP 400 listing any skill name the catalog does not know. Add missing ones
first:

```bash
curl -X POST localhost:8080/api/catalog/skills -H 'Content-Type: application/json' \
  -d '{"name":"Apache Iceberg","category":"DATABASE","aliases":["Iceberg"]}'
```

See `docs/profile-format.md` for the document format.

### Managing profiles themselves

```bash
curl localhost:8080/api/profiles                    # list, each with isDefault
curl -X PUT localhost:8080/api/profiles/2/default    # make profile 2 the default
curl -X DELETE localhost:8080/api/profiles/2         # 409 if it's the default and another still exists
```

### Per-entity editing

Every collection has the same four operations, one path segment under the profile. `{coll}` is one
of `links`, `skills`, `experiences`, `education` or `languages`.

| Method | Path | |
|---|---|---|
| `POST` | `/api/profiles/{profileId}/{coll}` | 201 |
| `PUT` | `/api/profiles/{profileId}/{coll}/{id}` | full-entity body, not a patch |
| `DELETE` | `/api/profiles/{profileId}/{coll}/{id}` | |
| `PUT` | `/api/profiles/{profileId}/{coll}/order` | `{"ids":[...]}`, must name every id exactly once |

Bullets hang off a role but are addressed on their own, so their ids survive edits to the role:

```
POST   /api/profiles/{profileId}/experiences/{experienceId}/bullets
PUT    /api/profiles/{profileId}/experiences/{experienceId}/bullets/order
PUT    /api/profiles/{profileId}/bullets/{id}
DELETE /api/profiles/{profileId}/bullets/{id}
```

Writes name skills by **catalog id**; only the import document uses names and aliases. Every
mutation answers with the whole `CandidateProfile`, so there is nothing to reassemble client-side.
A skill or language is unique **within** a profile, not globally — two profiles can each hold
Kotlin, or each declare English at a different level.

Failure shapes, all RFC 7807 `ProblemDetail`:

| Status | When | Extensions |
|---|---|---|
| 400 | blank required field | `fieldErrors` |
| 400 | import naming an unknown skill | `unresolvedSkills`, `undeclaredBulletSkills` |
| 404 | unknown id | |
| 409 | skill already held, language already listed, role ending before it starts, partial reorder | |
| 409 | deleting a skill that bullets still cite | `blockingBullets` |
| 409 | deleting the default profile while another one exists | |

### Staleness

`CandidateProfile.revision` counts writes to the profile. An analysis and a generated document each
record the `profileRevision` they were produced from; when it trails the current one, the output has
been overtaken by an edit. It is not wrong — the stored HTML was true when written — but a gap
report recommending a skill you have since added is worth flagging, and the UI does.

## 2. Paste an offer

```bash
curl -X POST localhost:8080/api/offers -H 'Content-Type: application/json' \
  -d '{"text":"Senior Kotlin Engineer ...","sourceUrl":"https://example.com/job/1"}'
```

`201` for a new offer, `200` with `"deduplicated": true` if that text was already stored.

## 3. Analyse it

Offers stay unowned by a profile — only the analysis is profile-scoped, via `?profileId=`.

```bash
curl -X POST 'localhost:8080/api/offers/1/analyses?profileId=1'   # 202 + {"analysisId": 1}
curl localhost:8080/api/analyses/1                                # poll until state is DONE or FAILED
```

`GET .../analyses/latest` and `GET /api/analyses/aggregate` take the same `?profileId=` optionally,
defaulting to the default profile when omitted.

States run `PENDING → EXTRACTING → MATCHING → NARRATING → DONE`. A job interrupted by a restart is
marked `FAILED` at startup rather than left polling forever.

The report contains, per requirement: `importance` (MUST_HAVE / NICE_TO_HAVE), `status`
(MET / PARTIAL / MISSING / UNRESOLVED), and `evidence` naming the profile record behind a
MET or PARTIAL. `matchScore` and `scoreExplanation` cover must-haves only.

`UNRESOLVED` means the catalog could not place the phrase — a gap in the catalog, not in you.
Those land in the review queue:

```bash
curl localhost:8080/api/catalog/unmatched
curl -X POST 'localhost:8080/api/catalog/unmatched/3/approve?skillId=42'
```

## 4. Generate documents

`profileId` is required here too, and must match the profile the analysis being tailored against
was run for — `document/internal/JdbcDocumentService` looks up the latest analysis for that same
`(offerId, profileId)` pair, so a document can never be tailored against one profile's analysis
while reading another profile's skills.

```bash
curl -X POST 'localhost:8080/api/offers/1/documents?profileId=1&type=CV&language=English'
curl -X POST 'localhost:8080/api/offers/1/documents?profileId=1&type=COVER_LETTER'

curl localhost:8080/api/documents/1/html -o cv.html   # the preview
curl localhost:8080/api/documents/1/pdf  -o cv.pdf    # Chromium render, identical
```

A `422` with `fabricatedClaims` means the model tried to put a technology on the page that your
profile does not contain. Nothing is stored in that case — regenerate.

## 5. Track and learn

```bash
curl -X PUT localhost:8080/api/offers/1/status -H 'Content-Type: application/json' \
  -d '{"status":"APPLIED","appliedOn":"2026-08-23","notes":"Referred by a friend"}'

curl 'localhost:8080/api/analyses/aggregate?profileId=1'   # what to actually learn, for that persona
```

The aggregate counts each offer once, using its most recent completed analysis, and ranks by
must-have gaps. This is the number that should drive a learning plan — a single offer only tells
you about one job.

## Debugging a bad report

Every model call is recorded:

```bash
curl 'localhost:8080/api/llm/calls?limit=20'   # task, model, tokens, cost, latency, error
curl localhost:8080/api/llm/calls/7            # full prompt and raw response
```

## What it cost

```bash
curl 'localhost:8080/api/llm/spend?days=90&bucket=WEEK'   # summary, series, breakdowns
curl localhost:8080/api/llm/spend/account                 # what the provider says the key spent
```

Two things to know before reading either number.

`/api/llm/spend` never touches `llm_call`. That table is purged after thirty days and
cascade-deleted with its profile, so a total read from it would shrink over time while still being
labelled a total. Everything here comes from `llm_spend_daily`, which is written in the same
transaction as each audit row and never purged.

**Every figure carries `pricedCalls` beside `calls`.** A provider that reports no price still
produces a row, so a `costUsd` whose `pricedCalls` is below its `calls` is a floor rather than a
total. Anything rendering the money renders that pair.

`/api/llm/spend/account` is a separate request because it is an outbound call to the provider — a
dashboard that cannot render until a third party answers goes down when they do. It reads
`GET /api/v1/key` on OpenRouter, which works with the inference key already configured (`/credits`
and `/activity` need a management key and answer 403). A failure comes back as
`available: false` with a reason, never a 5xx: for a profile pointed at a local model there is no
account to report on. The two figures are meant to be read side by side, and **the gap is the
point** — ours holds nothing from before cost capture existed and nothing spent on the same key by
anything else.
