-- Per-entity editing of the profile, rather than only whole-document replacement.

-- `language_skill_unique` is case-sensitive, but CandidateProfile.languageLevel() matches
-- case-insensitively. So 'English' and 'english' could both exist and which one the analysis
-- pipeline compared against was arbitrary. Collapse any such pairs, then make the constraint agree
-- with the lookup.
--
-- Where a duplicate exists, keep the strongest claim: silently downgrading a declared level would
-- understate the profile in every future gap report. Ties break on the lowest id.
--
-- This runs before the display_order backfill below so the collapsed rows are numbered without gaps.
delete from language_skill
where id not in (
    select distinct on (lower(language)) id
    from language_skill
    order by lower(language),
             case level
                 when 'NATIVE' then 6
                 when 'C2' then 5
                 when 'C1' then 4
                 when 'B2' then 3
                 when 'B1' then 2
                 when 'A2' then 1
                 else 0
             end desc,
             id
);

alter table language_skill drop constraint language_skill_unique;
create unique index language_skill_lower_unique on language_skill (lower(language));

-- Import assigned display_order from array position, so every collection that a CV renders in a
-- meaningful order already had the column. Skills and languages did not: they were read back
-- `order by id` and `order by language` respectively. That was invisible while import was the only
-- write path -- a fresh insert per import happened to preserve the document's order for skills, and
-- nobody could reorder anything anyway. Once a skill can be added on its own it would be pinned to
-- the bottom of the list forever, so both collections get a real ordering column.
--
-- Both backfills reproduce the order these rows are read today, so existing profiles render
-- unchanged.

alter table profile_skill add column display_order integer not null default 0;

update profile_skill ps
set display_order = ordered.position
from (select id, (row_number() over (order by id))::integer - 1 as position from profile_skill) ordered
where ps.id = ordered.id;

alter table language_skill add column display_order integer not null default 0;

update language_skill ls
set display_order = ordered.position
from (select id, (row_number() over (order by language))::integer - 1 as position from language_skill) ordered
where ls.id = ordered.id;

-- A monotonic counter bumped by every write to the profile. A generated CV or a finished analysis
-- records the value it was produced from, so the UI can say "your profile changed since this ran"
-- instead of presenting stale output as current. It lives on the details singleton because that row
-- is the profile's identity today; when profiles become plural it moves to the profile root.
alter table profile_details add column revision bigint not null default 0;
