-- How much of what the model asked for did not exist.
--
-- CvSelection already computes this on every generation as a side effect of enforcing the rule:
-- a bullet id the profile does not contain, or a skill it does not hold, is dropped rather than
-- rendered. Until now the numbers went into selection_json and were never read again, so the one
-- continuously measured signal of how often a model fabricates was invisible.
--
-- Columns rather than a read-time parse of selection_json, because the point is the distribution
-- across many documents, and that has to be a query:
--   select type, avg(dropped_skill_count) from generated_document group by type;
--
-- Cover letters return no selection, so they are always zero. Rows written before this migration
-- keep the default and are indistinguishable from a clean generation; the counts are a signal to
-- watch from here on, not a backfilled history.
alter table generated_document
    add column dropped_bullet_count integer not null default 0,
    add column dropped_skill_count integer not null default 0;
