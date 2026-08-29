-- The profile records what the candidate has done; nothing in it records what they are aiming at
-- next, which matters for a career change. One nullable column, because it is a single prose field
-- per profile and profile_details is already keyed by profile_id.
alter table profile_details add column career_goal text;
