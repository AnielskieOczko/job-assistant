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

## 1. Build the profile

Either create one and edit it a piece at a time, or import a document wholesale.

```bash
# Brings a profile into existence - no document needed.
curl -X PUT localhost:8080/api/profile/details -H 'Content-Type: application/json' \
  -d '{"fullName":"Rafal Jankowski","headline":"Backend Engineer"}'
```

```bash
# Or seed it in bulk. A full replace: reassigns every id, so stored CVs and analyses read as stale.
curl -X POST localhost:8080/api/profile/import \
  -H 'Content-Type: application/json' --data @docs/sample-profile.json
```

Import rejects with HTTP 400 listing any skill name the catalog does not know. Add missing ones
first:

```bash
curl -X POST localhost:8080/api/catalog/skills -H 'Content-Type: application/json' \
  -d '{"name":"Apache Iceberg","category":"DATABASE","aliases":["Iceberg"]}'
```

See `docs/profile-format.md` for the document format.

### Per-entity editing

Every collection has the same four operations. `{coll}` is one of `links`, `skills`, `experiences`,
`education` or `languages`.

| Method | Path | |
|---|---|---|
| `POST` | `/api/profile/{coll}` | 201 |
| `PUT` | `/api/profile/{coll}/{id}` | full-entity body, not a patch |
| `DELETE` | `/api/profile/{coll}/{id}` | |
| `PUT` | `/api/profile/{coll}/order` | `{"ids":[...]}`, must name every id exactly once |

Bullets hang off a role but are addressed on their own, so their ids survive edits to the role:

```
POST   /api/profile/experiences/{experienceId}/bullets
PUT    /api/profile/experiences/{experienceId}/bullets/order
PUT    /api/profile/bullets/{id}
DELETE /api/profile/bullets/{id}
```

Writes name skills by **catalog id**; only the import document uses names and aliases. Every
mutation answers with the whole `CandidateProfile`, so there is nothing to reassemble client-side.

Failure shapes, all RFC 7807 `ProblemDetail`:

| Status | When | Extensions |
|---|---|---|
| 400 | blank required field | `fieldErrors` |
| 400 | import naming an unknown skill | `unresolvedSkills`, `undeclaredBulletSkills` |
| 404 | unknown id | |
| 409 | skill already held, language already listed, role ending before it starts, partial reorder | |
| 409 | deleting a skill that bullets still cite | `blockingBullets` |

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

```bash
curl -X POST localhost:8080/api/offers/1/analyses      # 202 + {"analysisId": 1}
curl localhost:8080/api/analyses/1                     # poll until state is DONE or FAILED
```

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

```bash
curl -X POST 'localhost:8080/api/offers/1/documents?type=CV&language=English'
curl -X POST 'localhost:8080/api/offers/1/documents?type=COVER_LETTER'

curl localhost:8080/api/documents/1/html -o cv.html   # the preview
curl localhost:8080/api/documents/1/pdf  -o cv.pdf    # Chromium render, identical
```

A `422` with `fabricatedClaims` means the model tried to put a technology on the page that your
profile does not contain. Nothing is stored in that case — regenerate.

## 5. Track and learn

```bash
curl -X PUT localhost:8080/api/offers/1/status -H 'Content-Type: application/json' \
  -d '{"status":"APPLIED","appliedOn":"2026-08-23","notes":"Referred by a friend"}'

curl localhost:8080/api/analyses/aggregate   # what to actually learn, across all offers
```

The aggregate counts each offer once, using its most recent completed analysis, and ranks by
must-have gaps. This is the number that should drive a learning plan — a single offer only tells
you about one job.

## Debugging a bad report

Every model call is recorded:

```bash
curl 'localhost:8080/api/llm/calls?limit=20'   # task, model, tokens, latency, error
curl localhost:8080/api/llm/calls/7            # full prompt and raw response
```
