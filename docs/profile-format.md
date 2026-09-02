# Profile document format

The profile is **verified ground truth**. Nothing in it is written by a model, and a generated CV
may only make claims that trace back to a row here.

There are two ways to build one. Day to day, edit it in the UI at `/profile` — every part of the
profile has create, edit, delete and reorder, and `PUT /api/profile/details` brings a profile into
existence from nothing, so an empty database does not force you through this document first. See
[`api-flow.md`](api-flow.md) for the endpoints.

This document format is the **bulk** path: seeding a profile you already have written down, or
moving one between machines. Edit `sample-profile.json` (or your own copy) and import it:

```bash
curl -X POST http://localhost:8080/api/profile/import \
  -H 'Content-Type: application/json' \
  --data @docs/sample-profile.json
```

Import is a **full replace**, not a merge — the document is the profile. It also reassigns every
entity id, which strands the bullet ids that previously generated CVs and analyses cite: they keep
their stored text but start reading as stale. The UI confirms this before replacing an existing
profile.

## The portrait is not part of this document

A profile may carry one optional photograph, and it lives outside the JSON on purpose. The document
is text you can read, diff and keep in version control; a base64 image inside it would be neither.

- Upload it with `PUT /api/profiles/{id}/portrait` as multipart (`file`), remove it with `DELETE` on
  the same path, and fetch it with `GET`. The accepted formats are JPEG, PNG and WebP, capped at
  2 MB, and the type is **sniffed from the bytes** — a mislabelled file is refused with 415 rather
  than stored under the wrong media type.
- The profile JSON carries `hasPortrait: true|false` and never the image. That is deliberate:
  `CandidateProfile` is also what every prompt builder reads, and a boolean cannot leak a face.
- **An import does not touch the portrait.** Import is a full replace of the *document*, and the
  photograph is not in it, so a re-import keeps the photo that was already there. Deleting the
  profile does delete it, by database cascade — a portrait is a direct identifier, so erasure has to
  be guaranteed rather than remembered.
- It never reaches a model. Like the candidate's name, it is added by the renderer from the
  database after the model has answered, and inlined into the CV as a `data:` URI.

## Skill names

`skills[].skill`, `experiences[].bullets[].skills[]`, `projects[].skills[]` and
`projects[].bullets[].skills[]` are catalog names or aliases, not ids.
Any spelling the catalog knows works: `postgres`, `PostgreSQL` and `psql` all resolve to the same
skill. Import fails with HTTP 400 listing every name it could not resolve, rather than silently
dropping it — a dropped skill would vanish from every future gap report.

Per-entity edits are different: those identify skills by **catalog id**, because the picker they
come from resolved the name already. Names and aliases are a property of this document, where a
human is typing them.

If a skill genuinely isn't in the catalog yet, add it first — from the skill picker in the UI, or
directly:

```bash
curl -X POST http://localhost:8080/api/catalog/skills \
  -H 'Content-Type: application/json' \
  -d '{"name":"Apache Iceberg","category":"DATABASE","aliases":["Iceberg"]}'
```

Categories: `LANGUAGE`, `FRAMEWORK`, `DATABASE`, `MESSAGING`, `CLOUD`, `DEVOPS`, `TESTING`,
`FRONTEND`, `AI`, `PRACTICE`, `TOOL`, `SOFT`, `OTHER`.

## Two rules every write enforces

1. **Every skill must resolve** to a catalog entry — by name here, by id for per-entity edits.
2. **A bullet — or a project's own skill badge — may only cite skills the profile declares.**
   Tagging a bullet with `Kubernetes`, or listing it in `projects[].skills[]`, while `Kubernetes` is
   absent from the top-level `skills[]` is rejected — otherwise that skill could reach a CV with
   nothing backing it.

Both are enforced for import and for per-entity editing alike. The second one also runs in the other
direction once deletes exist: removing a skill that bullets still cite is refused with HTTP 409
listing them, rather than quietly untagging the evidence behind a claim.

## Field notes

- `proficiency`: `BEGINNER` | `WORKING` | `PROFICIENT` | `EXPERT`
- `languages[].level`: CEFR `A1`–`C2`, or `NATIVE`. Offers asking for "English B2" are checked
  against this by ordinal comparison, not by a model's opinion.
- `experiences[].endedOn: null` means the role is current.
- `credentials[].kind`: `COURSE` | `BOOTCAMP` | `CERTIFICATION` | `OTHER`.
- `credentials[].expiresOn` must not be earlier than `credentials[].issuedOn` when both are set —
  the same rule `experiences[].endedOn` follows against `startedOn`.
- `projects[]` is side-project evidence — the main thing a career changer has that they can do the
  work — separate from `experiences[]` because it carries none of employment's contractual fields
  (no company, no role title, no "current" date range). `projects[].bullets[]` are the same shape
  and follow the same skill-declaration rule as `experiences[].bullets[]`; `projects[].skills[]` is
  a separate, project-level skill badge rather than derived from the bullets, so a project can name
  its stack even where the bullets themselves don't spell every technology out. `projects[].endedOn`
  must not be earlier than `projects[].startedOn` when both are set. **`projects[].url` is a direct
  identifier** — `github.com/AnielskieOczko/…` names the candidate as surely as an email does — so
  it is never sent to a model; it only reaches the rendered CV straight from the database, the same
  treatment the candidate's name and photo already get.
- `consentClauses[]` is the CV's data-processing consent clause (RODO/GDPR), one per output
  **language**, unique case-insensitively the same way a `languages[]` entry is. It is rendered onto
  a generated CV straight from the database and never sent to a model — a model paraphrasing a
  consent statement would change what is being consented to, so this text is exempt from the
  "rephrase and reorder" licence a tailored CV otherwise has. `consentClauses[].text` may contain
  `{{company}}`, substituted at render time from the offer by plain string replacement; when the
  offer has no company name the placeholder is left visible rather than a made-up employer being
  substituted in. A CV rendered in a language with no matching clause simply omits the section, and
  the generated document records `consentClauseLanguage: null` so the gap is visible rather than
  silent. Ordinary legal text will not collide with a catalog skill, but the field is user-authored
  free text like any other bullet, so `CvInvariant` still scans it — naming a technology in it that
  the profile does not otherwise hold fails the generation exactly as anywhere else on the CV.
- Array order is preserved and becomes the display order on the CV — for **every** collection.
  Before `V8` that was not true of `languages[]`, which was read back alphabetically, and of
  `skills[]`, which happened to work only because a fresh import inserted them in document order.
- A language is unique case-insensitively: `English` and `english` are the same entry.
- `details.careerGoal` is what the candidate is trying to move *toward*, as distinct from
  `details.headline` and `details.summary`, which describe what they have already done — the
  worked example is a PM writing "I'm moving into backend development." It is prose shown to a
  model as an aspiration, never a capability: `matchScore`, `RequirementMatcher` and
  `SkillCoverage` do not read it, and it plays no part in the deterministic diff. Two traps follow
  from that: naming a technology from it that the profile does not otherwise hold still fails
  `CvInvariant` on a generated CV or cover letter — an aspiration is not evidence, so "I want to
  move into Kubernetes work" gets the generation rejected exactly as if the word had appeared
  anywhere else in the document — and because it is free text, it can carry the candidate's own
  name, which trips `PromptPrivacyInvariant` and fails the analysis. Write it in the first person
  with no contact details.
