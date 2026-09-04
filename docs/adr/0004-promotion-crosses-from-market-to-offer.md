# 4. Promotion crosses from `market` to `offer`, and never the other way

Date: 2026-09-04

## Status

Accepted.

## Context

`CLAUDE.md` has always specified the way out of the corpus — *"A `market_offer` is not a `JobOffer`
and must not become one … Saving one for real is an explicit copy"* — and until issue #79 no code
path performed that copy. Building it means one module has to know about the other, and there are
only three shapes available.

The modules today:

- `offer` **depends on nothing.** `JdbcOfferService` imports the JDK, Spring and its own package.
- `market` depends on `catalog`, and on `profile` for reads only, and carries an HTTP client, a
  third-party payload model and a scheduled poll.
- `analysis` and `document` both depend on `offer`, which is the direction everything downstream of
  an offer already runs in.

## Decision

**`market` depends on `offer`.** `MarketPromotionService` reads one corpus row and calls
`OfferService.promoteFromMarket`, which is the entire edge — one call, from one class, that
ingestion knows nothing about.

### Why not `offer → market`

It would put an HTTP client, a JSON payload model and a `@Scheduled` poll into the transitive
closure of the module that `analysis` and `document` both sit on top of, to serve one endpoint. That
is the argument ADR-0003 made for `catalog`, and it applies here for the same reason: `offer`
depending on nothing is a property worth more than the convenience of writing the copy on the offer
side. It would also be a cycle, since `market` would still need `offer` for nothing — or `market`
would have to expose its corpus rows publicly so that `offer` could read them, which widens a public
surface to avoid widening a dependency.

### Why not a third module, as `triage` was

ADR-0003 created a module rather than an edge because the join genuinely needed **both** sides and
`catalog` could not be allowed to depend on anything at all. Neither holds here. `offer` has no such
rule attached to it — it is simply undepended-on today — and promotion is not a join: it is a copy
in one direction, with no reads back. A module for one call would be an indirection bought with a
package.

### Why the edge stays narrow

The rule `CLAUDE.md` already states about `market → profile` applies unchanged: *"the moment
ingestion needs a profile, the corpus has stopped being a sample and started being about one
person."* The same is true here. `MarketIngestion` does not know `MarketPromotionService` exists,
and must not: pulling offers in and choosing to apply to one are different jobs, and only the second
is about the candidate.

## Consequences

- `market` now depends on `catalog`, `profile` (read-side) and `offer`. `offer` still depends on
  nothing, which was the point.
- The endpoint is `POST /api/market/offers/{id}/promote`, on the market side, because that is where
  the human is looking when they decide.
- `offer` gained `promoteFromMarket`, which shares its whole body with `paste` — including the
  content hash. A promoted offer is a pasted offer that knows where it came from, so promoting a
  listing whose text was already pasted returns the offer you have rather than forking its history.
- `job_offer.origin` and `job_offer.market_offer_id` carry provenance on the row rather than in a
  UI label. `origin` survives the deletion of the corpus row that `market_offer_id` names, because
  "did I find this or did the poll" must not become unanswerable.
- **There is no bulk or scheduled form, and adding one would undo this.** Thousands of `SAVED`
  applications nobody chose is the outcome the two tables exist to prevent, and it would silently
  change what `AggregateGapReport.analysedOffers` counts. `docs/roadmap.md` declines the scheduled
  variant outright.
