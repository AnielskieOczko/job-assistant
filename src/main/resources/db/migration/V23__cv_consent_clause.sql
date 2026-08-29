-- A Polish CV needs a data-processing consent clause (RODO / GDPR) or an employer has no stated
-- legal basis for keeping the application. The wording varies by recruiter, by whether consent
-- covers future recruitment too, and by the CV's own language, so it is candidate-authored text
-- keyed by output language rather than one constant compiled into the template. See issue #52.

create table cv_consent_clause (
    id            bigserial primary key,
    profile_id    bigint  not null references profile (id) on delete cascade,
    language      text    not null,
    text          text    not null,
    display_order integer not null default 0
);

-- Same precedent as `language_skill_lower_unique`: the language is free text a human types, so
-- 'Polish' and 'polish' must collapse onto the same clause rather than silently coexisting.
create unique index cv_consent_clause_language_unique
    on cv_consent_clause (profile_id, lower(language));

create index cv_consent_clause_parent_idx on cv_consent_clause (profile_id);

-- Null means no clause existed for the document's output language - a fact worth keeping rather
-- than letting the CV render silently without one. See JdbcDocumentService.buildCv.
alter table generated_document add column consent_clause_language text;
