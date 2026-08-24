-- Every model call, recorded. Without this a bad report is undebuggable: you cannot tell whether
-- the prompt was wrong, the model ignored the schema, or the parsing failed.
create table llm_call (
    id            bigserial primary key,
    task          text        not null,
    model_profile text        not null,
    model_name    text,
    request_json  text        not null,
    response_text text,
    error         text,
    input_tokens  integer,
    output_tokens integer,
    latency_ms    bigint,
    created_at    timestamptz not null default now()
);

create index llm_call_created_idx on llm_call (created_at desc);
create index llm_call_task_idx on llm_call (task, created_at desc);
