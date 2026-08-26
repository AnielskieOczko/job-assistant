# Roadmap wayfinding

This file exists so that a session starting cold can pick up the roadmap effort without being told
anything. It is a **checked-in snapshot**; the live map is
[Roadmap: the next phase of the job assistant](https://github.com/AnielskieOczko/job-assistant/issues/9)
and its child issues, and **GitHub is canonical**. When the two disagree, GitHub is right and this
file is stale.

> **Refresh this file** whenever a ticket is resolved, using
> `gh issue view 9` for the map body and
> `gh issue list --state all --label "wayfinder:research" --label "wayfinder:grilling" --label "wayfinder:prototype"`
> for ticket states. Stale is acceptable; silently stale is not, so the refresh belongs in the same
> commit as the resolution.

Distinguish this file from `docs/roadmap.md`. That one records decisions already taken for work not
yet started. This one records the effort that is *producing* the next version of it — and rewriting
`docs/roadmap.md` is the last thing this effort does.

## How to continue in a new session

Run `/mattpocock-skills:wayfinder 9`. That loads the map, picks the first unblocked, unassigned
ticket, claims it, and resolves it. Resolve **one ticket per session**, except research tickets,
which can be run in parallel because they only gather facts.

To pick a ticket yourself, pass it: `/mattpocock-skills:wayfinder 9 --ticket 13`.

To see what is takeable right now:

```bash
gh issue list --state open --json number,title,assignees,labels \
  --jq '.[] | select(.assignees | length == 0) | "\(.number)  \(.title)"'
```

A ticket is takeable when it is open, unassigned, and has no open blocker. GitHub renders the
blocking relationships in the issue UI, so the sidebar of issue 15 shows what still gates it.

## Destination

A ranked, sequenced roadmap for the next phase of the job assistant, written into `docs/roadmap.md`:
every candidate feature carrying an explicit feasibility, complexity and effort-versus-value verdict,
the list ordered by that verdict, and the top ~4 items shaped to spec-ready. Items below that line
are ranked but deliberately left unshaped.

Reaching the end means someone can open `docs/roadmap.md` cold and start building the top item
without relitigating why it is the top item.

## The decisions that fixed this map's shape

Settled by conversation on 2026-08-26, before any ticket existed. Recorded here because reopening
them silently is the main way an effort like this goes wrong.

- **Who it is for.** A personal tool that stays Rafal's. Portfolio quality is a strong secondary
  concern and breaks ties. Multi-user accounts and authentication are out of scope.
- **What "value" means.** The *quality* of applications first — better-targeted offers, a CV that
  survives a recruiter's ten-second scan, more interviews per application — and learning direction
  second. Explicitly **not** throughput: the paste-analyse-generate path is already fast, and volume
  is not the bottleneck in a real job hunt.
- **Ingestion is restricted to sanctioned sources.** Official APIs, RSS or JSON feeds, and offers
  forwarded by email. No HTML scraping, which buys a maintenance treadmill against anti-bot defences
  and changing markup.
- **Browser-automated auto-apply is out.** Its downside is asymmetric — an agent sending
  applications under Rafal's name to the very people deciding whether to hire him — and its upside
  is about two minutes per application.

## Foundation, as distinct from ranked features

The repository has no `.github` directory, no Dockerfile and no host configuration, so there is no CI
at all. CI/CD scores zero on the ranking axis above — not for lack of value, but because it is
developer infrastructure rather than a user-facing feature, and ranking a build pipeline against a CV
redesign on "interviews per application" is a category error.

It is therefore proposed as **foundation sequenced ahead of the ranked list** rather than as an entry
within it. **Rank and sequence the roadmap** will confirm or overturn that, and is asked to say so
explicitly either way rather than letting the item drift to the bottom by default.

Note also that CI is the cheapest half. `docker-compose.yml` says "Production/staging uses Neon", but
that is the *database*: nothing in the repository says where the application itself would run, so
the "CD" half may be aspirational until a host and a Dockerfile exist.

## Two rules every ticket is checked against

Both are from `CLAUDE.md` and neither is negotiable within this effort:

1. **The AI must never be able to invent experience the candidate does not have.** Any feature where
   a model produces a *fact* about the candidate — rather than prose about facts already in the
   profile — is on the wrong side of the line and needs a review queue, not a prompt asking nicely.
   The GitHub project import is the live test case: an inferred tech stack is a generated fact.
2. **No direct identifier may reach a model provider.** Any feature adding a prompt must state which
   profile fields it interpolates.

## Already shipped — do not re-propose

Charting turned up four items that were assumed missing and are not:

- **Application lifecycle tracking.** `ApplicationStatus` runs
  `SAVED → ANALYZED → APPLIED → INTERVIEWING → REJECTED → OFFER`, with `appliedOn` and `notes`, a
  `PUT /api/offers/{id}/status` endpoint, and a frontend mutation.
- **Unmatched-term catalog triage UI**, in `CatalogPage.tsx`.
- **Output-language parameterisation** of CV and cover letter (`Write in {{language}}`), so a Polish
  offer already yields Polish documents.
- **Cross-offer skill demand and gap aggregation** — `AggregateGapReport` and `GapsPage.tsx`. The
  *skill* half of market intelligence exists; only the *market* half is missing.

## Tickets

| # | Ticket | Type | State |
|---|---|---|---|
| [10](https://github.com/AnielskieOczko/job-assistant/issues/10) | Which job-offer sources can we ingest without scraping? | research | open |
| [11](https://github.com/AnielskieOczko/job-assistant/issues/11) | What does a model call actually cost, and can we know it per call? | research | open |
| [12](https://github.com/AnielskieOczko/job-assistant/issues/12) | What can GitHub tell us about a repository? | research | open |
| [13](https://github.com/AnielskieOczko/job-assistant/issues/13) | What should the offer market dashboard answer? | grilling | open |
| [14](https://github.com/AnielskieOczko/job-assistant/issues/14) | What should a tailored CV look like? | prototype | open |
| [16](https://github.com/AnielskieOczko/job-assistant/issues/16) | What should CI/CD actually do, and does CD have a target? | grilling | open |
| [15](https://github.com/AnielskieOczko/job-assistant/issues/15) | Rank and sequence the roadmap | grilling | blocked by 10–14, 16 |

The first six are independent and can run in any order or at once. **Rank and sequence the roadmap**
is deliberately last: ranking automated ingestion before knowing whether ingestable sources exist, or
ranking the dashboard before its scope is fixed, would be ranking a guess.

## Not yet specified

In scope, not yet sharp enough to ticket. Most graduates once **Rank and sequence the roadmap** has
run, because until then it is not known which items earn detailed shaping.

- **Shaping tickets for the top ~4 ranked items.** The destination requires them; the ranking names
  them. This one patch becomes roughly four tickets.
- **Whether a credential is a distinct thing from an education record.** Bootcamps, MOOC
  certificates, vendor certifications and conference talks all fit `EducationImport`
  (institution / degree / fieldOfStudy) badly. The sharp version is whether a credential *grants
  catalog skills* — if it does it feeds `heldSkillIds` and therefore the fabrication guard, making it
  a domain decision rather than a form field.
- **How a GitHub repository becomes a profile Project without becoming a fabrication vector.**
  Blocked on ticket 12.
- **What outcome calibration can honestly claim at small n.** Correlating `matchScore` against real
  application outcomes is the best value-per-effort idea on the list precisely because the data
  already exists and nothing reads it — but at a few dozen applications any correlation is noise.
- **Where a cross-offer shortlist ranking lives.** Urgent only if automated ingestion ships.
- **Placement of two small insurance policies:** an LLM spend guardrail (a budget that refuses calls
  past a cap, as distinct from merely displaying cost), and profile durability (ground truth is
  hand-authored in one Postgres; import exists, scheduled export does not).

## Out of scope

Beyond this map's destination. These do not graduate; they return only as a fresh effort.

- **Browser-automated auto-apply.**
- **HTML scraping of job boards behind anti-bot defences.**
- **Interview preparation pack** — generating likely interview questions from an offer's
  requirements and the exact bullets a tailored CV claimed, which extends the anti-fabrication rule
  to what gets said out loud. A genuinely new product surface deserving its own map. Parked
  deliberately, not rejected.
- **Model and prompt regression as a merge gate** — extending `EvalScorecard` to narrative and cover
  letter quality so model swaps are measured rather than felt. Real hygiene, no user-visible value,
  so it would rank last and clutter the ordering. Parked deliberately, not rejected. **Its cost drops
  sharply once a pipeline exists**, since it becomes one more job rather than a new mechanism — which
  makes it the out-of-scope item most likely to return.
- **Multi-user accounts and authentication** — already a separate step in `docs/roadmap.md`, and it
  reverses `CLAUDE.md`'s "single user, no authentication" premise.
