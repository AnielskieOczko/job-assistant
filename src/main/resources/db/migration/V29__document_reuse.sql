-- Reusing a generated CV on a second offer, as a copy rather than a link.
--
-- The library (issue #82) needs a way to say "this document is a copy of that one" so a reused CV
-- reads as reused rather than as a second independent tailoring. A many-to-many between documents
-- and offers was rejected: it would make "this offer's CV" ambiguous, and the aggregate that
-- already exists over `generated_document` (see CLAUDE.md) would double-count one generation as
-- if it were two.
--
-- Nullable, and `on delete set null` for the same reason V27's links are: there is no
-- document-deletion path today, but a copy naming a row that is gone should lose its provenance
-- rather than dangle.
--
-- A copy carries its source's `dropped_bullet_count` and `dropped_skill_count` rather than fresh
-- ones - nothing was regenerated, so there is nothing new to count. That means any aggregate over
-- those columns (`avg(dropped_skill_count) ... group by type`) should filter to
-- `source_document_id is null`, or a single generation's fabrication rate is counted once for every
-- offer it was ever reused onto.
alter table generated_document
    add column source_document_id bigint
        references generated_document (id) on delete set null;
