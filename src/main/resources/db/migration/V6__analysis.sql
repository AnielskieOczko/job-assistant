-- One analysis run per (offer, attempt). State lives in the row rather than in memory so a
-- restart can sweep up jobs that were mid-flight, and so polling needs no server affinity.
create table analysis (
    id            bigserial primary key,
    job_offer_id  bigint      not null references job_offer (id) on delete cascade,
    state         text        not null default 'PENDING',
    error         text,
    model_profile text,
    -- (met must-haves + 0.5 * partial must-haves) / total must-haves. Null until DONE.
    match_score   numeric(5, 4),
    summary_md    text,
    created_at    timestamptz not null default now(),
    started_at    timestamptz,
    completed_at  timestamptz,
    constraint analysis_state_valid check (state in (
        'PENDING', 'EXTRACTING', 'MATCHING', 'NARRATING', 'DONE', 'FAILED'
    ))
);

create index analysis_offer_idx on analysis (job_offer_id, created_at desc);
create index analysis_state_idx on analysis (state);

-- One row per requirement the extractor found. canonical_skill_id is null when nothing in the
-- catalog matched; those rows are surfaced separately and queued for review rather than hidden.
create table offer_requirement (
    id                 bigserial primary key,
    analysis_id        bigint  not null references analysis (id) on delete cascade,
    raw_text           text    not null,
    canonical_skill_id bigint references canonical_skill (id) on delete set null,
    importance         text    not null,
    status             text    not null,
    evidence           text,
    rationale          text,
    display_order      integer not null default 0,
    constraint offer_requirement_importance_valid check (importance in ('MUST_HAVE', 'NICE_TO_HAVE')),
    constraint offer_requirement_status_valid check (status in ('MET', 'PARTIAL', 'MISSING', 'UNRESOLVED'))
);

create index offer_requirement_analysis_idx on offer_requirement (analysis_id, display_order);
create index offer_requirement_skill_idx on offer_requirement (canonical_skill_id);

-- Natural languages are not skills: they are compared by CEFR ordinal, not by relation edges.
create table language_requirement (
    id             bigserial primary key,
    analysis_id    bigint not null references analysis (id) on delete cascade,
    language       text   not null,
    required_level text   not null,
    held_level     text,
    status         text   not null,
    constraint language_requirement_status_valid check (status in ('MET', 'PARTIAL', 'MISSING'))
);

create index language_requirement_analysis_idx on language_requirement (analysis_id);

create table learning_plan_item (
    id                 bigserial primary key,
    analysis_id        bigint  not null references analysis (id) on delete cascade,
    canonical_skill_id bigint references canonical_skill (id) on delete set null,
    skill_name         text    not null,
    why                text    not null,
    practice_project   text,
    effort_estimate    text,
    priority           integer not null default 0
);

create index learning_plan_item_analysis_idx on learning_plan_item (analysis_id, priority);
