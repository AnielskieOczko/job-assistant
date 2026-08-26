# 2. No personal data reaches a model provider

Status: accepted

## Context

The profile is ground truth about a real person — name, email, phone, location, employers, dates and
free-text achievement bullets. Four model calls go to OpenRouter, and nothing constrained what text
they carried.

An audit of the egress surface found one identifier actually leaving: `ProfileBriefing` opened every
CV and cover-letter prompt with `Name: <full name>`. Nothing consumed it. No prompt referenced the
name, and the model *cannot* return one — `TailoredCv` and `CoverLetter` have no contact fields, and
the rendered header is rebuilt from the database by `CvSelection.from` after the model has answered.
The strongest identifier we hold was being disclosed to a third party for no benefit at all.

Email, phone, links and education were already absent — but only by omission. Nothing stopped a new
prompt from including them.

Separately, `llm_call` stored every prompt verbatim with no retention and no owner: it was the only
profile-derived table that did not cascade from `profile`, so prompt text about a person outlived
their profile.

## Decision

**Direct identifiers never reach a provider; quasi-identifiers still do.**

Name, email, phone and links are withheld. Employers, schools, dates and bullet text continue to be
sent, because selecting and rephrasing a CV is impossible without them. Pseudonymizing employers was
considered and rejected: it needs a mapping layer, risks the model writing "Company A" into prose,
and flattens cover-letter quality for a gain that does not survive the candidate naming the employer
on the CV anyway.

**Enforcement is three layers — minimize, scrub, assert.** The first two mean the third never fires
in practice; the third is what makes it a guarantee rather than a convention.

The check lives in a `ChatModel` decorator (`InspectingChatModel`), applied in
`DefaultAiServiceFactory`. Three alternatives were rejected:

- **LangChain4j `InputGuardrail`** — `InputGuardrailRequest` exposes only `userMessage()`. A system
  prompt that interpolated something sensitive would pass straight through. Verified against
  `langchain4j-core-1.19.0`.
- **Inside `OpenAiCompatibleChatModelRegistry`** — tests replace the whole registry with scripted
  models, so the guard would be absent from exactly the tests meant to prove it.
- **A `ChatModelListener`** — fires alongside the call rather than before it, and cannot reliably
  block. It would also mean the audit listener had already written the offending prompt to
  `llm_call` before the refusal.

**Detection is deliberately narrow.** `location` and individual name tokens are not matched. A hard
refusal is the response to a match, so a check that fired on ordinary text would take the feature
down: `location` is "Poland", and surnames are frequently ordinary words. Those fields are kept out
of prompts by construction instead — `ProfileIdentity` does not even carry `location`, so it cannot
be reintroduced by accident.

**Identifiers are read for every profile on every call**, not for a "current" profile. The guard sits
beneath the AI-service call where the profile is not known, and a thread-local would fail open the
moment a call crossed a thread boundary — which the analysis pipeline does, on `analysisExecutor`.
Checking all profiles cannot leave the guard with nothing to check. With a single user this costs two
indexed reads against a call that takes seconds, so it is not cached; a cache would need invalidating
on every profile write, and stale identifiers here mean checking against a name already changed.

## Consequences

- The name is gone from document prompts at no functional cost. The rendered CV is unchanged.
- A future prompt that interpolates an identifier fails loudly instead of leaking silently.
- `SensitiveDataInPromptException` names fields, never values, because its message reaches
  `analysis.error` and an HTTP problem detail.
- In the analysis flow a refusal surfaces as `FAILED` with the reason, not as a 422 — the pipeline is
  async and no request thread is left to answer.
- `llm_call` gained `profile_id` with a cascade, plus a retention purge. V11 also deletes rows written
  before the guard existed, since they contain names and cannot be redacted in place.
- Offer text sent to the extractor is scrubbed of contact details while `job_offer.raw_text` keeps the
  original. The scrubber is narrow by design: an earlier version matched any three punctuated digit
  groups and ate salary ranges and dates, which are extraction input.

## What this does not do

`generated_document.html` still stores the rendered CV with name and contacts, and
`GET /api/documents/{id}/html` and `GET /api/llm/calls/{id}` remain unauthenticated. That is the
existing single-user, loopback-bound design (`server.address: 127.0.0.1`), not something this change
regressed. Anything that can reach loopback can still read both.

The candidate's name also remains in git history and across test fixtures. Only `docs/sample-profile.json`
carried a real, reachable email address; that is replaced.
