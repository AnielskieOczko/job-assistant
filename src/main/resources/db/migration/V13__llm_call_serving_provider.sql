-- Which upstream provider actually served each call.
--
-- OpenRouter serves one model slug from many providers with genuinely different capabilities:
-- some implement structured outputs and some ignore the JSON schema entirely. Without this column
-- a bad eval score cannot be attributed - the same model name appears on every row whether the
-- request was constrained to a schema or answered by a provider that threw it away.
--
-- Nullable: providers other than OpenRouter do not report one, and rows written before this
-- migration have no way to know.
alter table llm_call
    add column serving_provider text;

create index llm_call_provider_idx on llm_call (serving_provider, created_at desc);
