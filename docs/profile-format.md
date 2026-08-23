# Profile document format

The profile is **verified ground truth**. Nothing in it is written by a model, and a generated CV
may only make claims that trace back to a row here. Edit `sample-profile.json` (or your own copy)
and import it:

```bash
curl -X POST http://localhost:8080/api/profile/import \
  -H 'Content-Type: application/json' \
  --data @docs/sample-profile.json
```

Import is a **full replace**, not a merge — the document is the profile.

## Skill names

`skills[].skill` and `experiences[].bullets[].skills[]` are catalog names or aliases, not ids.
Any spelling the catalog knows works: `postgres`, `PostgreSQL` and `psql` all resolve to the same
skill. Import fails with HTTP 400 listing every name it could not resolve, rather than silently
dropping it — a dropped skill would vanish from every future gap report.

If a skill genuinely isn't in the catalog yet, add it first:

```bash
curl -X POST http://localhost:8080/api/catalog/skills \
  -H 'Content-Type: application/json' \
  -d '{"name":"Apache Iceberg","category":"DATABASE","aliases":["Iceberg"]}'
```

Categories: `LANGUAGE`, `FRAMEWORK`, `DATABASE`, `MESSAGING`, `CLOUD`, `DEVOPS`, `TESTING`,
`FRONTEND`, `AI`, `PRACTICE`, `TOOL`, `SOFT`, `OTHER`.

## Two rules the import enforces

1. **Every skill name must resolve** to a catalog entry.
2. **A bullet may only be tagged with skills the profile declares.** Tagging a bullet with
   `Kubernetes` while `Kubernetes` is absent from `skills[]` is rejected — otherwise that skill
   could reach a CV with nothing backing it.

## Field notes

- `proficiency`: `BEGINNER` | `WORKING` | `PROFICIENT` | `EXPERT`
- `languages[].level`: CEFR `A1`–`C2`, or `NATIVE`. Offers asking for "English B2" are checked
  against this by ordinal comparison, not by a model's opinion.
- `experiences[].endedOn: null` means the role is current.
- Array order is preserved and becomes the display order on the CV.
