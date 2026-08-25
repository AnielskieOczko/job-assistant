-- Which profile state produced this output.
--
-- Both an analysis and a generated document are derived from the profile at a moment in time, and
-- neither recorded which moment. Stored HTML is a snapshot and was true when it was written, so
-- this is not a correctness fix -- it is what lets the UI distinguish a current CV from one whose
-- profile has since moved on. A stale gap report is the more misleading of the two: it tells you to
-- go and learn something you have already added.
--
-- Nullable, because rows written before V8 have no revision to claim.
alter table analysis add column profile_revision bigint;
alter table generated_document add column profile_revision bigint;
