-- The accumulated spend total, in a table that outlives the audit log.
--
-- `llm_call` cannot answer "what have I spent". LlmCallRetention deletes its rows after 30 days,
-- and V11 cascade-deletes them with their profile, so `select sum(cost_usd) from llm_call` is a
-- 30-day, surviving-personas-only figure that would render under a label saying "total". Rather
-- than weaken either of those deliberate rules, the total gets its own home: a tiny daily rollup
-- written in the same transaction as each audit row and never purged.
--
-- Three properties, each a decision rather than an implementation detail:
--
-- 1. NO profile_id, AND NO CASCADE. This is the one profile-derived table that deliberately
--    survives V11's erasure rule. Deleting a persona does not un-spend the money, and a bucket
--    holding a day, a task, a model name and some counters carries no identifier - there is
--    nothing here to erase. The per-call rows, which do carry prompt text, are still erased.
--
-- 2. ACCRUED, NOT DERIVED - the mirror image of catalog.unmatched_term.market_occurrences, which
--    is *set* from the corpus precisely because a daily re-poll re-observes the same offers and
--    accumulating would multiply demand by how often we looked. A model call is the opposite: it
--    happens exactly once and can never be re-observed, so incrementing is the correct operation
--    here - and after a purge it is the only possible one, because the rows are gone.
--
-- 3. priced_calls IS THE DENOMINATOR. A provider that reports no price (a local model, a plain
--    OpenAI-compatible endpoint) still produces a row here, counted in `calls` and contributing
--    nothing to `cost_usd`. Without priced_calls a total of $0.40 over 100 calls is
--    indistinguishable from the same $0.40 over the 60 of them that were actually priced, and only
--    one of those is a total - the other is a floor. Every surface rendering cost_usd must render
--    this beside it.

create table llm_spend_daily (
    -- UTC, matching the day boundary the providers' own dashboards use, so a figure here and a
    -- figure there describe the same window rather than one shifted by a timezone.
    day                     date           not null,
    task                    text           not null,
    model_profile           text           not null,
    -- '' rather than null so the primary key works: a null component would let the same bucket be
    -- inserted repeatedly instead of conflicting, silently splitting one day's spend into rows.
    model_name              text           not null default '',

    calls                   integer        not null default 0,
    failed_calls            integer        not null default 0,
    priced_calls            integer        not null default 0,

    input_tokens            bigint         not null default 0,
    output_tokens           bigint         not null default 0,
    cached_input_tokens     bigint         not null default 0,
    reasoning_output_tokens bigint         not null default 0,

    cost_usd                numeric(14, 8) not null default 0,

    primary key (day, task, model_profile, model_name)
);

create index llm_spend_daily_day_idx on llm_spend_daily (day desc);

-- Seed from whatever the audit log still holds.
--
-- Those rows predate cost capture, so they contribute token counts and call counts but nothing to
-- cost_usd, and priced_calls comes out 0 for every one of them. That is the honest answer: the
-- calls really were made, and their price really is unknown. It is also exactly the case
-- priced_calls exists to make visible.
insert into llm_spend_daily (
    day, task, model_profile, model_name,
    calls, failed_calls, priced_calls,
    input_tokens, output_tokens, cached_input_tokens, reasoning_output_tokens, cost_usd
)
select (created_at at time zone 'UTC')::date,
       task,
       model_profile,
       coalesce(model_name, ''),
       count(*),
       count(*) filter (where error is not null),
       count(*) filter (where cost_usd is not null),
       coalesce(sum(input_tokens), 0),
       coalesce(sum(output_tokens), 0),
       coalesce(sum(cached_input_tokens), 0),
       coalesce(sum(reasoning_output_tokens), 0),
       coalesce(sum(cost_usd), 0)
from llm_call
group by 1, 2, 3, 4;
