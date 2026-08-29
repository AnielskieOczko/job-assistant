-- A side project: the main evidence a career changer has that they can do the work they are
-- applying for. Its bullets are experience_bullet rows under a different owner, so they inherit
-- the id-based selection that CvSelection.from uses to keep tailoring honest.

create table project (
    id            bigserial primary key,
    profile_id    bigint  not null references profile (id) on delete cascade,
    name          text    not null,
    url           text,
    description   text,
    started_on    date,
    ended_on      date,
    display_order integer not null default 0,
    constraint project_dates_ordered check (ended_on is null or started_on is null or ended_on >= started_on)
);
create index project_parent_idx on project (profile_id);

-- A project-level skill badge, declared rather than derived from its bullets. Separate from
-- experience_bullet_skill, which still tags a project's individual bullets.
create table project_skill (
    project_id         bigint not null references project (id) on delete cascade,
    canonical_skill_id bigint not null references canonical_skill (id) on delete restrict,
    primary key (project_id, canonical_skill_id)
);

alter table experience_bullet
    alter column work_experience_id drop not null,
    add column project_id bigint references project (id) on delete cascade,
    add constraint experience_bullet_owner_exclusive
        check ((work_experience_id is null) != (project_id is null));

create index experience_bullet_project_idx on experience_bullet (project_id);
