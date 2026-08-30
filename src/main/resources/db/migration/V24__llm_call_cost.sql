-- What each model call actually cost, and the metadata needed to explain the number.
--
-- The data was already arriving and being thrown away. OpenRouter and Requesty both return the
-- charged amount inline on every non-streaming completion as `usage.cost`, with no request flag,
-- and LangChain4j hands the raw provider JSON to the listener this application already runs - the
-- same body `serving_provider` is read from since V13. LangChain4j's own parsed response types
-- have no `cost` field at all (they are provider-neutral on purpose), so nothing but a second read
-- off that body could have captured it. See docs/research/11-model-call-cost.md.
--
-- Every column is nullable. A local model reports no price, an errored call never got a response
-- body, and every row written before this migration has none of it.

alter table llm_call
    -- The amount charged for this call. `numeric` rather than a float because it is summed for a
    -- lifetime total and compared against a budget, and float summation drift is not acceptable
    -- for either. Denominated in whatever unit the provider bills the key in (OpenRouter calls
    -- them credits); llm_spend_daily and the account reconciliation both use the same unit, so no
    -- conversion is ever applied to it.
    add column cost_usd numeric(12, 8),

    -- The subset of input_tokens served from a prompt cache. Cache reads bill at a fraction of the
    -- ordinary input rate, so a uniform per-token price applied to input_tokens overstates spend
    -- whenever caching is in play - and a cache that has silently stopped working is otherwise
    -- invisible.
    add column cached_input_tokens integer,

    -- The subset of output_tokens spent on reasoning rather than on the visible answer. Already
    -- included in output_tokens and already paid for, but absent from response_text, so without
    -- this column the response looks far cheaper than it was.
    add column reasoning_output_tokens integer,

    -- Why the model stopped. STOP is the ordinary case; LENGTH means the answer was truncated and
    -- paid for anyway, which is the difference between a bad prompt and a cut-off one.
    add column finish_reason text,

    -- The provider's own id for this generation (OpenRouter: `gen-...`). The join key to their
    -- dashboard and to GET /api/v1/generation, so "which line on the bill is this row" stops being
    -- a guess.
    add column provider_call_id text,

    -- What the call was for, beyond its task: ('ANALYSIS', 42), ('DOCUMENT', 7). Turns "what did
    -- this call cost" into "what did analysing this offer cost, repairs included".
    --
    -- Deliberately NOT a foreign key, unlike profile_id. That column has one because erasing a
    -- profile must erase its prompts; this one must survive the deletion of the analysis it paid
    -- for, because the money was still spent. The llm module also has no dependency on analysis or
    -- document, and a constraint naming their tables would be the first half of one.
    add column subject_kind text,
    add column subject_id bigint;

-- Cost queries are "this period, grouped" rather than "this row", and the guardrail runs one of
-- them before every call.
create index llm_call_cost_idx on llm_call (created_at desc) where cost_usd is not null;
create index llm_call_subject_idx on llm_call (subject_kind, subject_id);
