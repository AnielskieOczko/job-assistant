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

**Every leaf is now closed.** The three research tickets, the CV prototype, both grillings and the
alert-email task are all resolved, and their findings are checked in under `docs/research/` — except
the CV prototype, which lives on branch `prototype/cv-layouts`.

**[Rank and sequence the roadmap](https://github.com/AnielskieOczko/job-assistant/issues/15) is the
only unblocked ticket, and it is the one this map exists to reach.** Resolving it rewrites
`docs/roadmap.md` and ends the effort. Note that Rafal has already fixed its top item by decision
rather than by ranking — see the entry for ticket 18 below — so that ticket's remaining job is the
*rest* of the order, not the first item.

To pick a ticket yourself, pass it: `/mattpocock-skills:wayfinder 9 --ticket 15`.

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

CI/CD scores zero on the ranking axis above — not for lack of value, but because it is developer
infrastructure rather than a user-facing feature, and ranking a build pipeline against a CV redesign
on "interviews per application" is a category error.

It was therefore treated as **foundation sequenced ahead of the ranked list** rather than as an entry
within it, and **built rather than ranked** — resolved by
[ticket 16](https://github.com/AnielskieOczko/job-assistant/issues/16) on 2026-08-27. **Rank and
sequence the roadmap** should confirm that framing in one line rather than re-ranking a pipeline that
now exists.

The **CD half was found to have no target and was deliberately not built.** `docker-compose.yml` says
"Production/staging uses Neon", but that is the *database*: nothing in the repository says where the
application itself would run. Publishing a container image on merge was considered and rejected as
speculative — an image nobody pulls. Spring Boot's `spring-boot:build-image` uses Paketo buildpacks,
so if a host is ever chosen, the deploy job is an append and **no Dockerfile is owed then either**.
See `CLAUDE.md` § *Continuous integration* for what now runs.

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

## Decisions so far

Five tickets resolved, on 2026-08-26 and 2026-08-27. Each carries the full answer as a resolution
comment; the research findings, with a source cited against every factual claim, are checked in
under **`docs/research/`**.

- **[Which job-offer sources can we ingest without scraping?](https://github.com/AnielskieOczko/job-assistant/issues/10)**
  → `docs/research/10-offer-ingestion-sources.md`. A first pass concluded the Polish market was
  closed. A second pass, prompted by Rafal naming six sources on the PR, **overturned that**:
  [solid.jobs](https://solid.jobs/api-ofert-pracy) publishes a documented, keyless, sanctioned read
  API whose `description` field carries full posting prose, and whose `robots.txt` names `ClaudeBot`
  and `anthropic-ai` as welcome. It also returns structured `salary` on 500 of 500 offers — currency,
  period and the B2B-versus-employment distinction — and `skills` with a required `level` enum, which
  reaches straight into ticket 13. The other Polish boards do stay shut: JustJoin.IT forbids its own
  API in `robots.txt`, NoFluffJobs has a real RSS feed carrying no posting text, theprotocol.it 403s
  its own homepage behind a WAF, Bulldogjob's `/feeds` path never resolves. Arbeitnow, Himalayas and
  WeWorkRemotely round out the set with confirmed full text. Adzuna is the near-miss worth not
  re-proposing: clean API, Poland covered, permissive terms, description its own docs call a snippet.
  jobright.ai is not a source at all, and as a product it auto-submits applications — which this map
  ruled out deliberately.
- **[What does a model call actually cost, and can we know it per call?](https://github.com/AnielskieOczko/job-assistant/issues/11)**
  → `docs/research/11-model-call-cost.md`. Yes, and the data is already arriving and being discarded.
  OpenRouter and Requesty both return `usage.cost` inline on the completion, with no request flag,
  and LangChain4j 1.19 hands the raw provider JSON to the `AuditingChatModelListener` that already
  runs, through `OpenAiChatResponseMetadata.rawHttpResponse()` — verified against the 1.19.0 jar,
  where the field is set unconditionally. Capturing cost is a cast and a permissive JSON read, not a
  new seam. Four nullable columns on `llm_call` (`cost_usd`, `upstream_cost_usd`,
  `cached_input_tokens`, `reasoning_output_tokens`) unblock both cost display and the spend guardrail.
- **[What can GitHub tell us about a repository?](https://github.com/AnielskieOczko/job-assistant/issues/12)**
  → `docs/research/12-github-project-import.md`. A project import is not mechanical. Owner-stated
  fields are safe but thin on a real account — `description` present on 5 of 11 repositories,
  `topics` on **none**, `license` on none — and everything richer is a derived signal that must be
  confirmed by a human before it becomes profile truth. The SBOM endpoint, which would have been the
  strongest honest evidence, 404s unpredictably on repositories with equivalent manifests, so the
  reliable path is parsing `pom.xml` / `package.json` locally.

- **[Do the Polish boards' alert emails carry the full offer text?](https://github.com/AnielskieOczko/job-assistant/issues/18)**
  → `docs/research/18-alert-email-format.md`. **No — rule the email path out.** Checked manually by
  Rafal on 2026-08-28: every alert email carries a link to the posting, never the posting body, with
  no variation across the boards he subscribed to. The ticket had fixed the consequence in advance —
  following that link to get the withheld text *is* scraping — so IMAP ingestion fails on the same
  ground the map used to rule out HTML scraping, and is not a near miss to revisit. It costs little,
  because ticket 10's second pass had already replaced the email path as the route to Polish volume.
  **Rafal's direction on resolving it: solid.jobs is the single source to integrate, and the primary
  corpus for the market dashboard.** That fixes the top of the ranking by decision rather than by
  ranking, which ticket 15 should record rather than relitigate.

- **[What should the offer market dashboard answer?](https://github.com/AnielskieOczko/job-assistant/issues/13)**
  → `docs/research/13-offer-market-dashboard.md`. It answers **what a skill gap is worth** — which
  missing skill most increases the number of offers you would clear, and what those offers pay.
  Ticket 10 overtook the ticket's own premise: salary is not scarce prose to be extracted, it arrives
  normalised on 500 of 500 solid.jobs offers, so the two hardest sub-questions are answered by the
  source rather than by a model. **The finding ticket 15 must carry: market intelligence is
  downstream of automated ingestion, not a peer of it** — the market half cannot be built honestly on
  the few dozen offers Rafal pasted, because a statistic over those describes his taste. Ingested
  offers go in their own `market_offer` corpus, not into `job_offer`, whose `application` lifecycle
  row would multiply by 1,491. **The market measure is deliberately not `matchScore`**: solid.jobs
  carries no must-have/nice-to-have distinction, so `RequirementMatcher.score()` would return `null`
  on every offer; the market side computes plain `SkillCoverage` coverage under a different name, and
  the new module therefore depends on `catalog` and `profile` only, never on `analysis`. Required
  skill *level* — which Rafal asked for by name — stays descriptive and out of the deterministic
  diff, because `Proficiency` is already stored and already ignored, and switching it on would change
  what every historical `matchScore` means. A third honesty rule falls out of it, the mirror of
  ticket 32's: **a claim about the market carries its denominator, its coverage and its window, or it
  does not render** — n ≥ 30 offers, 80% field coverage, 5 offers before a per-skill premium. And
  `dataviz` overturned the assumed shape: 30–200 meaningful skills is a **table**, not charts, so the
  page is a hero figure, a KPI row, exactly one emphasis bar chart, and the priced-gap table.

- **[What should a tailored CV look like?](https://github.com/AnielskieOczko/job-assistant/issues/14)**
  → prototypes on branch `prototype/cv-layouts`, under `docs/prototypes/cv/`. **Register** wins:
  single column, skills sorted into `SkillCategory` instead of one undifferentiated chip run, dates
  in a mono rail, an **optional portrait**, and **skill badges per job** — the union of a role's
  bullet skills, taken over the bullets that actually render so a skill whose evidence was dropped
  cannot survive into the badge row. Two-column Dossier was rejected on parsing: its sidebar is a
  genuinely separate reading order. Three things generalise beyond this layout. Fonts must be
  base64-embedded **with the `latin-ext` subset**, because Chromium embeds whatever it resolved at
  render time and `latin` carries no ł, ą, ę, ś or ż. A near-miss costs a whole page, and a trailing
  `margin-bottom` on the last section is the invisible culprit. And a portrait is a **direct
  identifier**: it follows the rule the name already follows — never in a prompt, added by the
  renderer from the database afterwards, cascade-deleted with the profile.

- **[What should CI/CD actually do, and does CD have a target?](https://github.com/AnielskieOczko/job-assistant/issues/16)**
  → built, not just decided: `.github/workflows/ci.yml` and `eval.yml`, a `coverage` profile in
  `pom.xml`, and `CLAUDE.md` § *Continuous integration*. **CD has no target and none was invented.**
  The fast tier and a `frontend` job (`npm ci`, `oxlint`, `tsc -b` — oxlint being the one check
  nothing previously ran) are required on every PR; `pdf` runs on every PR but stays advisory,
  because a Playwright browser download is a network dependency and a required check that flakes is
  worse than none. **The eval tier never runs automatically** — `workflow_dispatch` or the
  `run-eval` label, with the key in an approval-gated GitHub Environment, which is also the seam
  that makes model-and-prompt regression a merge gate later for one `if:` condition rather than a
  new mechanism. SonarQube Cloud is **CI-based, not Automatic**: automatic analysis does support
  Kotlin, but fully covers only the default branch, and `main` is merges-only, so every finding
  would land after the decision. Two coverage exclusions are honest rather than cosmetic — the
  frontend has no test suite, and `PlaywrightDocumentRenderer` is tested by the tier the coverage
  job skips. The default "Sonar way" gate was kept unweakened; existing Kotlin coverage measured
  **87.1% by line**, so its 80% bar is a floor already cleared rather than an aspiration.

## Tickets

| # | Ticket | Type | State |
|---|---|---|---|
| [10](https://github.com/AnielskieOczko/job-assistant/issues/10) | Which job-offer sources can we ingest without scraping? | research | **closed** |
| [11](https://github.com/AnielskieOczko/job-assistant/issues/11) | What does a model call actually cost, and can we know it per call? | research | **closed** |
| [12](https://github.com/AnielskieOczko/job-assistant/issues/12) | What can GitHub tell us about a repository? | research | **closed** |
| [13](https://github.com/AnielskieOczko/job-assistant/issues/13) | What should the offer market dashboard answer? | grilling | **closed** |
| [14](https://github.com/AnielskieOczko/job-assistant/issues/14) | What should a tailored CV look like? | prototype | **closed** |
| [16](https://github.com/AnielskieOczko/job-assistant/issues/16) | What should CI/CD actually do, and does CD have a target? | grilling | **closed** |
| [18](https://github.com/AnielskieOczko/job-assistant/issues/18) | Do the Polish boards' alert emails carry the full offer text? | task | **closed** |
| [15](https://github.com/AnielskieOczko/job-assistant/issues/15) | Rank and sequence the roadmap | grilling | **open, unblocked** |
| [19](https://github.com/AnielskieOczko/job-assistant/issues/19) | How does a GitHub repository become a profile Project? (narrowed to the *import* half) | grilling | blocked by 15 |

**Rank and sequence the roadmap** was deliberately last, so that nothing would be ranked on a guess.
Every guess it was waiting on is now settled, and it is unblocked. Ticket 19 remains gated behind it,
and was **narrowed on 2026-08-29**: the domain half of its question — what a `Project` is — was
decided by conversation and now lives in issue 50, so ticket 19 owns only the GitHub-import half.

## Features decided outside this map

Filed as plain `enhancement` issues on 2026-08-29, by decision rather than by ranking. They are not
tickets of this map and do not gate it, but **Rank and sequence the roadmap must place them in the
order** — a ranked roadmap that omits four decided features is not a roadmap.

All four extend the profile, and the first three take the same answer to the question the map had
been circling: **`profile_skill` remains the single allowlist behind `CvInvariant`.** Neither a
project nor a credential may widen `heldSkillIds`; a skill is claimed by declaring it in `skills[]`
with a proficiency, and the new sections reference what is already declared. That keeps every one of
them on the safe side of the first rule without touching the fabrication guard.

- **[49](https://github.com/AnielskieOczko/job-assistant/issues/49) — courses, bootcamps and
  certifications.** A `credential` aggregate rather than a `kind` discriminator on `education`: the
  fields genuinely diverge, and a credential grants no skills.
- **[50](https://github.com/AnielskieOczko/job-assistant/issues/50) — side projects.** `Project` as
  its own aggregate, carrying `experience_bullet` rows under a different owner so bullets inherit the
  id-selection guard. Note the privacy trap: **a GitHub URL is a direct identifier**, so it never
  enters a prompt and `ProfileIdentityInspector` must learn about project URLs.
- **[51](https://github.com/AnielskieOczko/job-assistant/issues/51) — a stated career goal.** One
  prose field per profile, read by the narrative, cover-letter and tailoring prompts and by nothing
  else. **It never moves `matchScore`** — an aspiration is not a capability, the same reasoning that
  cut `SOFT` skills from the scoring denominator.
- **[52](https://github.com/AnielskieOczko/job-assistant/issues/52) — the CV consent clause.** The
  RODO/GDPR paragraph a Polish CV ends with, stored per profile **per language** because output
  language is already parameterised. It is the mirror image of the other three: rather than guarding
  what a model may *claim*, it guards what a model may *touch* — a paraphrased consent statement
  consents to something else, so the clause never enters a prompt and is added by the renderer, the
  pattern the name and the portrait already follow.

## Not yet specified

In scope, not yet sharp enough to ticket. Most graduates once **Rank and sequence the roadmap** has
run, because until then it is not known which items earn detailed shaping.

- **Shaping tickets for the top ~4 ranked items.** The destination requires them; the ranking names
  them. This one patch becomes roughly four tickets.
- ~~**Whether a credential is a distinct thing from an education record.**~~ **Settled on
  2026-08-29** and specified as [issue 49](https://github.com/AnielskieOczko/job-assistant/issues/49)
  — see *Features decided outside this map* below. A credential is its own aggregate and grants no
  catalog skills.
- **What outcome calibration can honestly claim at small n.** Correlating `matchScore` against real
  application outcomes is the best value-per-effort idea on the list precisely because the data
  already exists and nothing reads it — but at a few dozen applications any correlation is noise.
- **Where a cross-offer shortlist ranking lives.** Urgent only if automated ingestion ships — and
  ticket 10 concluded it would ship as a trickle, which weakens the case rather than strengthening it.
- **Placement of two small insurance policies.** The LLM spend guardrail's *mechanism* is no longer
  fog: ticket 11 established that `usage.cost` arrives inline and the audit listener already receives
  it, so only its rank remains open. Profile durability is untouched — ground truth is hand-authored
  in one Postgres, import exists, scheduled export does not.

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
