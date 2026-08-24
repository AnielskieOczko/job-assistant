-- The candidate profile: verified ground truth, hand-authored, never written by a model.
-- Everything the CV can say must be traceable to a row in here.

-- Single-user MVP, so contact details are a singleton row rather than a table of people.
create table profile_details (
    id         integer primary key default 1,
    full_name  text not null,
    headline   text,
    email      text,
    phone      text,
    location   text,
    summary    text,
    constraint profile_details_singleton check (id = 1)
);

create table profile_link (
    id            bigserial primary key,
    label         text    not null,
    url           text    not null,
    display_order integer not null default 0
);

create table profile_skill (
    id                  bigserial primary key,
    canonical_skill_id  bigint      not null references canonical_skill (id) on delete restrict,
    proficiency         text        not null,
    years_of_experience numeric(4, 1),
    last_used_year      integer,
    constraint profile_skill_unique unique (canonical_skill_id),
    constraint profile_skill_proficiency_valid check (proficiency in (
        'BEGINNER', 'WORKING', 'PROFICIENT', 'EXPERT'
    )),
    constraint profile_skill_years_sane check (years_of_experience is null or years_of_experience >= 0)
);

create table work_experience (
    id            bigserial primary key,
    company       text    not null,
    role_title    text    not null,
    location      text,
    started_on    date    not null,
    ended_on      date,
    summary       text,
    display_order integer not null default 0,
    constraint work_experience_dates_ordered check (ended_on is null or ended_on >= started_on)
);

create table experience_bullet (
    id                 bigserial primary key,
    work_experience_id bigint  not null references work_experience (id) on delete cascade,
    text               text    not null,
    display_order      integer not null default 0
);

create index experience_bullet_parent_idx on experience_bullet (work_experience_id);

-- The join that makes the CV invariant checkable: each bullet declares which canonical skills it
-- actually evidences, so a rendered CV mentioning a skill with no backing bullet is a bug.
create table experience_bullet_skill (
    experience_bullet_id bigint not null references experience_bullet (id) on delete cascade,
    canonical_skill_id   bigint not null references canonical_skill (id) on delete restrict,
    primary key (experience_bullet_id, canonical_skill_id)
);

create table education (
    id             bigserial primary key,
    institution    text    not null,
    degree         text    not null,
    field_of_study text,
    started_on     date,
    ended_on       date,
    display_order  integer not null default 0
);

create table language_skill (
    id       bigserial primary key,
    language text not null,
    level    text not null,
    constraint language_skill_unique unique (language),
    constraint language_skill_level_valid check (level in (
        'A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'NATIVE'
    ))
);
