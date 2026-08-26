-- Personal-data handling for the model-call audit log.
--
-- Two changes, both about the same problem: llm_call holds a verbatim copy of every prompt, and
-- until now nothing ever removed one.

-- 1. Give the audit log an owner.
--
-- Every other profile-derived table cascades from `profile`, so deleting a persona erases it. This
-- one did not, which meant prompt text about a person outlived the person's profile - the one place
-- erasure was guaranteed to miss. Nullable because rows written before this migration have no
-- known owner (and because a call can legitimately have none).
alter table llm_call
    add column profile_id bigint references profile (id) on delete cascade;

create index llm_call_profile_idx on llm_call (profile_id);

-- 2. Discard the prompts already sent.
--
-- DESTRUCTIVE AND DELIBERATE. Rows written before this release were captured while prompt builders
-- still included the candidate's full name, so the table currently holds identifiers that the
-- outbound guard now refuses to send. There is no way to redact them in place - the name sits in
-- free-form JSON - so they go.
--
-- The cost is real: this is the debugging history the table exists to provide. It is accepted
-- because the rows predate the guard and cannot be trusted to be clean.
delete from llm_call;
