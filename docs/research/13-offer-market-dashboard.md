# 13 — What should the offer market dashboard answer?

Resolution of [ticket 13](https://github.com/AnielskieOczko/job-assistant/issues/13), a
**grilling** ticket on the [roadmap map](https://github.com/AnielskieOczko/job-assistant/issues/9).
It sits in `docs/research/` to keep one file per ticket, but it is a design decision record rather
than a research finding: nothing here was looked up, it was decided, and the decisions were taken
with Rafal in fourteen questions over four rounds on 2026-08-28.

It is not a specification. It fixes the shape of the feature so that
[ticket 15](https://github.com/AnielskieOczko/job-assistant/issues/15) can rank it against a known
scope instead of against a guess, which is the whole reason the ticket existed.

## The one-line answer

**The dashboard answers what a skill gap is worth.** Not "what should I learn" — `GapsPage.tsx`
already answers that — and not "what do offers pay", but the two joined: *which missing skill most
increases the number of offers I would clear, and what do those offers pay?*

## What the ticket got wrong, and why

The ticket was written assuming market attributes would be extracted from pasted offer text by a
model, and it feared salary coverage above all: "a dashboard whose salary chart is built on 20%
coverage is worse than no salary chart."

Research [ticket 10](https://github.com/AnielskieOczko/job-assistant/issues/10) overtook that.
solid.jobs publishes a sanctioned, keyless read API returning **normalised salary on 500 of 500
sampled offers** — `from`, `to`, `currency`, `period` and the B2B-versus-employment distinction —
plus `skills` with a `level` enum on 489 of 500 and `experienceLevel` on 500 of 500. The ticket's
hardest sub-questions are answered by the source rather than by a model, and the coverage fear does
not apply to it. See `docs/research/10-offer-ingestion-sources.md`.

That changes the ticket's premise, and the first decision below follows from it.

## The decisions

### 1. Purpose: salary and learning, joined

Of the ticket's four candidate purposes, two are in: **what to learn next** and **what to ask for in
salary** — and they are in *as one question*, not as two halves of a page. The unique output is a
priced gap: "Kubernetes appears in 34% of matching offers and those offers pay 18% above the ones
without it."

**Out:** evaluating companies and boards (you apply to too few for a sample), and trend-over-time
(needs a time series that will not exist for months). Note that "what to learn next" on its own was
already shipped — `AggregateGapReport` ranked by must-have gaps, rendered by `GapsPage.tsx`. Choosing
it alone would have bought charts over a list rather than a new answer.

### 2. Population: an ingested board corpus, not your pasted offers

The market half of this dashboard **cannot be built honestly on offers you pasted**. That corpus is
a few dozen offers selected by your own taste; a statistic over it describes you, not the market.

So the dashboard is fed by ingested board offers, stored locally and aggregated in Kotlin.

**This is a sequencing finding and ticket 15 needs it: market intelligence is not independent of
automated offer ingestion, it is downstream of it.** Ranking the two as peers would be wrong. The
profile-comparison half continues to run on your analysed offers, where it belongs.

Rejected: proxying solid.jobs's own `/public-api/market-statistics/` endpoint. It is far cheaper —
no storage, no extraction — but it returns aggregates rather than per-offer rows, so it cannot be
joined against the profile, which is the entire point.

### 3. Honesty: every statistic carries its denominator, its coverage and its window

A market dashboard asserts a new class of fact. `CLAUDE.md`'s first rule stops a model inventing
facts about *the candidate*; ticket 32 established the mirror case, that an empty result must not
read as a clean bill of health. A claim about *the world* had no rule, and "median PLN 21k" over
eleven offers of which four stated a salary is the same empty-denominator failure in chart form.

The rule adopted, the same shape as `MIN_CLAIMS_FOR_A_RATE` in the eval tier:

- Every statistic renders with its denominator and coverage, on the chart, not in a footnote:
  `n=340 · salary stated on 331 (97%)`.
- A statistic below a coverage floor does not render as a number at all.
- Every chart is labelled with its source and its scope. A chart over solid.jobs is labelled
  solid.jobs, **never "the market"**, and no statistic silently mixes sources.
- Every chart carries its corpus window: `ingested 12–28 Aug`. A statistic without its date is as
  unmoored as one without its denominator.

Concrete floors:

| Floor | Value |
|---|---|
| Offers in scope before any salary statistic renders | **n ≥ 30** |
| Field coverage within scope before that field renders as a number | **80%** |
| Per-skill demand before a salary premium renders for that row | **5 offers** |

Below a floor, show the count and the words — "salary stated on 11 of 47" — never a greyed-out
chart, which reads as loading rather than as declined.

### 4. Attributes: typed columns for what is queried, plus the whole payload

Promote to typed columns the fields the dashboard queries: salary's five parts, `experienceLevel`,
`isRemote` / `isHybrid`, `locations`, and the skills array with its levels. **Keep the original API
response as `jsonb` alongside them.**

Offers expire and get delisted, so a field you did not store is not re-fetchable later, and the
second question you ask of this corpus will not be the first one. This is the same instinct as
`job_offer.raw_text`: keep the source, derive the rest.

Company size and industry are on the ticket's candidate list and are **not in the payload at all**.
They would need enrichment from somewhere else and are out of scope.

### 5. Required skill level stays market-side and descriptive

Rafal asked for required skill level specifically. The decision is that it **does not enter the
deterministic diff**: `RequirementMatcher` and `SkillCoverage` stay binary, and no offer's stated
level is compared against `Proficiency`.

Three reasons:

- `Proficiency` (`BEGINNER`/`WORKING`/`PROFICIENT`/`EXPERT`) is already stored on every profile
  skill and is **already ignored** by the matcher. Turning it on changes what `matchScore` *means*,
  and every stored analysis becomes incomparable with every new one with no honest way to recompute
  the old ones.
- The two scales are not one scale. solid.jobs's `Advanced` is a poster's self-report about a job;
  `PROFICIENT` is your self-report about yourself. Mapping them is a judgement dressed as arithmetic.
- The underlying want — "am I at the right level for this role" — is better answered by
  `experienceLevel` (Regular/Senior, populated 500/500) against your own seniority, which is a
  market-side comparison that never touches the matcher.

The dashboard still *displays* levels: "Kubernetes is asked for at Advanced in 60% of the offers that
ask for it at all" needs the offer's level, not yours.

If this is wanted in matching later it is **its own ticket**, with a migration plan for historical
scores, not a rider on this one.

### 6. A market offer is not a `JobOffer`

Ingested offers live in their own corpus — a `market_offer` table — with an explicit "save this one"
action that copies an offer into `job_offer`.

`application` carries `unique (job_offer_id)` and every offer has a lifecycle row, so ingesting 1,491
offers into `job_offer` would put 1,491 `SAVED` applications in the offer list and silently change
what `AggregateGapReport.analysedOffers` counts. More fundamentally they are different domain
objects: a `JobOffer` is something you might apply to, carrying a lifecycle, a `profile_revision`,
analyses and generated documents. A market offer is a row in a sample, and you will never write a
cover letter for 1,400 of them. Keeping them apart also makes the corpus disposable — re-ingest or
prune it without touching anything you have applied to.

### 7. The market measure is not `matchScore`, and must not be called one

**solid.jobs does not distinguish must-have from nice-to-have.** It gives skills with a level and
nothing more. `RequirementMatcher.score()` scores must-haves only and returns `null` when nothing is
scoreable — so *every* market offer would score `null`.

The market-side measure is therefore a **different measure with a different name**: coverage of the
offer's listed skills, "you cover 7 of 9", computed straight from `SkillCoverage` with no importance
weighting, because the source carries no importance.

This dissolves a coupling worry rather than solving it. The `market` module depends on `catalog` and
`profile` only — **never on `analysis`** — so there is no duplicated matcher and nothing that can
drift, because it is a different question with a different name.

Rejected: having a model classify importance on ingested offers (token cost at 1,491×, and it puts a
model back where this design just removed one), and treating every listed skill as must-have so
`matchScore` could be reused (two numbers called the same thing, one importance-weighted and one
not, disagreeing about the same offer).

Matching ingested offers is otherwise **free**: extraction is the model step, and solid.jobs hands
over skill *names*, so `SkillNormalizer` → `SkillCoverage` is pure Kotlin over the whole corpus.

### 8. Coverage is computed on read, never stored

A stored score goes stale the moment the profile is edited — the problem `profile_revision` exists to
make visible on analyses. Expanding `SkillCoverage` once and doing a set lookup per offer is
microseconds over the whole corpus. Analyses persist their findings because they cost two model
calls; that argument does not carry over to something that costs nothing.

No staleness, no revision stamping, no migration when the coverage rules change.

### 9. Unresolved ingested terms join the existing queue, with a second counter

`CLAUDE.md` is unambiguous that code must never auto-create canonical skills, and the review queue is
the point. `unmatched_term` already dedupes — `unique (normalized_term)` with an `occurrences`
counter, indexed `(status, occurrences desc)` — so volume increments rows rather than creating them.

But market volume would dominate that ranking: terms from 1,400 offers you never read would outrank
terms from the twelve you did. So the queue gets **two counters** — occurrences in offers you
analysed, and occurrences in the market corpus — with the triage UI ranking on the first and showing
the second as context.

That turns volume into the evidence triage was missing. "You have seen `Vaadin` once; the market has
asked for it 47 times" is a better prompt for a decision than either number alone.

### 10. The corpus accumulates and is never deleted

Record `first_seen_at` / `last_seen_at` per offer key and keep everything. Rows are tiny and
re-ingest is impossible after an offer is delisted. Trend-over-time was dropped as a *purpose* in
decision 1, which is not the same as throwing away the data that would allow it later.

Poll daily. At 500 offers per page and a 300 requests/minute limit, the entire IT division is three
calls, so cadence is not a constraint worth optimising.

### 11. What renders: table-first, one chart

`dataviz` pushes back on the ticket's framing — *"more than ~7 classes that all carry meaning → a
table (or table + chart)"*. There will be 30–200 skills with meaning, so charting them all is the
wrong instinct. It also forbids dual-axis outright, so demand and salary cannot share one bar chart.

1. **A hero figure** with the scope line beneath it — the priced-gap headline. *"PLN 22.4k median ·
   the offers you'd clear by learning Kubernetes"* over *"Java/Kotlin/Spring · Poland · n=340 ·
   ingested 12–28 Aug"*.
2. **A KPI row of stat tiles** for the scope's salary shape: median, p25–p75, B2B versus employment
   split, remote share — each carrying its own coverage, so the remote tile states its gap rather
   than hiding it.
3. **One chart, in the emphasis form**: top ~8 skills by *offers gained if learned*, single
   sequential hue, your gaps in the accent and everything else in de-emphasis gray. Not categorical
   — categorical buries the one row that matters.
4. **The priced-gap table as the primary surface**: skill · demand (n) · your status · median salary
   of offers requiring it · delta versus scope median · offers gained. Sortable. Status wears the
   reserved status palette with an icon and a label, never colour alone.

### 12. The scope must be stated

"Offers requiring X pay 18% more" is meaningless without naming the population it is measured
against. The population is a **stated scope** — Java/Kotlin/Spring backend, Poland — labelled on
every chart, with the premium computed inside it. Comparing your gaps against all IT offers would
produce real and useless numbers; a React salary premium is not actionable for you.

The "offers you would plausibly clear" filter sits on top of that scope as a filter, not as the
population, and it is what makes the headline in decision 11 computable.

## What this leaves for ticket 15

Two things, stated plainly so they are not rediscovered:

1. **Market intelligence is downstream of automated ingestion.** Rank them accordingly; they are not
   peers, and market intelligence cannot be sequenced first.
2. **Scope is now known rather than guessed.** The feature is a new `market` module depending only on
   `catalog` and `profile`, one table plus a `jsonb` column, a daily poll, a computed-on-read
   coverage measure, one counter added to `unmatched_term`, and a four-element page with exactly one
   chart. No model calls, no changes to `RequirementMatcher`, no changes to the analysis pipeline.
