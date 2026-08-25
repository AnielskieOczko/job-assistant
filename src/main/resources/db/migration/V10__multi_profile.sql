-- Profiles become plural: one per persona. See docs/roadmap.md #2 and docs/adr/0001.

create table profile (
    id         bigserial primary key,
    name       text        not null,
    is_default boolean     not null default false,
    revision   bigint      not null default 0,
    created_at timestamptz not null default now()
);

-- Only rows where is_default is true are indexed, so any number of false rows coexist without conflict.
create unique index profile_one_default on profile (is_default) where is_default;

-- The existing singleton becomes profile 1, "Default". Deterministic, not derived from headline: a
-- name synthesized from data that might be blank is worse than an obviously-placeholder one the
-- user is expected to rename. No-op on a fresh database with no profile yet.
insert into profile (id, name, is_default, revision)
select 1, 'Default', true, coalesce(revision, 0)
from profile_details
where id = 1;

select setval(pg_get_serial_sequence('profile', 'id'), greatest((select max(id) from profile), 1));

-- profile_details becomes a 1:1 child of profile rather than a fixed-id singleton. The surrogate
-- `id` is dropped in favour of `profile_id` itself as the primary key -- a details row has no
-- identity independent of the profile it belongs to.
alter table profile_details drop constraint profile_details_singleton;
alter table profile_details add column profile_id bigint;
update profile_details set profile_id = id;
alter table profile_details alter column profile_id set not null;
alter table profile_details drop column id;
alter table profile_details drop column revision;
alter table profile_details add primary key (profile_id);
alter table profile_details
    add constraint profile_details_profile_fk foreign key (profile_id) references profile (id) on delete cascade;

-- Every other profile table gains the same parent. Nothing could be written before profile_details
-- existed -- every write path commits through ProfileService.require(), which throws when it is
-- absent -- so backfilling every pre-existing row to profile 1 is safe.
alter table profile_link add column profile_id bigint;
alter table profile_skill add column profile_id bigint;
alter table work_experience add column profile_id bigint;
alter table education add column profile_id bigint;
alter table language_skill add column profile_id bigint;

update profile_link set profile_id = 1;
update profile_skill set profile_id = 1;
update work_experience set profile_id = 1;
update education set profile_id = 1;
update language_skill set profile_id = 1;

alter table profile_link alter column profile_id set not null;
alter table profile_skill alter column profile_id set not null;
alter table work_experience alter column profile_id set not null;
alter table education alter column profile_id set not null;
alter table language_skill alter column profile_id set not null;

alter table profile_link add constraint profile_link_profile_fk foreign key (profile_id) references profile (id) on delete cascade;
alter table profile_skill add constraint profile_skill_profile_fk foreign key (profile_id) references profile (id) on delete cascade;
alter table work_experience add constraint work_experience_profile_fk foreign key (profile_id) references profile (id) on delete cascade;
alter table education add constraint education_profile_fk foreign key (profile_id) references profile (id) on delete cascade;
alter table language_skill add constraint language_skill_profile_fk foreign key (profile_id) references profile (id) on delete cascade;

create index profile_link_parent_idx on profile_link (profile_id);
create index profile_skill_parent_idx on profile_skill (profile_id);
create index work_experience_parent_idx on work_experience (profile_id);
create index education_parent_idx on education (profile_id);
create index language_skill_parent_idx on language_skill (profile_id);

-- The uniqueness that mattered was always "one row per skill/language *within a profile*"; it was
-- only ever global because there was only ever one profile. This is the schema-level version of
-- "the risk that matters": without this, two profiles could not each independently hold Kotlin.
alter table profile_skill drop constraint profile_skill_unique;
alter table profile_skill add constraint profile_skill_unique unique (profile_id, canonical_skill_id);

drop index language_skill_lower_unique;
create unique index language_skill_lower_unique on language_skill (profile_id, lower(language));

-- Which profile produced this analysis or document. Not null, unlike profile_revision: every
-- historical row was produced against the one profile that existed when it ran, so there is no
-- "predates the concept" case the way there was for the revision counter.
alter table analysis add column profile_id bigint;
update analysis set profile_id = 1;
alter table analysis alter column profile_id set not null;
alter table analysis add constraint analysis_profile_fk foreign key (profile_id) references profile (id) on delete cascade;
create index analysis_profile_idx on analysis (profile_id);

alter table generated_document add column profile_id bigint;
update generated_document set profile_id = 1;
alter table generated_document alter column profile_id set not null;
alter table generated_document add constraint generated_document_profile_fk foreign key (profile_id) references profile (id) on delete cascade;
create index generated_document_profile_idx on generated_document (profile_id);
