-- Canonical skill taxonomy. Everything downstream (profile skills, extracted offer
-- requirements, the gap diff) refers to canonical_skill.id rather than free text, which is
-- what makes "React" / "ReactJS" / "React.js" collapse into a single gap.

create table canonical_skill (
    id         bigserial primary key,
    name       text        not null,
    category   text        not null,
    created_at timestamptz not null default now(),
    constraint canonical_skill_name_unique unique (name),
    constraint canonical_skill_category_valid check (category in (
        'LANGUAGE', 'FRAMEWORK', 'DATABASE', 'MESSAGING', 'CLOUD', 'DEVOPS',
        'TESTING', 'FRONTEND', 'AI', 'PRACTICE', 'TOOL', 'SOFT', 'OTHER'
    ))
);

-- Lookup happens on normalized_alias, never on the display alias: "React.js", "react js"
-- and "REACTJS" all normalise to "reactjs".
create table skill_alias (
    id                bigserial primary key,
    canonical_skill_id bigint not null references canonical_skill (id) on delete cascade,
    alias             text   not null,
    normalized_alias  text   not null,
    constraint skill_alias_normalized_unique unique (normalized_alias)
);

create index skill_alias_skill_idx on skill_alias (canonical_skill_id);

-- IMPLIES: holding from_skill genuinely covers to_skill (Kotlin implies JVM).
-- RELATED: adjacent enough to count as a PARTIAL match, not a MET one (Quarkus ~ Spring Boot).
create table skill_relation (
    from_skill_id bigint not null references canonical_skill (id) on delete cascade,
    to_skill_id   bigint not null references canonical_skill (id) on delete cascade,
    kind          text   not null,
    primary key (from_skill_id, to_skill_id, kind),
    constraint skill_relation_kind_valid check (kind in ('IMPLIES', 'RELATED')),
    constraint skill_relation_not_self check (from_skill_id <> to_skill_id)
);

create index skill_relation_to_idx on skill_relation (to_skill_id);

-- Terms the extractor could not snap to the catalog. This is the review queue that grows the
-- catalog from real offers instead of requiring the taxonomy to be guessed upfront.
create table unmatched_term (
    id               bigserial primary key,
    term             text        not null,
    normalized_term  text        not null,
    occurrences      integer     not null default 1,
    first_seen_at    timestamptz not null default now(),
    last_seen_at     timestamptz not null default now(),
    status           text        not null default 'PENDING',
    resolved_skill_id bigint references canonical_skill (id) on delete set null,
    constraint unmatched_term_normalized_unique unique (normalized_term),
    constraint unmatched_term_status_valid check (status in ('PENDING', 'APPROVED', 'REJECTED'))
);

create index unmatched_term_status_idx on unmatched_term (status, occurrences desc);
