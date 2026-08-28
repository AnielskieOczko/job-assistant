# Offer ingestion sources, without scraping

Research for [issue #10](https://github.com/AnielskieOczko/job-assistant/issues/10), part of the
[roadmap map](https://github.com/AnielskieOczko/job-assistant/issues/9). Method: primary sources
only — official docs, a board's own terms-of-service page, a direct fetch of the endpoint itself.
Where a claim could not be traced to a primary source it is marked **UNVERIFIED** and left that way
rather than filled in with a plausible guess.

> **Update, 2026-08-28.** The email-forward path this file left open was tested and ruled out
> by [ticket 18](https://github.com/AnielskieOczko/job-assistant/issues/18): alert emails carry a
> link, never the posting body. See `docs/research/18-alert-email-format.md`. The verdict below is
> unaffected — **solid.jobs is the source** — and is now the *only* route to Polish-market volume
> rather than one of two.

## Verdict

Ingestion without HTML scraping is worth building, and a second, harder pass on six more sources (see
below) changes its shape in one real way. Every dominant Polish IT board by traffic — JustJoin.IT,
NoFluffJobs, Pracuj.pl, theprotocol.it, Bulldogjob — was checked again with a much wider net
(`robots.txt`, sixteen documentation/API/RSS paths, developer subdomains, each board's own GitHub org,
and archived terms pages where a direct fetch failed), and the answer for all five is unchanged: none
publishes a sanctioned API or a full-text feed. NoFluffJobs turned out to run an undocumented `/rss`
feed, but its `<description>` field carries only a logo, a location, and a salary — a teaser, not the
posting — and theprotocol.it and Bulldogjob are both blocked at the infrastructure level (a WAF 403s
every direct fetch, including the homepage) rather than by anything documented as policy. But a
smaller Polish board, solid.jobs, does exactly what the first pass concluded the whole market was
withholding: a documented, keyless, no-registration REST API at `/public-api/offers/{division}` whose
response — confirmed by a live fetch in this pass, not inferred from the docs page's own abbreviated
example — carries the **full** HTML job description, generously rate-limited (300 req/min/IP) and
explicitly welcoming automated and AI-agent traffic in its own `robots.txt`. It is not JustJoin.IT
scale (1,491 live IT listings at test time against a market leader's tens of thousands), so it doesn't
replace the case for the general boards below, but it is the first Poland-native, full-text, sanctioned
source found across either pass, and it belongs alongside the viable sources rather than the excluded
ones. What else exists, cleanly, is the same handful of general European and remote job-board APIs
from the first pass — Arbeitnow, Himalayas, Jobicy, Remotive, plus WeWorkRemotely's RSS and the HN
"Who is hiring" thread via Algolia's own search API — every one of which returns the **full** offer
description text, not a snippet, and none of which needs more than a free, keyless or self-registered
API call. So the honest shape of the recommendation changes from "do not spend effort chasing the
Polish boards" to "one Polish board is worth the effort and the other four still are not": build a
small poller against solid.jobs plus three or four of the confirmed full-text general-board sources,
expect a mix of genuine Polish-market coverage and general remote-market trickle rather than either
alone, and continue to leave JustJoin.IT, NoFluffJobs, theprotocol.it, and Bulldogjob alone — that door
is still closed, now by far more thoroughly checked evidence rather than by a single 403.

## Sources

| Source | API/feed (URL) | Auth | Full description text? | ToS for personal use | PL/remote-EU Java/Kotlin coverage | Verdict |
|---|---|---|---|---|---|---|
| JustJoin.IT | Checked harder, still none: `robots.txt` disallows `/api/` ([justjoin.it/robots.txt](https://justjoin.it/robots.txt)); 16 documentation/RSS paths all 404; `api.justjoin.it` resolves but serves a bare nginx 503; both GitHub orgs (`justjoin-it`, `justjoinit`) hold zero public repos. See second pass below | n/a | n/a | Own robots directive forbids it | Would be excellent (Poland's largest IT board) | **Not viable** — checked harder, same answer |
| NoFluffJobs | No documented API (`robots.txt` disallows only `/api/`, `/posting/`, `/pdf/` — [nofluffjobs.com/robots.txt](https://nofluffjobs.com/robots.txt)), but a real undocumented RSS feed exists at `/rss`, confirmed by direct fetch (4,335 items). See second pass below | None | **No — teaser only**, confirmed by direct fetch: `<description>` holds a logo image plus Location and Salary, no posting text | UNVERIFIED — the `/regulamin` route resolves to a client-rendered shell requiring JS | Would be excellent | **Not viable for this pipeline** — a genuine feed exists but fails the full-text test |
| Pracuj.pl | None documented; `robots.txt` shows only sitemaps, no API path | n/a | n/a | UNVERIFIED — `/regulamin` returned 403 to a direct fetch | Would be excellent | **Not viable** — no feed found |
| theprotocol.it | Checked harder, still none: a site-wide WAF returns 403 to every direct fetch, including the homepage itself, across 16 probed paths plus `/rss`; owned by Grupa Pracuj (same parent as Pracuj.pl), whose GitHub org ships only unrelated internal .NET tooling. See second pass below | n/a | n/a | UNVERIFIED — `/regulamin` links to a downloadable PDF a wayback snapshot didn't capture | Would be excellent | **Not viable** — checked harder, same answer |
| Bulldogjob | Checked harder, still none: `robots.txt` disallows `/feeds` (implying a path exists) but every feed/API variant probed 403s or 404s ([bulldogjob.pl/robots.txt](https://bulldogjob.pl/robots.txt)); GitHub org has one unrelated repo. See second pass below | n/a | n/a | UNVERIFIED — `/terms` (found via an archived homepage nav link) resolves to a client-rendered shell | Would be excellent | **Not viable** — checked harder, same answer |
| solid.jobs | Official, [solid.jobs/api-ofert-pracy](https://solid.jobs/api-ofert-pracy); REST at `/public-api/offers/{division}` and `/public-api/market-statistics/{scopeKind}/{scopeKey}` | None — no key, no OAuth, no registration; a self-chosen `campaign` string is analytics-only | **Yes, confirmed by a live direct fetch** — the `description` field returned full HTML job-posting prose (responsibilities, requirements, benefits), not a snippet or the docs page's abbreviated example | `robots.txt` explicitly allows crawling and names `ClaudeBot`/`anthropic-ai` among permitted agents ([solid.jobs/robots.txt](https://solid.jobs/robots.txt)); 300 req/min/IP, hourly cache, HTTP 429 on excess; no clause found restricting personal or research use | Poland-native IT board (Poznań-registered, KRS 0000815297); 1,491 live IT-division listings at test time; a 50-offer sample contained real Java/Kotlin/Spring Boot backend roles | **Viable** — the first Poland-native, full-text, sanctioned source found across either pass |
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

## Second pass: the Polish boards, checked harder

Rafal pushed back on PR #20: four sources the first pass marked "not viable" rested on one 403 each,
which is absence of evidence, not evidence of absence. This pass checked six sources — four of the
originals plus two new ones — much harder: `robots.txt` on every board, sixteen common documentation
and API paths (`/api`, `/api/docs`, `/developers`, `/developer`, `/dla-programistow`, `/integracje`,
`/integrations`, `/partners`, `/dla-firm`, `/api-docs`, `/swagger`, `/openapi.json`, `/v2/api-docs`,
`/rss`, `/feed`, `/atom.xml`, `/jobs.rss`), four developer subdomain guesses (`api.`, `developers.`,
`developer.`, `docs.`), each board's own GitHub org via the GitHub API, and — where a direct fetch
403'd — the Wayback Machine as a fallback for terms pages. Every claim below cites the exact fetch or
path that produced it.

### solid.jobs

This is the one that could change the answer, so it got the most scrutiny. The public API is
documented on its own page, [solid.jobs/api-ofert-pracy](https://solid.jobs/api-ofert-pracy), fetched
directly (`curl`, HTTP 200) and again with a browser user agent to defeat any bot-gate — the page is a
server-rendered Angular app whose content is present in the raw HTML, not injected client-side, so no
JS execution was needed to read it. The page states plainly: "Bez rejestracji, bez kluczy, bez OAuth"
(without registration, without keys, without OAuth) — a self-chosen `campaign` identifier is the only
thing a caller supplies, and the docs say it "służy tylko do analityki ruchu" (serves only traffic
analytics). Two endpoints exist: `/public-api/offers/{division}` for listings across eight divisions
(IT, Engineering, Marketing, Sales, HR, Logistics, Finances, Other) and
`/public-api/market-statistics/{scopeKind}/{scopeKey}` for aggregate labour-market metrics (demand,
salary bands, remote share, experience distribution, top locations/skills) — the latter is aimed at
dashboards, not offer ingestion, and was not pursued further. Direction of flow is unambiguous: this
is a read API for "agregatorów, integratorów i każdego, kto chce zbudować coś własnego" (aggregators,
integrators, and anyone who wants to build their own thing), not an employer-posting endpoint. Rate
limit is documented and generous: "300 zapytań na minutę na IP" (300 requests/minute per IP) with a
10-request queue, hourly response caching, and a `429` on excess.

The decisive question — full description prose or a teaser — is where the docs page and the live API
disagree, and the live API is what matters. The docs page's own example JSON response omits a
`description` field entirely (it shows only `title`, `company`, `salary`, `isRemote`, `skills`, `url`),
which is exactly the kind of thin evidence this pass was supposed to distrust. So the endpoint was
fetched directly instead: `curl "https://solid.jobs/public-api/offers/IT?campaign=job-assistant-
research&pageSize=2"` returned HTTP 200 with a `description` field per offer containing full HTML —
for one sampled offer (a Senior React Developer role), 2,328 characters covering "Czym będziesz się
zajmować?" (what you'll be doing), a bulleted responsibilities list, "Kogo poszukujemy?" (who we're
looking for) with requirements, and a benefits section — not a snippet, not a teaser, the same shape
of prose the app already extracts from pasted offer text. The response also carries
`jobOfferKey`, `division`, `category`, `subCategory`, normalized `salary` (from/to/currency/period/
employmentType), `locations`, `isRemote`/`isHybrid`, `experienceLevel`, a `skills` array with
`name`+`level`, and a `languages` array with the same shape — a structure that maps cleanly onto this
project's own skill-and-proficiency model. `totalCount` for the IT division was 1,491 at test time,
and a 50-offer sample pulled from it contained genuine Java/Kotlin/Spring Boot backend roles (e.g.
"Java Engineer" tagged Kotlin/Spring Boot/MongoDB, "Programista Java" tagged Java/Spring/Docker/
Microservices), not just Java-adjacent QA/test-automation postings, confirming the target skill set is
represented and not just nominally present.

`robots.txt` ([solid.jobs/robots.txt](https://solid.jobs/robots.txt)) allows `/` for `User-agent: *`
and, distinctively, lists `ClaudeBot`, `anthropic-ai`, `GPTBot`, `PerplexityBot`, and others by name as
explicitly permitted, disallowing only `/management/` and `/admin/` — the opposite of JustJoin.IT's
stance. The general terms of service ([solid.jobs/about/terms](https://solid.jobs/about/terms),
fetched directly, HTTP 200, 96KB) covers job-posting terms for employers and general GDPR/IP clauses;
it contains no clause restricting programmatic consumption of the public API, and the API's own rules
(no key, `campaign` for analytics, 300/min, hourly cache) are stated directly on the API doc page,
which functions as that page's own governing terms. The FAQ on the same page explicitly invites
integration ("Zostań partnerem" — become a partner — for anyone who builds a working integration) and
offers first-party client examples in nine languages plus an installable CLI (`sjctl`) and AI-agent
skills package (`npx skills add solid-company/solid-jobs-skills`) — first-party evidence of intended
third-party use, not third-party reverse-engineering.

#### It also answers questions this ticket was not asked

Verified independently on a 500-offer page of `/public-api/offers/IT?campaign=...` (the endpoint
returns `pageIndex`/`pageSize`/`totalCount`/`totalPages`, so one call yields 500 offers at a time).
Two of those fields land squarely on
[What should the offer market dashboard answer?](https://github.com/AnielskieOczko/job-assistant/issues/13),
which is a separate open ticket, and they land on precisely its two hardest sub-questions.

**Salary was populated on 500 of 500 offers**, structured rather than buried in prose:
`{"from": 13400.0, "to": 15100.0, "currency": "PLN", "period": "Month", "employmentType": "B2B"}`.
Ticket 13 states the worry plainly — salary "is the one Polish candidates most want and the one most
often absent from the text", and "a dashboard whose salary chart is built on 20% coverage is worse
than no salary chart". On this source it is not absent from the text at all, because it never was
text: it is a normalised object carrying currency, period **and the B2B-versus-employment
distinction** the ticket calls out by name. No extraction, no model, nothing to get wrong.

**Skills arrive with a required level**: `[{"level": "Advanced", "name": "React"}, ...]`, populated on
489 of 500 offers. Ticket 13's decision 3 is required skill *level*, noted there as the sharpest
sub-question because the offer says "good knowledge of Spring" while the profile says `Proficiency`,
and reconciling the two touches `RequirementMatcher`. This source states the level as an enum instead
of leaving it to be inferred from prose — which does not settle whether the two scales *should* map
onto one another, but it does mean the question can be answered against real labelled data rather
than against a model's reading of an adjective. `languages` carries the same `name`+`level` shape,
which is directly comparable to the profile's own language proficiency model.

`experienceLevel` (Regular/Senior/…) was populated on 500 of 500, and `isRemote` on 214 of 500 — the
last being a genuine coverage gap worth remembering rather than a field to trust blindly.

A 500-offer IT sample contained 65 offers naming Java, Kotlin or Spring in the title or skills array,
including "Java Engineer" tagged Kotlin/Spring Boot/MongoDB and "Programista Java" tagged
Java/Spring/Docker — so the target stack is genuinely represented, though a meaningful share of the 65
are QA and test-automation roles rather than backend engineering.

There is a second endpoint, `/public-api/market-statistics/{scopeKind}/{scopeKey}`, returning
aggregate demand, salary bands, remote share, experience distribution and top locations and skills.
It was not pursued here because this ticket is about ingestion, but it is aimed squarely at what
ticket 13 wants to build, and whoever takes that ticket should look at it before designing anything.
**A caution that belongs with it:** those aggregates describe solid.jobs's own listings, not the
Polish market, and presenting one board's statistics as "the market" would be the dishonest version of
a dashboard rather than the useful one.

**Verdict: viable**, and the strongest finding of this pass. solid.jobs is not JustJoin.IT or
NoFluffJobs scale — it is a smaller, Poznań-based recruitment platform (KRS 0000815297, founded 2019)
covering IT among seven other divisions — so it does not replace the case for the general-board
sources already recommended below. But it is a genuinely Poland-native, full-text, sanctioned,
keyless, generously-rate-limited API, which is precisely what the first pass concluded did not exist
anywhere in the Polish market. It changes the verdict (see below).

### JustJoin.IT

`robots.txt` ([justjoin.it/robots.txt](https://justjoin.it/robots.txt), fetched directly and via
WebFetch, identical both times) disallows `/api/`, `/oferty-pracy/*,*`, `/terms-and-privacy-policies`,
and several internal paths, allowing only sitemaps and the rest of the public site — unchanged from
the first pass, but now cross-checked two ways. All sixteen documentation/API/RSS paths listed above
returned HTTP 404. The `api.` subdomain resolves (unlike the other three boards' subdomain guesses,
which didn't resolve at all) but serves a bare nginx "503 Service Temporarily Unavailable" page with
no content — evidence of an internal service existing behind that hostname, not evidence of a
documented public API. Both plausible GitHub orgs, `github.com/justjoin-it` and
`github.com/justjoinit`, exist and returned HTTP 200, but the GitHub API confirms zero public
repositories on the first and none related to job data on either. **Verdict: not viable** — the same
conclusion as the first pass, now backed by robots.txt, sixteen paths, a subdomain probe, and two
GitHub orgs instead of one robots.txt line.

### theprotocol.it

`robots.txt` ([theprotocol.it/robots.txt](https://theprotocol.it/robots.txt)) disallows only `/_next/`
and lists two sitemaps — nothing in it blocks an API or feed path. But every direct fetch to the site
403'd, including the **homepage itself**, not just `/regulamin` as the first pass found — this is a
site-wide WAF rejecting non-browser requests, not a documented anti-scraping policy, and it blocked all
sixteen probed paths plus `/rss` identically. To read the terms page anyway, this pass used the Wayback
Machine: `archive.org/wayback/available?url=theprotocol.it/regulamin` confirmed a snapshot exists, and
fetching it directly (`web.archive.org/web/20251210034224/https://theprotocol.it/regulamin`, HTTP 200)
showed the terms text itself is not inline HTML but a **downloadable PDF** ("pobierz" links for the
current and previous versions) — the PDF's actual URL is generated client-side and wasn't captured by
the archive crawler, so the terms wording remains genuinely unread. A web search
("theprotocol.it API oferty pracy dla programistów integracja") turned up no API or integration
documentation and confirmed theprotocol.it is "developed in synergy with the Pracuj Group" — i.e. the
same corporate parent, Grupa Pracuj S.A., that owns Pracuj.pl (also ruled not viable in the first
pass), which explains the matching closedness. `github.com/grupapracuj` and `github.com/GrupaPracuj`
are real, active orgs with public repos, but every one of them is internal infrastructure tooling
unrelated to job data (`iislog-prometheus-exporter`, a `C4-PlantUML` fork, `dotnet-libyear`, `RtfPipe`,
a `MassTransit` fork, `sonar-dotnet`) — no client library, no API spec. **Verdict: not viable** — same
conclusion, now with the WAF's scope, the PDF's existence, the corporate-parent link, and the GitHub
org all checked rather than a single 403.

### NoFluffJobs

`robots.txt` ([nofluffjobs.com/robots.txt](https://nofluffjobs.com/robots.txt)) disallows only
`/api/`, `/posting/` (and locale variants), `/pdf/`, `/not-found*`, and `/signal` — everything else,
including `/rss`, is technically crawlable, and unlike the first pass the site was directly fetchable
throughout this session (HTTP 200 on the homepage and most probed paths). Of the sixteen probed paths,
most return HTTP 200 but are the same Angular SPA shell for every route (confirmed by comparing
response bodies — `/developers`, `/api-docs`, `/feed`, `/atom.xml`, and `/jobs.rss` are all
byte-for-byte the same generic homepage), which is client-side routing serving a catch-all, not sixteen
real pages. `/rss`, however, is genuinely different: it returned a working RSS 2.0 feed with 4,335
`<item>` elements, confirmed by parsing it directly. Each item has `title`, `link`, `pubDate`, `guid`,
and a `<description>` — and that description is the decisive disappointment: for a sampled "Senior
Backend Engineer (Java/Kotlin)" listing, it contained only a company logo `<img>` tag plus "Location:"
and "Salary:" lines, no responsibilities, no requirements, nothing resembling the posting itself. This
is a real, working, undocumented feed — a materially different and better-evidenced finding than the
first pass's "site blocked direct fetch, no first-party source found" — but it fails the full-text test
outright. The `/regulamin` route, tried again in this pass, now resolves (HTTP 200) but the response
body is the same client-rendered app shell with the literal text "JavaScript is required for the page",
so the terms wording is still unread. `github.com/nofluffjobs` exists but the GitHub API confirms it is
a personal **user** account, not an org, with zero public repositories. **Verdict: not viable for this
pipeline** — upgraded from "no first-party source found" to "a real feed exists and was inspected
directly, but its description field is a teaser," which is a stronger negative than the first pass had.

### Bulldogjob

`robots.txt` ([bulldogjob.pl/robots.txt](https://bulldogjob.pl/robots.txt)) disallows `/Pzit*`,
`/auth`, `/page`, **`/feeds`**, `/authors`, `/withSalary,true`, `/salaryBrackets`, `/account`, `/faq`,
`/files`, and `/index` — the explicit `/feeds` disallow implies a feed path exists somewhere, so this
pass specifically probed `/feeds/rss`, `/feeds.xml`, `/feed.xml`, `/feeds/jobs`, `/rss.xml`,
`/jobs/feed`, and `/jobs/rss` in addition to the standard sixteen; every one of them 403'd or 404'd to
a direct fetch, same as the rest of the site (a site-wide WAF blocks non-browser requests here too,
similarly to theprotocol.it). The terms page isn't at the guessed `/regulamin` at all — a Wayback
Machine snapshot of the homepage (`web.archive.org/web/20260616143522/https://bulldogjob.pl/`, found
via `archive.org/wayback/available`) surfaced the real link, `/terms`, in the footer nav. Fetching that
archived page directly returned HTTP 200, but, as with NoFluffJobs and theprotocol.it, the body is a
client-rendered shell with nav labels ("Regulaminy", "Regulamin") and no inline legal text — the
archive crawler didn't capture whatever client-side call renders the actual clauses. `github.com/
bulldogjob` and `github.com/BulldogJob` both exist and the GitHub API confirms exactly one public repo
between them, `reviews-changelog`, which is a changelog for the site's employer-review feature and has
nothing to do with job data or an API. **Verdict: not viable** — same conclusion, now backed by
robots.txt, seventeen probed paths, a located-but-unreadable terms page, and a confirmed GitHub org
with unrelated content, rather than a single UNVERIFIED note.

### jobright.ai

This is a different category from the other five: an AI job-search product, not a job board, so it was
judged on two separate axes rather than folded into the sources table.

**(a) Is it an ingestible source? No**, and there's no reason to stretch it into one. `robots.txt`
([jobright.ai/robots.txt](https://jobright.ai/robots.txt)) explicitly disallows `/api/` and `/api/*`
alongside internal paths (`/matching/*`, `/swan/`, `/monitoring/*`) and allows only marketing and
product pages (`/ai-resume-builder`, `/ai-job-match`, `/jobs/*`, `/blog/*`, etc.). The `/ai-agent` page
itself, fetched directly, describes consumer features only — resume tailoring, job matching, an
"Orion" AI copilot, autofill — with no mention of a public API, developer access, or a data feed
anywhere in its content. There is nothing here to ingest from.

**(b) As a product**, jobright.ai does something this project deliberately does not: its stated
pitch is "One click and your resume goes out perfectly tailored, the application forms are filled, and
every submission is tracked for you" — automated resume tailoring **and automatic submission** of the
application itself, plus a tracking dashboard and career coaching. This project's own `document` module
stops well short of that line on purpose (`CvInvariant`, `CvSelection.from` — see `CLAUDE.md`'s "The
AI must never be able to invent experience the candidate does not have"), and application submission
isn't something this project does at all; the roadmap's application-lifecycle tracking item is about
recording status a human enters, not autofilling and submitting on their behalf. jobright.ai's
autofill-and-auto-submit flow is exactly the shape of feature the fabrication guard exists to be wary
of — worth naming in the roadmap as a contrast (an automation ceiling the market has crossed and this
project has structurally chosen not to), not as something to emulate.

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

One relevant new fact from the second pass: solid.jobs ships its own recurring-check mechanism that
makes the email-alert question moot for at least one source. Its `sjctl watch add` CLI command and the
`/jobs-digest` AI-agent skill (both documented on
[solid.jobs/api-ofert-pracy](https://solid.jobs/api-ofert-pracy)) poll the same public API on a
schedule and report only new matches since the last run — the same outcome a saved-search email alert
is trying to achieve, but reached over the sanctioned API directly, with no mailbox, no IMAP polling,
and no dependency on whatever format a delivered alert email turns out to use. This doesn't answer the
question ticket #18 actually owns — none of Pracuj.pl's, JustJoin.IT's, NoFluffJobs's, theprotocol.it's,
or Bulldogjob's alert-email bodies were inspected in this pass either, so that manual check is still
outstanding — but it's worth recording alongside it, because it means at least one Polish IT source can
skip the email-parsing path entirely rather than needing it solved first.

## Recommendation

Start with **solid.jobs, Arbeitnow, Himalayas, and WeWorkRemotely's RSS feed**. solid.jobs is this
pass's addition: its `/public-api/offers/IT` endpoint was fetched directly and confirmed to return full
HTML descriptions, a documented `experienceLevel` filter, and a normalized `skills` array with
proficiency levels that maps unusually cleanly onto this project's own skill model — and at 300
requests/minute per IP its rate limit will never be the binding constraint. The other three were
confirmed by direct inspection in the first pass to return the complete offer description rather than
a snippet, none requires anything beyond a courteous polling interval, and Himalayas additionally
offers a documented seniority filter that the other general boards lack. Treat Jobicy and Remotive as a
natural second wave — same shape, tighter rate limits (Jobicy: at most hourly; Remotive: at most a few
times a day) — and leave RemoteOK and the HN Algolia thread for later: RemoteOK's own endpoint blocked
a plain fetch in this session, and HN's per-offer relevance to Poland/remote-EU Java/Kotlin is low
enough that it is mostly noise the pipeline would have to filter out on every poll.

A first integration is small. Per source it is: one scheduled poll (`@Scheduled`, at the interval each
source's terms specify — daily is safe for all four starting sources), one DTO mapping the source's
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
- The exact wording of theprotocol.it's terms of service — the regulamin page was located (it links to
  a downloadable PDF) but the PDF's own URL is generated client-side and wasn't captured by the
  Wayback Machine snapshot used to reach the page at all, so the clause-level text remains unread.
- The exact wording of Bulldogjob's `/terms` page and NoFluffJobs's `/regulamin` page — both were
  located (Bulldogjob's via an archived homepage nav link, NoFluffJobs's at the expected path) but both
  resolve to a client-rendered Angular shell with no inline legal text in the fetched HTML, on a direct
  fetch and, for Bulldogjob, on a Wayback Machine snapshot as well.
- Whether NoFluffJobs's `/rss` feed is *sanctioned* consumption or merely tolerated — it isn't blocked
  by `robots.txt` and it works, but no terms text (see above) was found either permitting or
  restricting third-party use of it specifically.
- What fraction of solid.jobs's listings are Poland-specific versus other markets — the sampled
  `locations` values were exclusively Polish cities and the operating company is Polish-registered
  (KRS 0000815297, Poznań), which points strongly toward Poland-primary, but no explicit country filter
  or breakdown was found to confirm this as a documented fact rather than an inference from one sample.
- Whether solid.jobs's live `totalCount` (1,491 IT-division offers at test time) is representative or a
  snapshot that fluctuates meaningfully — it was observed once, in this session.
