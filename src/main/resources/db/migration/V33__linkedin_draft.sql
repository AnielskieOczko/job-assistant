-- Persisted LinkedIn section drafts with profile_revision staleness tracking.
create table linkedin_draft (
    id               bigserial   primary key,
    profile_id       bigint      not null references profile (id) on delete cascade,
    section          text        not null check (section in ('HEADLINE', 'ABOUT', 'EXPERIENCE_ENTRY', 'PROJECT_ENTRY', 'SKILL_OR_CREDENTIAL_POST')),
    subject_id       bigint,
    profile_revision bigint      not null,
    content          text        not null,
    unheld_skills    text[]      not null default '{}',
    model_profile    text,
    created_at       timestamptz not null default now()
);

create index linkedin_draft_profile_idx on linkedin_draft (profile_id, created_at desc);
