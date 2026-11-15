-- Structured target role per profile (#99 Milestone 1).
-- One row per profile; target_level is null when unset, title_keywords is an empty array by default.
create table profile_target_role (
    profile_id     bigint      primary key references profile (id) on delete cascade,
    target_level   text,
    title_keywords text[]      not null default '{}',
    updated_at     timestamptz not null default now()
);

create table profile_target_role_skill (
    profile_id         bigint not null references profile_target_role (profile_id) on delete cascade,
    canonical_skill_id bigint not null references canonical_skill (id) on delete cascade,
    primary key (profile_id, canonical_skill_id)
);
