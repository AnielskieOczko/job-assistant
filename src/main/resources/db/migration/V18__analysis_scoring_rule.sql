-- Records which scoring rule produced an analysis's match_score.
--
-- Soft skills are now reported but not scored: a "Communication" must-have the profile does not
-- declare should appear in the gap report without dragging down a number that is meant to say how
-- technically qualified the candidate is. That changes the score's denominator.
--
-- Historical scores are deliberately NOT recomputed. match_score is read from storage while
-- AnalysisReport.scoreExplanation recomputes its denominator from the stored requirements, so
-- changing the rule without recording it would make every existing report contradict its own
-- explanation. Recomputing instead would rewrite a number past decisions were made on, and could
-- not rewrite summary_md, leaving model-written prose narrating a percentage no longer shown.
--
-- So the rule travels with the row. Old analyses keep V1 and explain themselves in V1's terms; new
-- ones write V2. The default is V1 rather than V2 on purpose: it is the correct value for every row
-- that exists when this migration runs, and new rows set the value explicitly in Kotlin.
alter table analysis
    add column scoring_rule text not null default 'V1_ALL_CATEGORIES';

alter table analysis
    add constraint analysis_scoring_rule_valid check (scoring_rule in (
        'V1_ALL_CATEGORIES', 'V2_SOFT_EXCLUDED'
    ));
