-- Pasted job offers, stored in full including the raw text. content_hash makes re-pasting the
-- same posting a no-op instead of a duplicate row and another round of tokens.
create table job_offer (
    id                bigserial primary key,
    content_hash      text        not null,
    raw_text          text        not null,
    source_url        text,
    -- Filled in by the analysis module once extraction has run.
    title             text,
    company           text,
    seniority         text,
    detected_language text,
    created_at        timestamptz not null default now(),
    constraint job_offer_content_hash_unique unique (content_hash)
);

-- One lifecycle record per offer. Without this the app is a report generator you forget you ran.
create table application (
    id                bigserial primary key,
    job_offer_id      bigint      not null references job_offer (id) on delete cascade,
    status            text        not null default 'SAVED',
    status_changed_at timestamptz not null default now(),
    applied_on        date,
    notes             text,
    constraint application_offer_unique unique (job_offer_id),
    constraint application_status_valid check (status in (
        'SAVED', 'ANALYZED', 'APPLIED', 'INTERVIEWING', 'REJECTED', 'OFFER'
    ))
);

create index application_status_idx on application (status, status_changed_at desc);
