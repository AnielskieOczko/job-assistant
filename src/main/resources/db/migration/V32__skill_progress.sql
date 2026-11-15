-- Persistent skill learning progress per (profile, canonical_skill) (#99 Milestone 3).
-- Survives Career Plan regeneration; never writes to profile_skill automatically.
create table skill_progress (
    profile_id         bigint      not null references profile (id) on delete cascade,
    canonical_skill_id bigint      not null references canonical_skill (id) on delete cascade,
    state              text        not null check (state in ('NOT_STARTED', 'LEARNING', 'READY_TO_DECLARE')),
    updated_at         timestamptz not null default now(),
    primary key (profile_id, canonical_skill_id)
);
