-- Which document was actually sent for an application.
--
-- `application` already records that you applied and on what date; nothing recorded *what went
-- out*. Generate three CVs for one offer and the database could not say which one an employer
-- read, so the only place that fact could live was the free-text `notes` column, which is not
-- queryable. Outcome calibration is gated on thirty recorded outcomes (docs/roadmap.md) and an
-- unrecorded application cannot be reconstructed afterwards, so this is cheap now and impossible
-- later.
--
-- The link lives on `application` rather than on `generated_document` because an application has
-- at most one CV and one cover letter that were sent, while a document may exist and never be
-- sent. Two columns keep "what did I send for this offer" a single read of a row already loaded.
--
-- Both are nullable and stay nullable: an application made outside the tool has no document to
-- name, and forcing one would put a false row in the table calibration will eventually read.
--
-- The foreign key is deliberate, and deliberately the opposite choice from `llm_call`'s
-- subject_kind/subject_id pair, which carries none. That pair exists so cost history outlives what
-- it paid for; this one exists so the document can be opened, and an id naming a row that is gone
-- is worth nothing. `on delete set null` therefore drops the link rather than dangling it. There
-- is no document-deletion path today, so this is a rule for the one that may arrive.
alter table application
    add column sent_cv_document_id bigint
        references generated_document (id) on delete set null,
    add column sent_cover_letter_document_id bigint
        references generated_document (id) on delete set null;
