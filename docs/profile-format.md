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

## Skill names

`skills[].skill` and `experiences[].bullets[].skills[]` are catalog names or aliases, not ids.
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
2. **A bullet may only be tagged with skills the profile declares.** Tagging a bullet with
   `Kubernetes` while `Kubernetes` is absent from `skills[]` is rejected — otherwise that skill
   could reach a CV with nothing backing it.

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
- Array order is preserved and becomes the display order on the CV — for **every** collection.
  Before `V8` that was not true of `languages[]`, which was read back alphabetically, and of
  `skills[]`, which happened to work only because a fresh import inserted them in document order.
- A language is unique case-insensitively: `English` and `english` are the same entry.
