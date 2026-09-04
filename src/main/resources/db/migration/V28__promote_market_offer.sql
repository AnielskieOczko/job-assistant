-- Promoting a corpus offer into a real one.
--
-- Two halves: the corpus has to hold the posting prose before anything can be promoted, and a
-- promoted offer has to be distinguishable from a pasted one.

-- The posting text, which the corpus was supposed to have all along.
--
-- solid.jobs returns a `description` field carrying the full HTML posting - verified live on
-- 2026-09-04, 4,317 characters on the first offer of the IT division - and `docs/research/10`
-- records it as the reason this source was chosen over Adzuna and NoFluffJobs, both of which serve
-- a teaser. We were throwing it away on arrival: `SolidJobsOffer` did not model the field, so
-- Jackson dropped it, and V14's `payload` column stored a re-serialisation of that parsed object
-- rather than the response. The column comment promising "the whole response verbatim" was
-- therefore false, and the insurance it described - "a field not stored now is not re-fetchable
-- later" - did not pay out on the first occasion it was needed.
--
-- The client now keeps each offer's own JSON and stores that, so `payload` means what it says. The
-- description is additionally a column of its own because promotion reads it on every call and a
-- jsonb extraction per read would be the same field spelled two ways.
--
-- Nullable, and it stays nullable: every row ingested before this migration has no description and
-- can only gain one by being re-polled, which a delisted offer never will be. Promotion refuses
-- such a row rather than inventing text for it.
alter table market_offer
    add column description text;

-- Where a job offer came from.
--
-- `origin` and `market_offer_id` are both here on purpose, and it is not redundancy. The id answers
-- "which listing was this" and is a real foreign key, so it goes null if that listing is ever
-- deleted; `origin` answers "did I find this myself or did the poll", which must survive that -
-- the corpus quietly becoming indistinguishable from the candidate's own reading is exactly what
-- issue #79 asked to prevent.
--
-- No unique constraint on market_offer_id: deduplication already happens on content_hash, so
-- promoting the same listing twice returns the offer already stored, and two listings with
-- identical text are one offer that names whichever was promoted first.
alter table job_offer
    add column origin          text not null default 'PASTED',
    add column market_offer_id bigint references market_offer (id) on delete set null,
    add constraint job_offer_origin_valid check (origin in ('PASTED', 'MARKET'));

create index job_offer_market_idx on job_offer (market_offer_id);
