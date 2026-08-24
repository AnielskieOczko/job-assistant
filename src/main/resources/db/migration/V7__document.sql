-- Generated CVs and cover letters. The HTML is stored; the PDF is rendered on demand, because
-- Chromium output is reproducible from the HTML and Neon's free tier has 0.5 GB to spend.
create table generated_document (
    id             bigserial primary key,
    job_offer_id   bigint      not null references job_offer (id) on delete cascade,
    analysis_id    bigint references analysis (id) on delete set null,
    type           text        not null,
    language       text        not null,
    html           text        not null,
    -- What the model chose to include, kept so a surprising CV can be traced back to a decision.
    selection_json text        not null,
    created_at     timestamptz not null default now(),
    constraint generated_document_type_valid check (type in ('CV', 'COVER_LETTER'))
);

create index generated_document_offer_idx on generated_document (job_offer_id, type, created_at desc);
