-- An optional photograph for one profile.
--
-- Its own table rather than a column on profile_details, for one reason: profile_details is read on
-- every profile load, and every CRUD write answers with the whole CandidateProfile. A bytea on that
-- row would ride along on all of it, so the blob lives where only the two callers that want it -
-- the renderer and the image endpoint - go looking.
--
-- profile_id is the primary key: a portrait has no identity apart from the profile it belongs to,
-- and one profile has at most one. The cascade is the same one every other profile-owned table
-- carries, and here it is the erasure guarantee - a photograph is a direct identifier, so deleting
-- a persona has to delete its face with it.
create table profile_portrait (
    profile_id bigint primary key references profile (id) on delete cascade,
    media_type text        not null,
    bytes      bytea       not null,
    updated_at timestamptz not null default now()
);
