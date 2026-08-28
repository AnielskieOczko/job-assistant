-- Model-proposed readings of a queued term.
--
-- Stored rather than computed, unlike the deterministic trigram suggestions, because producing one
-- costs a model call. A review page that recomputed them on every load would spend tokens whenever
-- someone opened a tab, which is the opposite of the approval-gated eval environment and the
-- deliberately tiny analysis pool this project runs everywhere else.
--
-- A suggestion is never a decision. Nothing here changes what a term resolves to: approving still
-- goes through the catalog's alias write, driven by a human. These rows exist to put a candidate in
-- front of a person, and are deleted with the term or the skill they point at.
create table triage_suggestion (
    id                 bigserial   primary key,
    unmatched_term_id  bigint      not null references unmatched_term (id) on delete cascade,
    canonical_skill_id bigint      not null references canonical_skill (id) on delete cascade,
    -- The model's own words for why. Shown to the reviewer: a suggestion you cannot interrogate is
    -- worse than none, because it invites agreement without inspection.
    rationale          text,
    -- Which model profile produced it, so a change in suggestion quality can be attributed rather
    -- than argued about. Same reasoning as llm_call.serving_provider.
    model_profile      text,
    created_at         timestamptz not null default now(),
    -- One row per (term, skill). Re-running suggestion refreshes rather than accumulates.
    constraint triage_suggestion_unique unique (unmatched_term_id, canonical_skill_id)
);

create index triage_suggestion_term_idx on triage_suggestion (unmatched_term_id);
