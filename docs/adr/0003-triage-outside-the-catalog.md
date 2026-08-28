# 3. Ranking the review queue in its own module, outside the catalog

Date: 2026-08-28

## Status

Accepted.

## Context

Market ingestion put 1,533 unresolved terms into `unmatched_term` from a single poll. The review
queue was a flat, unfiltered, 100-row list ordered by `occurrences desc`, with no search, no
threshold, and no denominator — so it became a queue you avoid opening.

Making it usable needs three things: a frequency filter, a statement of what was left out, and a
ranking that puts terms from *this* job hunt above the rest. The whole IT division is ingested and
is thick with QA, BA and PM roles, so "what the market wants" and "what a backend candidate should
review" are different questions. The ranking signal that answers the second one is **in-scope
demand**: how many corpus offers ask for a term *and* also ask for Java, Kotlin, Spring or Spring
Boot.

That number cannot be computed where the queue lives.

- `unmatched_term` belongs to `catalog`.
- In-scope demand comes from `market_offer_skill`, which belongs to `market`.
- `catalog` has **zero dependencies** and is depended on by every other module. `SkillCatalog`'s own
  docstring says resolution "is a lookup, not a judgement", and the module's value is that nothing
  can reach into it.

A `catalog → market` edge would therefore put an HTTP client, a scheduler and a third-party payload
model into every module's transitive closure, to serve a number that only one screen reads. The
inverse — putting the queue view in `market` — is no better: the review queue is not a market
concern, and `market` must never depend on `llm`, which the next step in this effort requires.

A third option was to denormalise in-scope demand onto `unmatched_term` as a column that `market`
writes. It was rejected because scope is *configuration*: the column would silently go stale the
moment `job-assistant.market.scope.skills` changed, and a ranking that quietly reflects an old scope
is worse than no ranking.

## Decision

Introduce a `triage` module that depends on `catalog` and `market`, and put the queue view there.

`market` gains a small public interface, `MarketDemand`, exposing in-scope demand keyed by
normalised term plus the scope it was measured against. It stays separate from `MarketOfferService`
because it is a different job: one pulls offers in, the other answers questions about offers already
held. `MarketScopeProperties` stays internal to `market` — scope is configured once, in the module
that owns the corpus, so the market dashboard will read the same definition rather than inventing a
second notion of "relevant".

The join happens in memory in `TriageQueueService`, on the normalised term. That key is what
`unmatched_term` is unique on and what `MarketDemand` returns, so it is a join on a shared identity
rather than a guess at one; the V15 drift test is what keeps the two spellings of that identity from
parting company.

**`triage` reads. It never writes.** Approving and rejecting stay on `/api/catalog/unmatched/{id}`.
`unmatched_term` exists precisely so that nothing but a human decision can grow the catalog, and a
second write path would be a second place for that rule to be forgotten.

## Consequences

`ModularityTest` now enforces the direction: `triage → {catalog, market}` and nothing back. A future
change that makes `catalog` reach for demand data will fail the build rather than quietly invert the
dependency.

This module is also where model-assisted suggestions will live. That was the original reason to
plan it — `catalog` must not depend on `llm` for exactly the reasons above — and it arrives one step
earlier than planned because in-scope demand needed the same seam. When suggestions land, `triage`
will depend on `catalog`, `market` and `llm`, and `catalog` will still depend on nothing.

The cost is a module whose only job is to combine two others. That is a real cost, and it is worth
paying here because the alternative is not "one less module" but "an edge from the module everything
depends on to the module that talks to the internet".

Two smaller consequences worth recording:

- `SkillCatalog` gains `allPendingUnmatchedTerms()`. Ranking and counting need the whole queue, and
  a limited scan would make the reported total a floor rather than a total — a queue reporting
  "showing 100 of 100" while holding 1,500 rows reads as finished when it is not.
- The frequency filter applies to the **sum** of `occurrences` and `market_occurrences`. Every term
  the corpus contributed has `occurrences = 0`, so a threshold on the candidate's own counter alone
  would hide the entire market behind a control whose only job is to cut the singleton tail.
