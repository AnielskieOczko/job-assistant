# jobright.ai portal analysis

Research for [issue #21](https://github.com/AnielskieOczko/job-assistant/issues/21), which asked for
a competitive read of [jobright.ai](https://jobright.ai/) — a job-search product issue #21 describes
as having "a lot in common with" this app — to surface features worth naming on the roadmap. It is
not a wayfinder-map ticket: it stands alone, feeding
[`docs/roadmap-wayfinding.md`](../roadmap-wayfinding.md) rather than being tracked inside it.

Method: primary source first. Every jobright.ai marketing/feature page cited below was fetched
directly on 2026-08-29. Where jobright.ai's own pages didn't settle a question — its candidate
pricing page 404s, and its own blog post quotes two different prices for the same tier — that gap is
stated as a gap, and third-party review sites are cited only as **secondary**, labelled as such, never
substituted for a primary claim silently. This repository's own prior research already touched
jobright.ai once, from a different angle: `docs/research/10-offer-ingestion-sources.md` judged it as
a *potential ingestion source* (verdict: not one, and not worth stretching into one — its
`robots.txt` disallows `/api/`) and, in passing, quoted its auto-apply pitch as a contrast worth
naming later. This file is that "later": a full read of the product rather than a one-paragraph
aside.

## What jobright.ai is

A consumer job-search platform-plus-agent, not a job board. It maintains its own listings corpus
("8,000,000+ total jobs indexed," "400,000+ new jobs daily" — [jobright.ai/](https://jobright.ai/))
and layers matching, tailoring, application automation, a referral network, and interview prep on
top of it. The tagline on its dedicated agent page states the scope directly: "Jobright Agent
streamlines your entire job search — proactively matching roles, customizing your resume, and
applying for you" ([jobright.ai/ai-agent](https://jobright.ai/ai-agent)).

It also runs a **separate employer-facing product**, an AI recruiter sold at $499/month per active
role with a two-week free trial
([jobright.ai/employers/pricing](https://jobright.ai/employers/pricing)). That side is a different
business (sourcing and vetting candidates for a company) and has no bearing on a candidate-side tool
like this one; it is noted here only so a reader does not mistake the recruiter pricing table for the
job-seeker product's pricing.

## Core workflow and features

### 1. AI job matching

Listings come from jobright.ai's own indexed corpus, not a live web search. Matching combines
"your target role and preferences" with "your resume, experience, and skills," and every job carries
a **Match Score** — the page claims the model is "trained on 10 million+ JDs keywords which ensures
the highest accuracy" but does not publish the scoring method itself
([jobright.ai/ai-job-match](https://jobright.ai/ai-job-match)). The "Orion" copilot (below) can
explain a given match on request, reasoning over "relevant experience, seniority, skills, potential
gaps, and the job highlights."

The same page claims active spam/fraud filtering on its own corpus: "Fake Job Filtering: We squash
fake jobs realtime to show you only the highest quality open roles," with listings refreshed "every
hour."

### 2. AI resume tailoring

Takes an uploaded resume plus a target job description as input and produces a rewritten,
job-specific resume: "upload resume → receive instant analysis → get AI-refined resume → download"
([jobright.ai/ai-resume-builder](https://jobright.ai/ai-resume-builder)). It claims ATS-format
compatibility ("ATS friendly resume") and "guides you on what should be added and eliminated for
optimizing skills," but the page does not say whether that means rewriting existing bullets, adding
new ones, or both — the wording stays vague on the mechanism. **No warning about
accuracy or fabrication risk appears anywhere on the page.** The homepage separately claims tailored
resumes render "in 6 seconds" ([jobright.ai/](https://jobright.ai/)).

### 3. One-click autofill and auto-apply

A browser extension fills and submits application forms on third-party ATS platforms. The
documented flow is four steps: "Install the Autofill Extension" → "Set up your profile" → "Open a
job application site & Click Autofill" → "Submit your application, done!"
([jobright.ai/job-autofill](https://jobright.ai/job-autofill)). It claims to work on "thousands of
ATS platforms" (unnamed) and quantifies itself at "10 million Applications Autofilled" and "5 hrs
Saved per User Every Week," explicitly enabling users to "apply to hundreds of jobs each day." A
Match Score is surfaced "before autofill," but the page names **no mandatory human review or
confirmation step between autofill and submission** — the fourth step is literally "Submit... done."
The `/ai-agent` page's own pitch names this as the product's headline claim: "One click and your
resume goes out perfectly tailored, the application forms are filled, and every submission is
tracked for you," alongside a stated "90% job search automation" and "3x increase in interviews
landed."

### 4. Application tracking

"Every job you apply to on Jobright is instantly saved to your applied list for seamless
organization" ([jobright.ai/tools/job-tracker](https://jobright.ai/tools/job-tracker)). Five
statuses are tracked: Applied, Interviewing, Offer Received, Rejected, Archived. Jobs applied
through the platform populate automatically; a job pasted or applied to elsewhere can be added
manually and the tool "parses the details and creates a job page for each one." No stated cap on
tracked jobs.

### 5. Insider referrals

Requires connecting a LinkedIn account ("Connect Your Linkedin" is step two of onboarding) and then
surfaces "past colleagues, classmates and more within a hiring team for any Job"
([jobright.ai/job-referral](https://jobright.ai/job-referral)). It is not automated outreach: the
product hands the user "Custom Outreach Templates" — "personalized cold outreach message
templates" — to send themselves, plus (implied) contact details. Claimed lift: "Increase response
rates by 3x" and "4x your interview chances with an insider referral."

### 6. "Orion" AI copilot

A persistent chat assistant, described as "24/7," that personalizes guidance by "analyzing the
information you provide about your career goals, skills, experience, and preferences based on your
onboarding questions, resume, and as much information you provide Orion"
([jobright.ai/orion-copilot](https://jobright.ai/orion-copilot)). It is positioned as the
explanation layer over the match score and the source of interview coaching ("Wow in Interviews with
Specific Company Insights"), and is stated to be trained on "10 MILLION" job descriptions.

### 7. Interview question bank

A separate, self-paced practice surface rather than a live mock-interview product: "6,656+ Real
Interview Questions" from "328+" companies, sourced from "verified interview experiences reported by
real candidates," tagged by category (System Design, Coding, Behavioral), company, and seniority,
each with "Step-by-Step Solutions & Insider Tips"
([jobright.ai/interview-landing](https://jobright.ai/interview-landing)). No live/synchronous
interview simulation is described on this page.

### Other named surfaces, not explored in depth here

The site also links `/tools/ai-job-assistant`, `/tools/cover-letter-generator`, `/tools/resume-helper`,
`/h1b-jobs` (a visa-sponsorship-filtered job list) and `/tnt` from its main navigation
([jobright.ai/](https://jobright.ai/)); these appear to be thinner wrappers or filtered views over
the same corpus and copilot rather than distinct product surfaces, but were not individually fetched.
A `/tools/fake-candidate-detection` and a standalone `/pricing` path both returned HTTP 404 on direct
fetch and are not live pages as of this research.

## Pricing and gating

jobright.ai's own candidate-facing pricing page could not be located as a stable, fetchable URL: both
`/pricing` and `/jobseekers/pricing` returned HTTP 404. The company's own blog, fetched directly,
names its paid tier at **two different prices in the same article** — "$29.99/month" in one section
and "$19.99 per month" in another
([jobright.ai/blog/is-jobright-worth-it-a-detailed-comparison/](https://jobright.ai/blog/is-jobright-worth-it-a-detailed-comparison/)) —
which is itself worth recording as a finding rather than resolving by picking one: **this repo could
not confirm jobright.ai's current candidate price from jobright.ai's own site.**

**Secondary sources** (third-party review sites, not verified against jobright.ai directly) converge
on a different, more recent figure: a free tier with small daily allowances (limited resume
generations, a handful of insider-connection emails, roughly one autofill per day, no live career
coach) and a paid "Turbo" tier at $39.99/month (also offered weekly at $17.99 and quarterly at
$89.99) unlocking unlimited use of every tool — the AI Agent, tailored-CV generation, insider
connection emails, live career-coach sessions, the LinkedIn email finder, one-click autofill, and
instant job alerts, with reviewers noting this is a recent increase from a prior $29.99/month and
that no free trial or refund is offered on Turbo. These figures come from aggregator/review sites
(`outapply.com`, `zplatform.ai`, and similar) surfaced via web search, not jobright.ai itself, and
should be read as indicative rather than confirmed.

Employer-side pricing, by contrast, is stated cleanly on jobright.ai's own page: $499/month per
active role, "10 to 20 interview ready candidates every week," a two-week free trial requiring no
credit card, and custom/contact-required pricing for multi-role annual plans
([jobright.ai/employers/pricing](https://jobright.ai/employers/pricing)).

## Terms of service: what it says about accuracy and automation

jobright.ai's terms of service, fetched directly, place the burden of accuracy on the user rather
than disclaiming the AI's output specifically. Section 32 ("Job Seekers") requires users to "provide
accurate and truthful profile, resume, and application information" and states "You are responsible
for conducting your own due diligence regarding employers and roles." Section 10 states user
"Contributions are not false, inaccurate, or misleading" and that the user is "solely responsible"
for them ([jobright.ai/legal/service](https://jobright.ai/legal/service)). **Nothing found in the
terms addresses AI-generated resume content specifically, and nothing requires a review or
confirmation step before an autofilled application is submitted** — the responsibility clause is
generic, not tied to the auto-apply mechanism described above.

## Comparison with this app

### Overlap — same problem, mostly convergent design

| Area | jobright.ai | This app |
|---|---|---|
| Requirement/skill matching against a candidate | Match Score, undisclosed method, single number | Deterministic `SkillCoverage` diff over a curated catalog — reproducible, not model-scored (`CLAUDE.md`, `RequirementMatcher`) |
| Resume tailoring per offer | AI-refined resume from uploaded resume + JD, mechanism undisclosed | `CvTailor` selects existing profile bullets/skills by id; `CvInvariant` rejects any generated document naming a skill the profile doesn't hold (`FabricatedClaimException`) |
| Application tracking | Applied / Interviewing / Offer / Rejected / Archived, largely auto-populated by its own apply flow | `ApplicationStatus`: `SAVED → ANALYZED → APPLIED → INTERVIEWING → REJECTED → OFFER`, human-entered (already shipped, per `docs/roadmap-wayfinding.md`) |
| Cover letter generation | Present via `/tools/cover-letter-generator` (not explored in depth) | `document` module generates cover letters under the same `CvInvariant`-style guard against naming an absent technology |
| Output in the candidate's own voice/data | Claims ATS-friendly formatting, no stated invariant against invented content | Explicit test-enforced invariant (`CLAUDE.md`'s first rule), not a prompt "asking nicely" |

### Deliberately not done here — and why that's a design choice, not a gap

- **Auto-apply / one-click submission.** jobright.ai's headline claim is "the application forms are
  filled, and every submission is tracked for you," with no described human-confirmation step before
  submit. `docs/roadmap-wayfinding.md`'s settled decisions rule this out explicitly: "Browser-automated
  auto-apply is out. Its downside is asymmetric — an agent sending applications under Rafal's name to
  the very people deciding whether to hire him — and its upside is about two minutes per application."
  jobright.ai's own terms of service, read above, place accuracy responsibility on the user without a
  submission-review checkpoint — exactly the asymmetry this app's roadmap named as the reason to stay
  out.
- **Scraping/holding a large multi-source job corpus and running your own matching over it.**
  jobright.ai indexes millions of listings itself. This app deliberately restricts ingestion to
  sanctioned sources (solid.jobs plus a handful of full-text APIs/feeds — see
  `docs/research/10-offer-ingestion-sources.md`) and never scrapes; it also does not attempt to
  replace the market-corpus half of jobright.ai's product — the market dashboard work
  (`docs/research/13-offer-market-dashboard.md`) answers "what a skill gap is worth," not "search
  every job board for me."
- **Multi-user, accounts, subscription billing.** jobright.ai is a multi-tenant SaaS with tiered
  pricing; this app is explicitly single-user, no authentication, bound to loopback (`CLAUDE.md`),
  and multi-user support is already out of scope per `docs/roadmap-wayfinding.md`.
- **Automated LinkedIn-based referral outreach.** jobright.ai requires connecting a LinkedIn account
  and surfaces contacts plus outreach templates. This app has no social-graph feature and no plan for
  one; it would also cut against "no direct identifier reaches a model" if implemented carelessly
  (a LinkedIn profile URL is exactly the kind of identifier `ProfileIdentityInspector` exists to catch
  — see the treatment of GitHub URLs in issue #50).
- **No fabrication guard visible on the tailoring path.** jobright.ai's resume builder page carries no
  language about accuracy or invented content, and its ToS assigns responsibility to the user rather
  than constraining the model. This app's `CvInvariant`/`CvSelection.from` pairing is the opposite
  choice: enforce the guarantee in code rather than assign the risk to the user by contract.

### Observations for the roadmap's "not yet specified" fog

These are named as things noticed, not recommendations — `docs/roadmap-wayfinding.md` reserves
ranking and scoping to ticket 15 and its successors.

- **Match explanation as a feature, not just a score.** jobright.ai's Orion explicitly answers "why
  does this job match me" on request, reasoning over gaps and seniority. This app's `SkillCoverage`
  already carries the *provenance* for a verdict (`impliedBy`/`relatedBy`) — the raw material for an
  equivalent explanation already exists; it isn't surfaced as a conversational feature today.
- **An interview-question bank keyed to the exact bullets/skills a tailored CV claims.**
  jobright.ai's interview bank is company-keyed but generic per company, not keyed to what a specific
  application claimed. `docs/roadmap-wayfinding.md` already parks a related, sharper idea under "Out
  of scope" — an interview-prep pack "generating likely interview questions from an offer's
  requirements and the exact bullets a tailored CV claimed" — noting it is "parked deliberately, not
  rejected" and would need its own map. jobright.ai's version is evidence that the generic half of
  that idea (a question bank by company/role, without the fabrication-adjacent bullet-tracing part)
  is a validated, simpler feature on its own.
- **A visible "time saved" or "automation rate" framing.** jobright.ai leans heavily on quantified
  claims (80% time saved, 90% automation, 3x interviews). This app already tracks an analogous but
  differently-motivated number — `dropped_bullet_count` / `dropped_skill_count` as a fabrication-rate
  signal (`CLAUDE.md`, "Generating a document") — which is an honesty metric rather than a marketing
  one. Whether a user-facing "time saved" figure is worth adding is untouched by this research; it's
  named only because jobright.ai treats it as a core selling point.
- **Fake/spam listing filtering.** Only relevant if this app ever ingests from a noisier, larger
  corpus than solid.jobs; not applicable to its current sanctioned-source list, which doesn't carry
  jobright.ai's spam problem to begin with.

## What I could not verify

- jobright.ai's current candidate-facing price and free-tier limits, from jobright.ai's own site —
  the two candidate pricing URLs guessed (`/pricing`, `/jobseekers/pricing`) both 404, and the
  company's own blog post states two different figures for what reads as the same tier. The $39.99/mo
  "Turbo" figure used above is from secondary review sites, not confirmed directly.
- The real identity of the "thousands of ATS platforms" the autofill extension claims to support —
  none are named on the page, and this could not be checked against a real form-fill (no account
  created, per this research's constraints).
- Whether the interview question bank is genuinely sourced from "real candidates" as claimed, or
  AI-generated and merely labeled that way — no sourcing methodology is described on the page beyond
  the phrase "verified interview experiences reported by real candidates."
- The exact contents of `/tools/ai-job-assistant`, `/tools/cover-letter-generator`,
  `/tools/resume-helper`, `/h1b-jobs`, and `/tnt` — linked from the main navigation but not
  individually fetched in this pass.
- Whether `/tools/fake-candidate-detection` (which 404s today) ever existed as a live page, or was a
  navigation link found via search-engine indexing of a page that has since been removed or renamed.
