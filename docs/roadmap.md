# Roadmap

Decisions already taken for work not yet started, recorded so the next branch does not relitigate
them. Everything here was settled alongside `docs/adr/0001-per-entity-profile-crud.md`; the shipped
branch was deliberately shaped so neither of these becomes a redesign.

Sequence: profile CRUD (**done**) → multi-profile → user accounts. Each is independently shippable,
and each earlier step makes the next one mostly mechanical.

## 2. `feat/multi-profile`

One profile per persona — "Java developer", "application consultant" — because a single profile
tailors badly to two different kinds of role.

A `profile` root table (`id`, `name`, `is_default`, `revision`), with every profile table
(`profile_link`, `profile_skill`, `work_experience`, `education`, `language_skill`) gaining a
`profile_id` foreign key. The `profile_details` singleton and its `check (id = 1)` constraint go
away, and `revision` moves from `profile_details` onto `profile`.

- **Offers stay unowned by a profile.** An offer is an offer; only the *analysis* is profile-scoped.
  `analysis` and `generated_document` gain `profile_id`.
- **Scoping.** `GET /api/offers/{id}/analyses/latest?profileId=` and
  `GET /api/analyses/aggregate?profileId=`, both defaulting to the default profile. `OfferLayout`
  gains a profile switcher, and the user picks a profile before running an analysis. Without this,
  "latest" is ambiguous the moment one offer is analysed against two personas.
- **Exactly one default.** `create unique index profile_one_default on profile (is_default) where
  is_default`. Setting a default is a transactional swap. Deleting the default is a 409 unless it is
  the last profile. The first profile created is the default.
- **Endpoints** become `/api/profiles/{profileId}/skills/{id}` and so on — a path segment in front
  of what already exists, plus one file of changes in `frontend/src/api/profile.ts`.

### The risk that matters

`CvInvariant`, `RequirementMatcher` and `SkillCoverage` must all read the **selected** profile's
`heldSkillIds`, never a union across profiles. Otherwise a CV for the consultant persona can claim
Kubernetes because the developer persona holds it — the exact fabrication the whole design exists to
prevent, arriving through a new door.

This needs a dedicated test with two profiles holding disjoint skills. Care is not sufficient.

## 3. `feat/user-accounts`

A `User` root (email, username, password) owning profiles, offers and model calls, with `user_id` on
the owned tables.

**It ships together with authentication, not before it.** A `user_id` column with no login is a
value that is always `1`, and a stored password with no auth is a liability that looks like a
security boundary while enforcing none. Isolation cannot be meaningfully tested until there is a way
to be a different user. Backfill stays trivial for exactly as long as there is one user, so
deferring costs almost nothing while doing it early buys nothing that can be verified.

This step reverses `CLAUDE.md`'s opening premise — "Single user, no authentication, bound to
loopback" — and deserves its own ADR at that point rather than arriving as silent drift.
