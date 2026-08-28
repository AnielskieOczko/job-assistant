-- The ingested market corpus.
--
-- Deliberately NOT job_offer. A JobOffer is something the candidate might apply to: it carries an
-- application lifecycle row, a profile revision, analyses and generated documents. A market offer
-- is a row in a sample -- there will be thousands, and no cover letter will ever be written for
-- them. Folding the two together would also silently change what AggregateGapReport.analysedOffers
-- counts. See docs/research/13-offer-market-dashboard.md.
create table market_offer (
    id                bigserial primary key,
    -- Which board the row came from. Every statistic must name its source rather than claim to
    -- describe "the market", so the source travels with the row.
    source            text        not null,
    offer_key         text        not null,
    title             text        not null,
    company           text,
    division          text,
    category          text,
    sub_category      text,
    url               text,
    experience_level  text,
    contract_time     text,
    is_remote         boolean     not null default false,
    is_hybrid         boolean     not null default false,
    locations         text[]      not null default '{}',
    salary_from       numeric(12, 2),
    salary_to         numeric(12, 2),
    salary_currency   text,
    salary_period     text,
    employment_type   text,
    -- Validity as the source states it. Every offer in a 500-offer sample carried both, so there
    -- is no need to infer a window from when we happened to look.
    valid_from        timestamptz,
    valid_to          timestamptz,
    source_updated_at timestamptz,
    -- Ours, not the source's: when the row entered the corpus and when it last survived a poll.
    -- The corpus accumulates and is never pruned, so these bound every statistic's window.
    first_seen_at     timestamptz not null default now(),
    last_seen_at      timestamptz not null default now(),
    -- The whole response verbatim. Offers get delisted, so a field not stored now is not
    -- re-fetchable later, and the second question asked of this corpus will not be the first one.
    payload           jsonb       not null,
    constraint market_offer_key_unique unique (source, offer_key)
);

create index market_offer_seen_idx on market_offer (source, last_seen_at desc);
create index market_offer_valid_idx on market_offer (valid_to);

-- One row per skill an offer lists. This is what makes demand and salary-premium queries possible
-- without reopening the payload.
create table market_offer_skill (
    market_offer_id    bigint not null references market_offer (id) on delete cascade,
    skill_name         text   not null,
    -- Basic / Advanced / Expert / NiceToHave as the source states it. NiceToHave is the source's
    -- only importance signal, and it is carried on the level field rather than a separate one.
    level              text   not null,
    -- Null when the catalog could not place the term. Resolution is a lookup and never a creation:
    -- an unplaced term goes to unmatched_term for a human to review.
    canonical_skill_id bigint references canonical_skill (id) on delete set null,
    primary key (market_offer_id, skill_name)
);

create index market_offer_skill_canonical_idx on market_offer_skill (canonical_skill_id);

-- Market volume is counted separately from occurrences in offers the candidate actually read.
-- One poll sees hundreds of distinct terms, so a single counter would rank the review queue by the
-- market rather than by what the candidate looked at -- and the queue is ordered by occurrences.
-- Two counters keep one queue and one rule, and turn volume into evidence instead of noise:
-- "seen once in your offers, asked for 47 times by the market" is a better prompt than either.
alter table unmatched_term
    add column market_occurrences integer not null default 0;
