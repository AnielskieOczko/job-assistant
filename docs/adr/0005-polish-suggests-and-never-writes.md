# 5. Profile polish is its own module, and it suggests rather than writes

Date: 2026-09-04

## Status

Accepted.

## Context

Issue #81. The profile is hand-authored ground truth and its prose is the input to every CV ever
generated from it, so a weak project description weakens every tailored CV that selects it. Nothing
helped write one. The feature is a per-field "polish with AI" action in the profile editor.

It runs straight at the rule that governs the whole design: **the AI must never be able to invent
experience the candidate does not have**, and the first bullet under it is *"the profile is
hand-authored ground truth in Postgres. No model writes to it."* A feature whose entire purpose is
to put model output into that table has to answer that sentence, not work around it.

Two questions had to be settled before any of it could be built: where the code lives, and what the
model is allowed to cause.

## Decision

### The model suggests; the candidate's accept is the write

`ProsePolishService` has no repository, no `JdbcClient` and no write path of any kind. It returns a
`PolishSuggestion` over HTTP and stops. The profile changes when the candidate presses **Use this**
and then **Save**, which sends the same `PUT /api/profiles/{id}/projects/{id}` the edit dialog has
always sent. *"No model writes to the profile"* stays literally true — as a structural fact about
the module graph rather than a promise made in a prompt.

This is `LlmTask.TRIAGE`'s discipline, whose own comment states it: *"Never authoritative … a human
still clicks approve."*

### `polish` is a module, not a feature of `profile`

The obvious home is `profile`, and it is the wrong one. `profile` is depended on by `analysis`,
`document` and `market`; a `profile → llm` edge would put the model factory, the audit listener and
the provider configuration into the transitive closure of all of them, to serve one endpoint. That
is ADR-0003's argument for `catalog` in a new place, and ADR-0004's for `offer`.

So `polish` depends on `profile`, `catalog`, `llm` and `privacy`, and nothing depends on `polish`.
Unlike ADR-0004's promotion, this genuinely is a join — it needs the profile's held skills *and* the
catalog *and* a model — which is the same shape that made `triage` a module rather than an edge.

### The suggestion is scanned before it is shown, and flagged rather than refused

`CvInvariant`'s reading — *this text names a catalog skill the profile does not hold* — was welded
to document rendering. It is now `SkillMentions` in `catalog`, and both callers run it.

What is **not** shared is the consequence. A CV naming Kubernetes is thrown away with a 422; a
suggestion naming Kubernetes comes back with `unheldSkills: ["Kubernetes"]` and is shown anyway. The
difference is who is about to read the text. An employer is reading the CV, so an unbacked claim
there is the failure the guard exists to prevent. The candidate is reading the suggestion, and *"I
should add Kubernetes to my skills"* is a legitimate answer to the flag — as is deleting the word.
Refusing here would hide a suggestion from the only person able to judge it.

`CvInvariant` therefore stays a hard refusal and is not softened to match.

### The prompt is built from the field, never from the entity

`ProsePolisher` receives one field's text and a description of what that field is. Not the project's
URL, not the employer, not the dates, not the profile id. This is not tidiness: `JdbcProfileService`
folds project URLs into `linkUrls` because `github.com/AnielskieOczko` names the candidate as surely
as an email does, so a prompt built from a `Project` would be **refused outright** by
`ProfileIdentityInspector` (ADR-0002). Building from the field is what makes the call possible at
all.

### A fifth `LlmTask`

`POLISH` is routable in `job-assistant.llm.tasks` like the other four, so rewriting one sentence can
point at a cheap model without touching extraction, and so a habit of polishing every field shows up
as its own line in `llm_spend_daily` rather than inflating `DOCUMENT`.

## Consequences

- `catalog` gained `SkillMentions` and still depends on nothing. `CvInvariant` is a delegate whose
  tests were not edited — if the extraction had changed behaviour, that suite would have failed
  without having been touched, which is the check worth having.
- Blank text and text over `MAX_TEXT_LENGTH` are refused before a provider is reached. An empty box
  is not a request a model can answer, and a pasted CV in a description field is a request priced
  like an analysis.
- An empty answer is a 422, not an empty pane. Rendered next to the original, an empty suggestion
  reads as *"your text is best left alone"*, which is a judgement no empty response supports — the
  same shape as an empty extraction reading as *"this offer asks for nothing"*.
- There is no bulk form: no "polish this whole profile", no "polish every bullet". Each of those is
  several calls behind one click, and every suggestion has to be read by a person to mean anything.
- The frontend's flag warning narrows the server's list as the candidate edits and can never widen
  it. A client-side reading is not the authority on what is claimed.
