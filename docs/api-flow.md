# End-to-end flow

Start Postgres and the app:

```bash
docker compose up -d
export OPENROUTER_API_KEY=sk-or-...
./mvnw spring-boot:run
```

The server binds to `127.0.0.1:8080` only — it is a single-user tool with no authentication.

## 1. Seed the profile (once)

```bash
curl -X POST localhost:8080/api/profile/import \
  -H 'Content-Type: application/json' --data @docs/sample-profile.json
```

Rejects with HTTP 400 listing any skill name the catalog does not know. Add missing ones first:

```bash
curl -X POST localhost:8080/api/catalog/skills -H 'Content-Type: application/json' \
  -d '{"name":"Apache Iceberg","category":"DATABASE","aliases":["Iceberg"]}'
```

See `docs/profile-format.md` for the document format.

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
