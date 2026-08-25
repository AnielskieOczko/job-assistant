# 1. Per-entity profile editing alongside a full-replace import

Date: 2026-08-25

## Status

Accepted.

## Context

The profile is hand-authored ground truth: the deterministic gap diff compares against it, and a
generated CV may only make claims that trace back to a row in it. Until now the only way to change
it was `POST /api/profile/import`, a whole-document replace driven by pasting JSON. Fixing one word
in one bullet meant re-pasting the entire profile, and on an empty database there was no way to have
a profile at all without hand-writing that document first.

`ProfileService` carried a comment justifying the full replace: partial updates "would make it hard
to reason about what a CV was generated from". That reasoning turned out to be the wrong way round,
which is the substance of this decision.

The persistence layer was already entity-shaped. `profile_link`, `profile_skill`, `work_experience`,
`education` and `language_skill` were five separate aggregate roots with their own repositories.
Only bullets were different: they hung off `WorkExperienceRow` as a `@MappedCollection`.

## Decision

Add per-entity endpoints for every part of the profile, and keep import as the bulk path. Extract
the invariants both paths depend on into one place.

Three consequences are worth recording, because they are not obvious from the diff.

### `experience_bullet` becomes its own aggregate root

Spring Data JDBC deletes and reinserts an entire `@MappedCollection` whenever its owning aggregate
is saved. Bullets were such a collection, so `experiences.save(...)` renumbered every bullet under a
role — even when the edit was to the company name.

Bullet ids are not incidental. `CvTailor` returns bullet ids and `CvSelection.from` drops any id the
profile does not contain; that is the mechanism by which a hallucinated bullet has no text to
render. Editing through the parent aggregate would have churned those ids on every keystroke-level
edit, far more often than import ever did.

So bullets were promoted to a root of their own, with `work_experience_id` and `display_order` as
ordinary columns. Reads fetch them in one query and group in Kotlin. Their skill tags stay an owned
collection, because `experience_bullet_skill` has a composite primary key and no surrogate id —
rewriting that set churns nothing.

`ProfileCrudIntegrationTest` asserts id stability directly; it is the test that justifies this shape.

### The full-replace argument was backwards

`replace()` deletes and reinserts everything, so it already reassigned every id on every run. The
previous design did not preserve the link between a stored CV and the experience behind it — it
destroyed it once per import, and got away with it only because documents are generated in a single
pass. Per-entity editing that *preserves* ids is strictly better for traceability.

The real gap was elsewhere: nothing recorded which profile state produced a given output. So a
`revision` counter now lives on `profile_details`, bumped by every write including `replace()`, and
is stamped onto both `analysis` and `generated_document`. Trailing revision means out of date, not
wrong — the stored HTML was true when it was written. A stale gap report is the more misleading of
the two, since it recommends learning something you may have since added.

### Deleting a skill is refused, not cascaded

There is no foreign key between `profile_skill` and `experience_bullet_skill`, so nothing in the
database prevents a delete from stranding bullet tags. Cascading would silently discard the evidence
linking a claim to the work behind it, in the one dataset that is meant to be authored by hand. The
delete returns 409 and names the bullets in the way instead.

## Consequences

- Writes identify skills by catalog id; import keeps names and aliases. A CRUD write comes from a
  picker that already resolved the name, so re-resolving it would only add a way to fail.
- Updates are `PUT` with full-entity bodies. `endedOn: null` is what makes a role current, and a
  patch could not distinguish that from a field the client omitted without wrapping every property.
- The catalog still refuses to invent skills as a side effect of a profile write. When a term is
  unknown the picker offers an explicit "add to catalog" that calls the catalog's own endpoint on a
  click. The rule is unchanged; only the number of page navigations is.
- Cross-module surface stays `current() / require() / replace()` plus `revision()`. CRUD is internal:
  nothing outside the module has any business writing a single skill or bullet.
- `V8` also gives `profile_skill` and `language_skill` a `display_order`, and replaces the
  case-sensitive unique constraint on `language_skill` with one over `lower(language)` — the lookup
  in `CandidateProfile.languageLevel()` was already case-insensitive, so "English" and "english"
  could both exist with arbitrary precedence.

## Not in scope

The profile is expected to become plural — one persona per target role — and later user-owned. The
aggregates and invariants here are shaped so that becomes a `profile_id` column and a path segment
rather than a redesign. When it happens, the invariant that matters is that `CvInvariant`,
`RequirementMatcher` and `SkillCoverage` must read the *selected* profile's held skills and never a
union across profiles; otherwise a CV for one persona could claim a skill only another one holds.
