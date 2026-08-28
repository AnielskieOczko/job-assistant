# 18 — Do the Polish boards' alert emails carry the full offer text?

Resolution of [ticket 18](https://github.com/AnielskieOczko/job-assistant/issues/18), a **task** on
the [roadmap map](https://github.com/AnielskieOczko/job-assistant/issues/9). It graduated from
[ticket 10](https://github.com/AnielskieOczko/job-assistant/issues/10) as the one open route to the
Polish boards' volume that did not require scraping.

Unlike the other files in this directory, its evidence is not a citable public source. It is a manual
observation, and the only kind available: nothing could be decided until someone subscribed to a job
alert and read what actually arrived.

## The answer: no. Rule the email path out.

**Every alert email carries a link to the posting, never the posting body.** Checked by Rafal on
2026-08-28 against delivered messages from job-board alert subscriptions. The result was uniform
across the boards he subscribed to, which is why no per-board breakdown is recorded here — there was
no variation to record.

This confirms the working assumption the ticket itself flagged as needing testing rather than
trusting: alert emails exist to drive a click back to an ad-supported page, so a teaser is the format
that serves the sender.

## What follows

The ticket set out the consequence in advance, so there is no judgement left to make:

> If it contains a teaser and a link, the path is a worse version of the scraping problem the map
> already ruled out, because following that link to get the real text *is* scraping.

So **IMAP ingestion is not worth building**, and it is not a near miss to revisit — it fails on the
same ground the map used to rule out HTML scraping on 2026-08-26, which is a decision this effort
does not reopen. Parsing your own inbox is not scraping anyone's site; following the link inside it
to fetch the text the email withheld is exactly that.

## Why this costs less than it would have

When this ticket was filed, the email path was the *only* remaining route to Polish-market volume —
ticket 10's first pass had concluded the Polish boards were closed. Ticket 10's second pass overturned
that before this ticket was answered: [solid.jobs](https://solid.jobs/api-ofert-pracy) publishes a
documented, keyless, sanctioned read API whose `description` field carries the full posting prose,
and whose `robots.txt` names `ClaudeBot` and `anthropic-ai` as welcome.

So the negative closes a *supplementary* route rather than the only one.
`docs/research/10-offer-ingestion-sources.md` remains the live answer on ingestion, and its verdict
stands unchanged: **solid.jobs is the source**, with Arbeitnow, Himalayas and WeWorkRemotely as
thin-but-clean remote-market supplements.

Rafal's direction on resolving this ticket, recorded because it decides what the ingestion module is
built against: **solid.jobs is the single source to integrate, and the primary corpus for the offer
market dashboard specified in `docs/research/13-offer-market-dashboard.md`.**
