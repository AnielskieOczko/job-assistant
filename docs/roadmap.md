# Roadmap

Decisions already taken for work not yet started, ranked and sequenced, recorded so the next branch
does not relitigate them.

This file is the output of [issue #15](https://github.com/AnielskieOczko/job-assistant/issues/15),
which closed the roadmap wayfinding effort on 2026-09-03. `docs/roadmap-wayfinding.md` holds the
reasoning that produced it and is now a record of a finished effort rather than a live map;
`docs/architecture-review-2026-08.md` is a separate input, a snapshot of where the codebase is
shallow. **This file is the one that says what to build next.**

The top five items each have a shaping ticket on GitHub. Where an item has an issue number, GitHub
is canonical and this file is a summary.

## The ranking axis

Fixed by conversation on 2026-08-26 and unchanged by this ranking:

**The quality of applications first** — better-targeted offers, a CV that survives a recruiter's
ten-second scan, more interviews per application. **Learning direction second** — the app telling
the candidate what to learn next. Explicitly **not throughput**: the paste-analyse-generate path is
already fast, and volume is not the bottleneck in a real job hunt. Portfolio value breaks ties.

### The caveat this ranking had to write down

Hosting scores approximately zero on that axis and therefore ranks last. That is the axis working,
not failing: a loopback tool on your own machine produces exactly the same CV, the same gap report
and the same tailored letter as one behind a URL, and nobody gets an interview because the
application has a hostname. Hosting is justified by portfolio value and by convenience.

The axis was **not** amended to accommodate it. If the job hunt ends, or if showing the tool to
someone becomes the point rather than a side effect, that is the moment to revisit the axis
deliberately — and to say so here — rather than to quietly promote a single item past it.

## The order

| # | Item | Issue | Feasibility | Complexity | Effort | Value on the axis |
|---|---|---|---|---|---|---|
| ~~1~~ | ~~Promote a corpus offer into a real offer~~ | [#79](https://github.com/AnielskieOczko/job-assistant/issues/79) | — | — | **shipped 2026-09-04** | *see below* |
| ~~2~~ | ~~Record which document was sent~~ | [#85](https://github.com/AnielskieOczko/job-assistant/issues/85) | — | — | **shipped 2026-09-04** | *see below* |
| ~~3~~ | ~~Offer shortlist ranking~~ | [#80](https://github.com/AnielskieOczko/job-assistant/issues/80) | — | — | **shipped 2026-09-04** | *see below* |
| ~~4~~ | ~~AI-assisted polish of a profile field~~ | [#81](https://github.com/AnielskieOczko/job-assistant/issues/81) | — | — | **shipped 2026-09-04** | *see below* |
| 5 | Generated-document library and reuse | [#82](https://github.com/AnielskieOczko/job-assistant/issues/82) | certain | low–medium | ~1.5 sessions | **Visibility** |
| 6 | Privacy indicators in the UI | [#83](https://github.com/AnielskieOczko/job-assistant/issues/83) | certain | low–medium | ~1 session | Zero; wins the tiebreak |
| 7 | The dialog-reseed rule, stated once with a test | [#72](https://github.com/AnielskieOczko/job-assistant/issues/72) | certain | low | ~½ session | Zero, but a real bug |
| 8 | The wire-contract guard, Path B | [#68](https://github.com/AnielskieOczko/job-assistant/issues/68) | certain | low | ~½ session | Zero; closes a silent hole |
| 9 | GitHub repository → profile Project import | [#19](https://github.com/AnielskieOczko/job-assistant/issues/19) | thin source data | medium | ~2 sessions | ~Zero |
| 10 | The remaining profile-UI refactor | [#72](https://github.com/AnielskieOczko/job-assistant/issues/72) | certain | low | ~1 session | Zero |
| 11 | Host this application | [#62](https://github.com/AnielskieOczko/job-assistant/issues/62) | needs a decision first | high | many sessions | ~Zero; portfolio |

**The top four were 1–4, and all four are done.** Items 1–6 all carry shaping tickets
written to be picked up cold; the ranking only required the top four to be shaped, and the other two
are features that were asked for rather than lines in a list.

**The numbers do not move when an item ships.** Items 1 to 4 are done and the rest keep the
numbers they were ranked under: they are how this file and the GitHub tickets refer to each other,
and renumbering would silently change what a comment saying "item 5" points at. **Item 5 is next.**

Three orderings in that table look surprising and are deliberate. **Item 2 was ranked out of
value-for-effort order** — it was second because its cost rose with every application sent without
it, not because it was the second most valuable thing; it shipped on 2026-09-04 and its record is
below. **Hosting is last, not fifth** —
it is simultaneously the lowest-value item on the axis and by far the most expensive, so
value-for-effort puts it at the bottom on its own arithmetic; the portfolio tiebreak only separates
items of comparable cost, and hosting is not comparable to anything else here. And **item 4 outranks
item 5** even though it is the larger build, because polishing a project description once improves
every CV generated from it afterwards, whereas the library improves one document at a time.

---

## 1. Promote a corpus offer into a real offer — shipped 2026-09-04

`POST /api/market/offers/{id}/promote` copies one listing into the offer list, where it is analysed
and tailored to exactly like a pasted one. The corpus table gains a **Promote** button per row, and
a row already promoted becomes a link to the offer instead.

**The issue's premise was wrong, and finding out was half the work.** #79 said the corpus carried
the posting prose in `market_offer.description`. There was no such column, and the text was not
recoverable from `payload` either: `SolidJobsOffer` never modelled `description`, so Jackson dropped
it at parse, and ingestion stored a re-serialisation of that parsed object rather than the response —
so V14's promise that `payload` held "the whole response verbatim" was false, and the insurance it
described did not pay out on the first occasion it was needed. Verified against the live API on
2026-09-04: the field is there, 4,317 characters on the first IT-division offer. All 1,505 stored
rows had nothing but title, company, skills, salary and location.

Fixing that came first, which is why this item cost more than the "~1 session, low" it was ranked
at. `SolidJobsPages` now keeps each offer's own JSON, `V28` adds the `description` column, and a row
without one is **refused** rather than promoted from its structured fields.

The decisions worth not undoing:

- **`market` depends on `offer`, never the reverse.** `offer` depends on nothing, and putting an
  HTTP client and a scheduler into the closure of the module `analysis` and `document` both sit on
  is ADR-0003's mistake in a new place.
  `docs/adr/0004-promotion-crosses-from-market-to-offer.md` records the alternatives.
- **Provenance is two columns, not one.** `origin` (`PASTED`/`MARKET`) and `market_offer_id`:
  the id is a real foreign key and goes null if its corpus row is deleted, and "did I find this or
  did the poll" has to survive that.
- **Promotion shares its whole body with `paste`,** content hash included, so promoting twice
  returns the offer you have and a listing already pasted by hand keeps its `PASTED` origin.
- **The listing's own skill names are kept out of the offer text.** They are already resolved
  through the catalog, so feeding them to the extractor would return our own resolution and make
  `matchScore` the market dashboard's coverage number under a second name.
- **No bulk form and no scheduled form.** The scheduled variant stays declined below.

## 2. Record which document was sent — shipped 2026-09-04

`application` now carries `sent_cv_document_id` and `sent_cover_letter_document_id`
(`V27__sent_document.sql`), written through `PUT /api/offers/{offerId}/documents/{documentId}/sent`
and cleared by `DELETE /api/offers/{offerId}/documents/sent?type=…`. The Documents tab marks the
document on screen, and the offer list carries a **Sent** column so "Applied" is no longer a status
with nothing behind it.

Four decisions the build had to take, kept here because they are the ones a later change could undo
by accident:

- **The link lives on `application`, not on `generated_document`.** An application has at most one
  CV and one cover letter that went out; a document may exist and never be sent. Two columns keep
  *"what did I send for this offer"* a single read of a row the offer list already loads.
- **The write path lives in `document`, not in `offer`.** `document` already depends on `offer`
  (`JdbcDocumentService` reads `OfferService`), so an `offer → document` edge would be a cycle and
  `ModularityTest` would fail. `offer` therefore exposes `markCvSent` / `markCoverLetterSent` taking
  a bare id and checking nothing; `DocumentService.markSent` resolves the id, refuses a document
  belonging to another offer, and dispatches on the document's **own** type — marking a cover letter
  as the CV that was sent is not a thing the API can express.
- **The foreign key is deliberate, and the opposite choice from `llm_call`.** That table's
  `subject_kind`/`subject_id` pair carries no key so cost history outlives what it paid for; this
  link exists so the document can be opened, so `on delete set null` drops it rather than dangling
  it. There is no document-deletion path today; this is the rule for the one that may arrive.
- **Sent and status stay independent in both directions.** Marking a document does not move the
  status or restamp `statusChangedAt`, and a status change leaves the link alone. Both are asserted
  in `SentDocumentIntegrationTest`, because deriving either fact from the other would fabricate a
  record in the table calibration will eventually read.

Marking stays optional and reversible: an application made outside the tool has no document to name,
and `sentDocumentsLabel` renders that absence as a dash rather than as "nothing was sent".

## 3. Offer shortlist ranking — shipped 2026-09-04

`GET /api/analyses/shortlist?profileId=` returns every saved offer with the score of its latest
completed analysis, ranked. `/offers` renders it as a **Match** column with a sort control, so
"which of these should I apply to first" is answered without opening each offer in turn.

Five decisions the build had to take:

- **The join lives in `analysis`, not in `offer`.** `offer` depends on nothing, and the edge that
  would let it read a score is the one `analysis` already occupies in the other direction — ADR-0003's
  mistake in a new place. `AnalysisService.shortlist` is a new type on an existing edge, the way
  `ProfileCoverage` was. It reads `job_offer` and `application` through `OfferService` and queries
  only its own table, so the module owning each table is still the one reading it. Two queries, one
  HTTP request; what the issue ruled out was resolving an analysis per row from the browser.
- **Latest analysis, not the best one.** A re-run that scores lower replaces the number. Promoting
  an older, higher score would show a figure nothing currently backs.
- **A DONE analysis with a null `match_score` leaves its offer unscored.** Nothing scoreable is not
  zero percent, and `RequirementMatcher` already refuses to report it as one; the list had to agree.
  Unscored offers sort *below* every scored one, including a measured `0%`.
- **The order is total on both sides.** `ShortlistOrder` and `lib/shortlist.ts` apply the same rule —
  score descending, unscored last, offer id descending — because the client re-sorts rows it already
  holds so the toggle costs no request. Without the id tie-break the list is free to reshuffle.
- **`scored` and `total` travel with the rows,** and the page states the shortfall. Ten rows built
  from three analyses is a ranking of three, and the rows alone cannot say so. A `V1_ALL_CATEGORIES`
  score is marked in the cell rather than quietly compared: historical scores are never recomputed,
  so this is the one screen where the two rules sit side by side.

**One trap found on the way.** `ProfileService.defaultProfileId()` throws when no persona exists, and
the established way to treat that as a state rather than an error was to catch it — which is not
enough: the throw happens inside a transaction, so a transactional caller has its transaction marked
rollback-only and fails at commit having handled nothing. `ProfileSkillCoverage` got away with it
only because its caller was not transactional. `findDefaultProfileId(): Long?` now states the
non-throwing lookup once, and both callers use it.

## 4. AI-assisted polish of a profile field — shipped 2026-09-04

`POST /api/profiles/{id}/polish?field=…` returns a rewrite of one free-prose field and stores
nothing. The profile editor puts a **Polish with AI** button under the career goal, a project's name
and description, and every bullet's text; the suggestion opens under the field beside the original,
is editable, and **Use this** fills the form control the ordinary Save then writes.

**It sits on rule one and is shaped so that it does not cross it.** The precedent was already in the
codebase, in `LlmTask.TRIAGE`: *"Never authoritative … everything it returns is re-resolved against
the catalog and dropped if it does not exist, and a human still clicks approve."*

The decisions worth not undoing, recorded in
`docs/adr/0005-polish-suggests-and-never-writes.md`:

- **`ProsePolishService` has no write path at all** — no repository, no `JdbcClient`. The accept is
  a separate `PUT` from the browser to the CRUD endpoint that has always been the only way in, so
  "no model writes to the profile" is a fact about the module graph rather than a promise in a
  prompt.
- **`polish` is its own module.** `profile` is depended on by `analysis`, `document` and `market`, so
  a `profile → llm` edge would put the model factory, the audit listener and the provider config
  into all three closures to serve one endpoint — ADR-0003's argument in a new place. `polish`
  depends on `profile`, `catalog`, `llm` and `privacy`; nothing depends on `polish`.
- **The scan was extracted and the consequence was not.** `CvInvariant`'s reading is now
  `SkillMentions` in `catalog` and both callers share it; `CvInvariant` is a delegate whose tests
  were not edited, which is what proves the extraction changed nothing. A CV naming an unheld skill
  is still thrown away with a 422. A *suggestion* naming one comes back flagged and is shown — the
  reader is the candidate, and declaring the skill is a legitimate answer.
- **The prompt is one field's text plus a description of what that field is.** Not the project, not
  the employer, not the dates. A prompt built from a `Project` would carry its URL, which
  `ProfileIdentityInspector` refuses outright; building from the field is what makes the call
  possible rather than merely tidy.
- **Free prose only, and the list is closed:** career goal, project name, project description,
  experience bullet. An unknown `field` is a 400 rather than a fifth thing to polish.
- **An empty suggestion is a 422, not an empty pane.** Next to the original it would read as "your
  text is best left alone", a judgement no empty response supports — the same shape as an empty
  extraction reading as "this offer asks for nothing". Blank and oversized input never reach a
  provider, and there is no bulk form.
- **A fifth `LlmTask`**, `POLISH`, routable to a cheap model and separable in `llm_spend_daily`.

Cheaper than its "~2 sessions, medium" estimate: the shaping ticket had already settled the hard
questions, and the only thing the build discovered was that the module placement needed an ADR of
its own rather than a paragraph.

## 5. Generated-document library and reuse

Every generated document is reachable **only through the offer that produced it**. There is no
cross-offer view, which means there is no way to look at what you actually sent an employer. Two
similar Java offers do not always justify two generations — reusing a CV you already read and
approved is a legitimate decision the application currently cannot express.

`GeneratedDocument` already carries what a library needs: type, language, `createdAt`,
`profileRevision`, both drop counts and `consentClauseLanguage`. What is missing is the read path
and one column.

- **Reuse is a copy with provenance**, not a link. A new `generated_document` row for the target
  offer carrying `source_document_id`, so the list can say *"reused from &lt;offer&gt;"* and the
  drop counts are never misread as this offer's tailoring. A row that claims a tailoring which never
  happened is the same failure as a rate without its denominator.
- **The copy re-runs `CvInvariant`.** It costs no model call, and it catches the case the original
  generation could not: a skill deleted from the profile since is now a fabricated claim on a
  document about to be sent.
- **A trailing `profileRevision` is reported, not hidden.** The stored HTML was true when written;
  a moved revision makes it out of date rather than wrong, and the library should say which.
- **Where it lives:** a top-level `Documents` route beside Offers, Profile and Market, plus a
  *"reuse an existing CV"* action on an offer's Documents tab that opens the same list filtered.

## 6. Privacy indicators in the UI

The application has a genuinely strong privacy architecture — ADR-0002, three enforced layers, a
guard that refuses outgoing prompts — and the UI says nothing about any of it. This scores zero on
the ranking axis and wins the portfolio tiebreak outright: it is the codebase's best story and it is
currently invisible to anyone the application is shown to.

**A two-state shield would be a lie, and that is the whole design.** There are three states:

| State | Fields | Mechanism |
|---|---|---|
| Never sent, **enforced** | name, email, phone, profile links, project URLs | `PromptPrivacyInvariant` refuses the prompt |
| Never sent, **by construction** | location, portrait, consent clause | Prompt builders omit it; no guard |
| **Sent** to the model | employers, schools, dates, bullet text, skills | Tailoring is worthless without them |

The third row is the one most worth rendering. A user who reads a shield as *"my profile is
private"* and later sees their employer history in a prompt has been misled by a well-meaning icon.

- **The manifest comes from the backend**, derived from the same source the invariant checks, with a
  test asserting the enforced set equals what `PromptPrivacyInvariant` actually inspects. A
  hardcoded frontend list that says "protected" about a field the guard stopped covering is worse
  than no badge at all.
- **The two "never sent" states render differently.** Collapsing them claims enforcement that
  `location` and the portrait do not have — and `PromptPrivacyInvariant`'s own comment is explicit
  that they are omitted by construction rather than policed.
- Clicking an indicator names the mechanism — minimize, scrub, or assert — in a sentence.

## 7–10. The tail

- **7 — The dialog-reseed rule** (part of [#72](https://github.com/AnielskieOczko/job-assistant/issues/72)).
  A subtle correctness rule with a non-obvious failure mode — a *discarded* edit reappearing as
  though it had been saved — currently living as three independent copies with no test behind any.
  It ranks above the refactor that surrounds it because it is a bug, and because what it corrupts is
  hand-authored ground truth.
- **8 — [#68](https://github.com/AnielskieOczko/job-assistant/issues/68) Path B.** `ApiContractTest`
  discovers wire DTOs by scan rather than by 62 hand-written imports. Cheap, and it closes the hole
  that is genuinely silent today: a DTO added without a test entry is unprotected and nothing says
  so. **Path A — generating the TypeScript from an OpenAPI schema — is not ranked here**, because
  it changes a convention `CLAUDE.md` states deliberately; that decision is #68's deliverable, and
  doing Path B after Path A would be wasted work.
- **9 — [#19](https://github.com/AnielskieOczko/job-assistant/issues/19), the GitHub import.**
  Ranked normally rather than declined, and it lands low. Research #12 found `description` on 5 of
  11 repositories, `topics` on none, `license` on none, and the SBOM endpoint 404ing unpredictably;
  everything richer is a derived signal needing a human review queue before it becomes profile
  truth. Since #50 shipped `Project` as hand-authored, the import's remaining value is saving typing
  on eleven repositories once — which is throughput, and the axis excludes throughput by name. The
  reusable part is the review-queue pattern, not the data. Revisit if the repository count passes
  roughly thirty.
- **10 — [#72](https://github.com/AnielskieOczko/job-assistant/issues/72), the remaining refactor.**
  The row-actions and confirm-delete wiring across seven profile cards. It carries its own bar,
  set by the issue and kept here: **it must reduce lines, or it should be closed undone** — measured
  before the PR is opened, not after, because #65 was judged on a line count that turned out to be a
  wash.

## 11. Host this application ([#62](https://github.com/AnielskieOczko/job-assistant/issues/62))

Last, for the reason given in the caveat above. #62 carries a full dependency enumeration and a
free-tier survey current as of 2026-08-31; nothing in it has been decided, and the survey ages
quickly — three of the platforms it lists changed within twelve months and one changed without
announcement.

Two things are folded into this item rather than being roadmap entries of their own.

### Authentication is hosting's precondition, not a peer

`server.address: 127.0.0.1` is currently the **only** access control the application has. Deleting
that line deletes all of it, so authentication is not a feature that accompanies deployment.

The insight that kept it unbuilt still holds and is why it is not ranked separately: **a `user_id`
column with no login is a value that is always `1`, and a stored password with no auth is a
liability that looks like a security boundary while enforcing none.** Isolation cannot be
meaningfully tested until there is a way to be a different user, and backfill stays trivial for
exactly as long as there is one user — so deferring costs almost nothing and doing it early buys
nothing that can be verified.

**There are two shapes and they are not the same amount of work.** An in-app `User` root with Spring
Security, `user_id` on the owned tables, session cookies and CSRF for the SPA; or an identity-aware
proxy (Cloudflare Access, GCP IAP) that authenticates before a request reaches the JVM — zero
application code, sufficient for a genuinely single-user tool because there is no second tenant to
isolate from, and reversible, so it does not foreclose the first. **Choosing between them is the
first task of this item**, and it deserves an ADR: hosting reverses `CLAUDE.md`'s opening premise
and should not arrive as silent drift.

Whichever is chosen, if rows ever become user-scoped, `CvInvariant`, `RequirementMatcher` and
`SkillCoverage` must read the selected user's held skills and never a union — the fabrication rule
arriving through a new door. The profile-level version of that rule is already enforced by
`ProfileIsolationIntegrationTest`, which builds two profiles with disjoint skills and asserts the
refusal in both directions; a user-level version needs the same treatment.

### Portable profile export is a rider on this, not an item

Candidate E was *"scheduled export of the hand-authored ground truth — import exists, export does
not"*, which implies the profile is unprotected. **It is not.** `scripts/db-backup.sh` runs nightly
via launchd and before every prod start, each dump carries a `.counts` sidecar, and
`scripts/db-verify-restore.sh` performs a monthly restore drill, because a backup that has never
been restored is a file rather than a backup.

What a JSON export would add is a *portable, Postgres-independent* copy. That is worth little today
and becomes a real requirement the moment the data sits on a third party's disk — and #62 notes that
`scripts/db-backup.sh` is macOS-local and does not survive the move. Ranking it standalone would
present a risk that is managed nightly and verified monthly as though it were unmanaged.

---

## Gated, deliberately not ranked: outcome calibration

Correlating `matchScore` against real application outcomes is the best value-for-effort idea that
cannot yet be built. The data already exists in the `application` lifecycle and **nothing reads it**
— `ApplicationStatus` is referenced only by the two screens that display it. The build is one query
and one small screen.

It is not ranked because at low n any correlation is noise, and shipping a screen whose number
cannot be trusted is the exact failure this repository already guards against in three other places:
`MIN_CLAIMS_FOR_A_RATE` in the eval tier, n ≥ 30 for a market claim, and *never report a number
without its denominator*. Its cheapness is the trap, not the argument.

**The trigger, so this is falsifiable rather than indefinite:** build it when applications at
`APPLIED` or beyond with a recorded outcome reach **30**. That number is the one the market
dashboard already uses, so it is consistent rather than invented.

**Item 2 was this entry's prerequisite, which is why it was ranked second and why it shipped first.**
A gate that opens onto data nobody captured is not a gate, it is a delay. Reaching thirty outcomes
while recording only `APPLIED` would have made the cheap half of calibration possible and the
interesting half permanently impossible, because the link between an application and the document it
sent cannot be reconstructed after the fact. With `sent_cv_document_id` in place from the first
application onward, the gate now opens onto data that was captured rather than data that has to be
remembered.

## Declined

Said plainly rather than ranked politely last.

- **A scheduler that pushes corpus offers into the offer list.** This was the original shape of
  automated ingestion, and it fails on the reasoning that kept `market_offer` separate from
  `job_offer` in the first place: it would create `SAVED` applications the candidate never chose and
  silently change what `AggregateGapReport.analysedOffers` counts. Research #10 also concluded the
  inflow would be a trickle, which weakens the case rather than strengthening it. The half worth
  building was item 1 — promotion by an explicit human decision — and it shipped without it.

## Recorded as done, not ranked

These were candidates when the ranking was commissioned and shipped before it ran. They are listed
so that a reader of the old candidate list does not re-propose them.

Multi-profile (`V10`), the offer market dashboard (#47), the CV template redesign (#73, #75),
credentials (#49), side projects (#50), the stated career goal (#51), the CV consent clause (#52),
LLM per-call cost observability, the spend guardrail, and CI/CD (#16). `CLAUDE.md` also lists four
things assumed missing that already ship: application lifecycle tracking, the unmatched-term triage
UI, output-language parameterisation, and cross-offer skill aggregation.

## What this file used to say

Until 2026-09-03 this file recorded a three-step sequence: *profile CRUD (done) → multi-profile →
user accounts*. Two of those three are no longer steps.

- **§2 `feat/multi-profile` shipped** in `V10__multi_profile.sql` — the `profile` root,
  `is_default`, the partial unique index, the lot. The section was describing work that already
  existed, so a session reading this file would have started building it a second time. Its one rule
  worth keeping — that coverage must never be a union across profiles — is not lost with it: it is
  enforced by `ProfileIsolationIntegrationTest`, in code, with assertions in both directions.
- **§3 `feat/user-accounts` is not a step of its own**; it is the decision gate inside item 10,
  above, and its reasoning is preserved there. Left where it was, it would have had §2's failure
  mode for a different reason — describing the in-app Spring Security shape as though it were the
  plan, when an identity-aware proxy is a sufficient and cheaper alternative it never considered.
