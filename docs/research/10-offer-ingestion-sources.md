# Offer ingestion sources, without scraping

Research for [issue #10](https://github.com/AnielskieOczko/job-assistant/issues/10), part of the
[roadmap map](https://github.com/AnielskieOczko/job-assistant/issues/9). Method: primary sources
only — official docs, a board's own terms-of-service page, a direct fetch of the endpoint itself.
Where a claim could not be traced to a primary source it is marked **UNVERIFIED** and left that way
rather than filled in with a plausible guess.

## Verdict

Ingestion without HTML scraping is worth building, but only as a thin, low-maintenance supplementary
module, not as a replacement for pasting. None of the dominant Polish IT boards — JustJoin.IT,
NoFluffJobs, Pracuj.pl, theprotocol.it, Bulldogjob — publish anything a personal tool is sanctioned to
consume; every one of them was checked directly and none has a documented API, and JustJoin.IT's own
`robots.txt` explicitly disallows `/api/`, so even the undocumented endpoint third parties have found
is off-limits by the site's own rule. What does exist, cleanly, is a handful of general European and
remote job-board APIs — Arbeitnow, Himalayas, Jobicy, Remotive, plus WeWorkRemotely's RSS and the HN
"Who is hiring" thread via Algolia's own search API — every one of which returns the **full** offer
description text, not a snippet, and none of which needs more than a free, keyless or self-registered
API call. Coverage of the specific target (Poland, mid-to-senior Java/Kotlin backend) is real but
thin per source, because these are general boards, not Polish-market specialists — the Polish-market
volume still lives on the boards this ticket rules out. So the honest shape of the recommendation is:
build a small poller against three or four of the confirmed full-text sources, expect it to surface a
trickle of relevant offers rather than replace manual pasting, and do not spend effort chasing the
Polish boards — that door is closed by their own terms and infrastructure, not by an oversight.

## Sources

| Source | API/feed (URL) | Auth | Full description text? | ToS for personal use | PL/remote-EU Java/Kotlin coverage | Verdict |
|---|---|---|---|---|---|---|
| JustJoin.IT | None documented. A `/api/offers` path exists but `robots.txt` disallows `/api/` ([justjoin.it/robots.txt](https://justjoin.it/robots.txt)) | n/a | n/a | Own robots directive forbids it | Would be excellent (Poland's largest IT board) | **Not viable** — no sanctioned path exists |
| NoFluffJobs | None documented; only third-party reverse-engineered wrappers found (e.g. `github.com/oskar-j/nofluffapi`, not first-party) | n/a | n/a | UNVERIFIED — site blocked direct fetch | Would be excellent | **Not viable** — no first-party source found |
| Pracuj.pl | None documented; `robots.txt` shows only sitemaps, no API path | n/a | n/a | UNVERIFIED — `/regulamin` returned 403 to a direct fetch | Would be excellent | **Not viable** — no feed found |
| theprotocol.it | None documented; `robots.txt` shows only a sitemap | n/a | n/a | UNVERIFIED — `/regulamin` returned 403 | Would be excellent | **Not viable** — no feed found |
| Bulldogjob | None documented; `robots.txt` shows only a gzipped sitemap | n/a | n/a | UNVERIFIED | Would be excellent | **Not viable** — no feed found |
| LinkedIn Jobs | Talent/Jobs API exists but is partner-gated ([developer.linkedin.com](https://developer.linkedin.com/) partner-program apply flow, corroborated by [Microsoft Learn's LinkedIn API partner guide](https://learn.microsoft.com/en-us/linkedin/shared/linkedin-api-partner-support-guide)) | Formal partner application: use case, live privacy policy, product demo | UNVERIFIED (never reached) | Commercial partner terms, not a personal-use tier | Would be excellent | **Not viable** — no individual/personal tier exists |
| Indeed Publisher | Retired; current Job Sync API confirmed partner-gated at [docs.indeed.com/job-sync-api/job-sync-api-guide](https://docs.indeed.com/job-sync-api/job-sync-api-guide): "When you become an Indeed partner, Indeed sets up an app for your integration" | OAuth issued after a signed partner agreement | UNVERIFIED (never reached) | Partner-only, no self-serve signup | Would be excellent | **Not viable** — self-serve access is gone |
| Adzuna | Official, [developer.adzuna.com](https://developer.adzuna.com/overview), app_id/app_key on free registration | Free key, registration required | **No — snippet only**: "we currently only provide a snippet of the job description in the response" ([developer.adzuna.com/docs/search](https://developer.adzuna.com/docs/search)) | Explicitly permits "Personal research" with Adzuna attributed as source ([developer.adzuna.com/docs/terms_of_service](https://developer.adzuna.com/docs/terms_of_service)); default limits 25/min, 250/day, 1000/week, 2500/month | Poland ("pl") is one of the supported country codes | **Not viable for this pipeline** — clean API and permissive ToS, but the description is a snippet, and following the link back to read the rest is scraping again |
| Arbeitnow | Official, [arbeitnow.com/api/job-board-api](https://arbeitnow.com/api/job-board-api) | None | **Yes** — `description` field is "Full HTML-formatted description" | ToS section 11 covers the API by name: provided as-is, must link back to Arbeitnow, access "may [be] revoke[d] ... at any time" ([arbeitnow.com/terms](https://arbeitnow.com/terms)) | Board is explicitly Germany/DACH-focused; Poland-specific volume is UNVERIFIED — no country breakdown found | **Viable** — best-shaped payload of any source found (full text, `tags` for tech, `job_types` for seniority, `remote` boolean), coverage is adjacent-market rather than Poland-native |
| Remotive | Official, [remotive.com/api/remote-jobs](https://remotive.com/api/remote-jobs), documented at [github.com/remotive-com/remote-jobs-api](https://github.com/remotive-com/remote-jobs-api) | None | **Yes** — "HTML full description of the job listing" | Must attribute/link back; forbids re-syndicating to Jooble/Neuvoo/Google Jobs/LinkedIn or harvesting emails; violation "results in API access termination" | Fully-remote global board; category and title/description keyword filters exist, no country filter — Java/Kotlin present via keyword search, PL-specific volume UNVERIFIED | **Viable**, tightly rate-limited: "more than 2x per minute" is blocked and the vendor recommends polling at most 4x/day |
| RemoteOK | `remoteok.com/api`, described by RemoteOK's own FAQ as a free public JSON feed, no auth ([featurebase help article](https://remoteok.featurebase.app/help/articles/3140840-is-there-an-api-or-rssjson-feed-of-remote-jobs)) | None per the FAQ | UNVERIFIED — the endpoint returned HTTP 403 to a direct fetch in this session (twice, including the `/legal` ToS page), so the field list could not be confirmed first-hand | Requires linking back to RemoteOK and naming it as source per the FAQ; exact ToS text UNVERIFIED (403) | Global remote board; PL/EU-specific volume UNVERIFIED | **Provisionally viable, unconfirmed** — nominally a public API, but it sits behind bot-detection that blocked a plain fetch, which is itself a signal of fragility for an unattended poller |
| WeWorkRemotely | Official RSS, [weworkremotely.com/remote-jobs.rss](https://weworkremotely.com/remote-jobs.rss) plus category feeds (e.g. Back-End Programming) | None | **Yes, confirmed by direct fetch** — `<description>` contains the complete HTML posting (responsibilities, qualifications, "To apply:" link), not a teaser | UNVERIFIED — no explicit ToS clause for feed consumption found, but a public RSS feed is by nature meant for external consumption | Global remote board with a dedicated back-end programming category; PL-specific volume UNVERIFIED (roles are "remote," not country-pinned) | **Viable** — simplest integration of all, standard RSS, full text confirmed directly |
| EURES | No official API. `eures.europa.eu` has no developer/API links on its own site. A JSON backend the *website itself* calls (`europa.eu/eures/api/jv-searchengine/...`) is reverse-engineered by a third party, whose own README states: "not affiliated with or endorsed by the European Commission ... not an official API and has no guaranteed stability, rate limits, or support" ([github.com/rorar/EURES-API-Documentation](https://github.com/rorar/EURES-API-Documentation)) | n/a | UNVERIFIED — a direct fetch of the search endpoint returned HTTP 500 in this session | No sanctioned third-party use documented | Would be strong (it is the EU's own labour-mobility portal, 31 countries) | **Not viable today** — the data the EU institutionally owns is not exposed through anything sanctioned; what exists is an undocumented internal endpoint, not a published feed |
| HN "Who is hiring" (Algolia) | Official Algolia-run search over Hacker News, [hn.algolia.com/api](https://hn.algolia.com/api), open-sourced at [github.com/algolia/hn-search](https://github.com/algolia/hn-search) | None | **Yes, structurally** — a "who is hiring" listing *is* the raw top-level comment text; searching and reading a comment returns the whole posting, there is no separate summary/full-text split | UNVERIFIED — exact rate-limit and ToS text could not be pulled from a direct fetch in this session (page returned minimal content); the service's official, Algolia-operated status is well established | Monthly US-heavy thread; Poland/remote-EU Java/Kotlin roles appear but are a small fraction of each thread, and every field (seniority, tech, location) is free text requiring extraction rather than a structured filter | **Marginally viable** — technically clean (no key, full text by construction) but low PL/remote-EU signal density and no server-side filtering, so most of every poll would be irrelevant noise the pipeline still has to extract-and-discard |
| Himalayas | Official, [himalayas.app/docs/remote-jobs-api](https://himalayas.app/docs/remote-jobs-api), browse at `/jobs/api`, search at `/jobs/api/search` | None | **Yes** — "full description as sanitized HTML" | Must "include a visible link back to himalayas.app and mention that the data is sourced from Himalayas" | Search filters by country, seniority (Entry/Mid/Senior/Manager/Director/Executive), and free-text keyword — Java/Kotlin reachable via keyword, no dedicated tech filter | **Viable** — cleanest documented contract of the general boards: explicit seniority filter plus keyword search, full HTML text, no auth |
| Jobicy | Official, [jobicy.com/api/v2/remote-jobs](https://jobicy.com/api/v2/remote-jobs), README at [github.com/Jobicy/remote-jobs-api](https://github.com/Jobicy/remote-jobs-api) | None | **Yes** — `jobDescription` field is "Full job description in HTML" | Attribution required, no re-syndication to competing job networks, "do not schedule automated polling more frequently than once per hour" | `geo` and `industry` filters exist, plus a `tag` field searchable by title/description — Java/Kotlin reachable via tag, PL/EU-specific volume UNVERIFIED | **Viable** — same shape as Himalayas, slightly weaker filtering (no seniority parameter documented) |

## The email-forward path

Every major Polish board offers a saved-search alert by email — Pracuj.pl calls it JobAlert and
documents how to manage it in its own help centre ([pomoc.pracuj.pl](https://pomoc.pracuj.pl/hc/pl/articles/360039553733-Zapisane-wyszukiwania-JobAlert)),
and JustJoin.IT and NoFluffJobs offer equivalent subscriptions. That confirms the mechanism exists,
consented to by the user, and sidesteps the terms-of-service question entirely — parsing your own
inbox is not scraping anyone's site.

What it does **not** settle is the question that actually decides whether it is worth building: does
the alert email carry the full offer prose, or a teaser and a link back to the site. No primary
source answers this — board help centres document how to turn alerts on and off, not what the message
body contains, and this could not be verified without actually subscribing to each board's alerts and
inspecting a delivered email, which was out of scope for this pass. This is genuinely
**UNVERIFIED**, not a confident guess either way, though the general shape of the job-board industry
— alert emails exist to drive a click back to the site's own ad-supported page — makes a teaser-plus-
link format the more likely outcome for at least the Polish boards, which is worth stating as a
working assumption to be tested, not a finding.

If the content turns out to be full text, the ingestion mechanism is cheap: a dedicated mailbox or a
per-user forwarding address, polled over IMAP on a schedule, feeding the same `OfferTextScrubber` and
extraction pipeline that already handles pasted text — no new parsing logic beyond an email-to-text
step. If the content turns out to be a teaser, this path is a worse version of the same scraping
problem the roadmap already ruled out, because following the link to get the real text is scraping
by another name. **Verdict: credible in principle, unverified in the one detail that decides it** —
worth a five-minute manual check (subscribe to one alert from Pracuj.pl and JustJoin.IT, read the
raw email) before any code is written, rather than worth building speculatively.

## Recommendation

Start with **Arbeitnow, Himalayas, and WeWorkRemotely's RSS feed**. All three were confirmed by direct
inspection to return the complete offer description rather than a snippet, none requires anything
beyond a courteous polling interval, and Himalayas additionally offers a documented seniority filter
that the other general boards lack. Treat Jobicy and Remotive as a natural second wave — same shape,
tighter rate limits (Jobicy: at most hourly; Remotive: at most a few times a day) — and leave RemoteOK
and the HN Algolia thread for later: RemoteOK's own endpoint blocked a plain fetch in this session,
and HN's per-offer relevance to Poland/remote-EU Java/Kotlin is low enough that it is mostly noise the
pipeline would have to filter out on every poll.

A first integration is small. Per source it is: one scheduled poll (`@Scheduled`, at the interval each
source's terms specify — daily is safe for all three starting sources), one DTO mapping the source's
JSON or RSS fields onto the existing offer shape (raw text + metadata), and a dedup check against the
**existing content hash** the `offer` module already computes for pasted text — recomputing that same
hash over the ingested description before insert means a re-poll of an already-seen listing is a
no-op rather than a duplicate `Offer` row. No new module concept is needed: this is `offer` gaining an
ingestion adapter, not a new bounded context. The one piece of existing machinery to reuse
deliberately is `OfferTextScrubber` — full-text descriptions from any of these boards can contain a
recruiter's email or phone number exactly the way pasted text can, and the scrubber needs to run on
ingested text before it ever reaches a prompt, the same as it does today.

On the two constraints from `CLAUDE.md`: none of these sources requires a model to infer a fact — they
return prose to extract from, exactly like pasted offer text, so the extraction pipeline's existing
discipline (deterministic diff, no invented experience) is untouched. And because the offer text is
third-party prose, not the candidate's own profile, the identifier boundary that matters is the same
one `OfferTextScrubber` already enforces on pasted offers: strip recruiter contact details before the
text reaches a model, regardless of whether that text arrived by paste, poll, or forwarded email.

## What I could not verify

- The exact terms-of-service text for Pracuj.pl, theprotocol.it, and Bulldogjob — their `/regulamin`
  pages returned HTTP 403 to a direct fetch in this session, so the "not viable" verdict for those
  three rests on the absence of any documented API/feed, not on a read anti-scraping clause.
- NoFluffJobs's site could not be fetched directly at all in this session; the "no official API" call
  rests on an absence of results across several targeted searches, not a direct site inspection.
- RemoteOK's exact JSON field names and its `/legal` terms text — both fetches returned HTTP 403,
  so the "does it return full description text" question for RemoteOK is genuinely open.
- Whether Arbeitnow's listings meaningfully include Poland-located roles versus being effectively
  Germany/DACH-only — no primary source broke down volume by country.
- The precise rate-limit and terms-of-service wording for the HN Algolia search API — a direct fetch
  of `hn.algolia.com/api` returned too little content to quote from; its official, Algolia-operated
  status is well established elsewhere, but the exact numbers were not confirmed first-hand here.
- Whether the EURES internal search endpoint would return full description text if it worked — the
  direct fetch attempted in this session returned HTTP 500, so this was never actually observed.
- The content of Polish job boards' alert emails (full text vs. teaser-and-link) — see the
  email-forward section above; this needs a live subscription to answer, not a documentation search.
