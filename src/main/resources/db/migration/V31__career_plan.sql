-- Immutable, dated, evidence-bounded Career Plans (#99 Milestone 2).
create table career_plan (
    id                  bigserial   primary key,
    profile_id          bigint      not null references profile (id) on delete cascade,
    profile_revision    bigint      not null,
    target_level        text,
    target_skills       text[]      not null default '{}',
    matched_offer_count integer     not null,
    corpus_offer_count  integer     not null,
    dropped_topic_count integer     not null default 0,
    model_profile       text,
    created_at          timestamptz not null default now()
);

create index career_plan_profile_idx on career_plan (profile_id, created_at desc);

create table career_plan_topic (
    id                     bigserial primary key,
    career_plan_id         bigint    not null references career_plan (id) on delete cascade,
    canonical_skill_id     bigint    not null references canonical_skill (id) on delete cascade,
    priority               integer   not null,
    target_level           text      not null,
    why                    text      not null,
    practice_project       text,
    effort_estimate        text,
    demand_offers          integer   not null,
    demand_required_offers integer   not null,
    coverage_status        text      not null,
    constraint career_plan_topic_unique unique (career_plan_id, canonical_skill_id)
);

create index career_plan_topic_plan_idx on career_plan_topic (career_plan_id, priority);
