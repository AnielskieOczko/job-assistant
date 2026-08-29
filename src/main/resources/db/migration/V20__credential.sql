-- Courses, bootcamps and certifications get their own aggregate rather than a `kind` discriminator
-- on `education`: the fields genuinely diverge (a credential needs a URL and an optional expiry,
-- has no field of study), and the CV renders it as its own section. See issue #49.

create table credential (
    id            bigserial primary key,
    profile_id    bigint  not null references profile (id) on delete cascade,
    title         text    not null,
    issuer        text    not null,
    kind          text    not null,
    url           text,
    credential_id text,
    issued_on     date,
    expires_on    date,
    display_order integer not null default 0,
    constraint credential_kind_valid check (kind in ('COURSE','BOOTCAMP','CERTIFICATION','OTHER')),
    constraint credential_dates_ordered check (expires_on is null or issued_on is null or expires_on >= issued_on)
);

create index credential_parent_idx on credential (profile_id);
