-- Broadens the catalog to the IT vocabulary the ingested corpus actually shows demand for, and
-- gives the measured Polish terms English homes.
--
-- Driven by frequency rather than taste: every skill below was counted in a 1,493-offer sample of
-- the solid.jobs IT division, and roughly tracks the terms at five mentions or more. The catalog
-- was seeded for JVM backend roles, but the whole division is ingested and is thick with QA, BA and
-- PM work, so the review queue filled with ordinary English testing and analysis vocabulary that
-- simply had nowhere to go.
--
-- Canonical names are English and the original-language term is an alias. That is the convention
-- offer-extraction-user.md already states ("map its requirements onto the English catalog names"),
-- and the alias mechanism has never cared what script it holds.
--
-- normalized_alias is hand-written here exactly as in V2, and SkillCatalogIntegrationTest is the
-- oracle: it recomputes every stored key with SkillNormalizer and fails on any disagreement.
--
-- This migration cannot predate V15. Before the fold, "Dokladnosc" keyed as dokadno and
-- "Praca zespolowa" as pracazespoowa, because l-stroke has no NFD decomposition and the ASCII
-- filter deleted it. The keys below are the folded ones, so on a pre-V15 normaliser the drift test
-- would fail and the fix would mean editing an applied migration.
--
-- Categories are chosen with CvInvariant in mind. It scans TESTING and TOOL but not PRACTICE,
-- OTHER or SOFT, so the activity vocabulary ("Test Cases", "Manual Testing") is PRACTICE: those are
-- ways of working rather than technologies, and a tailored CV describing honest work as "wrote test
-- cases" must not be rejected as a fabricated claim. Named products stay TOOL, where claiming one
-- you do not have is exactly what should be caught.

insert into canonical_skill (name, category) values
    ('Test Automation', 'PRACTICE'),
    ('Manual Testing', 'PRACTICE'),
    ('Test Cases', 'PRACTICE'),
    ('API Testing', 'PRACTICE'),
    ('Functional Testing', 'PRACTICE'),
    ('Test Planning', 'PRACTICE'),
    ('Test Scenarios', 'PRACTICE'),
    ('Bug Reporting', 'PRACTICE'),
    ('Regression Testing', 'PRACTICE'),
    ('Exploratory Testing', 'PRACTICE'),
    ('TestRail', 'TOOL'),
    ('X-Ray', 'TOOL'),
    ('Requirements Analysis', 'PRACTICE'),
    ('Project Management', 'PRACTICE'),
    ('Data Analysis', 'PRACTICE'),
    ('Root Cause Analysis', 'PRACTICE'),
    ('Risk Management', 'PRACTICE'),
    ('Systems Development Life Cycle', 'PRACTICE'),
    ('UX Design', 'PRACTICE'),
    ('BPMN', 'OTHER'),
    ('UML', 'OTHER'),
    ('ERP', 'OTHER'),
    ('Salesforce', 'TOOL'),
    ('MS Excel', 'TOOL'),
    ('Attention to Detail', 'SOFT')
on conflict (name) do nothing;

-- on conflict do nothing rather than a bare insert: a term approved by hand before this migration
-- ran would already own its key, and failing the whole migration over one such row would be worse
-- than leaving that row's existing decision in place.
insert into skill_alias (canonical_skill_id, alias, normalized_alias)
select cs.id, v.alias, v.normalized_alias
from (values
    ('Test Automation', 'Test Automation', 'testautomation'),
    ('Manual Testing', 'Manual Testing', 'manualtesting'),
    ('Test Cases', 'Test Cases', 'testcases'),
    ('API Testing', 'API Testing', 'apitesting'),
    ('Functional Testing', 'Functional Testing', 'functionaltesting'),
    ('Test Planning', 'Test Planning', 'testplanning'),
    ('Test Scenarios', 'Test Scenarios', 'testscenarios'),
    ('Bug Reporting', 'Bug Reporting', 'bugreporting'),
    ('Regression Testing', 'Regression Testing', 'regressiontesting'),
    ('Exploratory Testing', 'Exploratory Testing', 'exploratorytesting'),
    ('TestRail', 'TestRail', 'testrail'),
    ('X-Ray', 'X-Ray', 'xray'),
    ('Requirements Analysis', 'Requirements Analysis', 'requirementsanalysis'),
    ('Project Management', 'Project Management', 'projectmanagement'),
    ('Data Analysis', 'Data Analysis', 'dataanalysis'),
    ('Root Cause Analysis', 'Root Cause Analysis', 'rootcauseanalysis'),
    ('Risk Management', 'Risk Management', 'riskmanagement'),
    ('Systems Development Life Cycle', 'Systems Development Life Cycle', 'systemsdevelopmentlifecycle'),
    ('UX Design', 'UX Design', 'uxdesign'),
    ('BPMN', 'BPMN', 'bpmn'),
    ('UML', 'UML', 'uml'),
    ('ERP', 'ERP', 'erp'),
    ('Salesforce', 'Salesforce', 'salesforce'),
    ('MS Excel', 'MS Excel', 'msexcel'),
    ('Attention to Detail', 'Attention to Detail', 'attentiontodetail'),
    ('Test Automation', 'Automated Testing', 'automatedtesting'),
    ('Test Automation', 'Automation Testing', 'automationtesting'),
    ('Manual Testing', 'Manual Tests', 'manualtests'),
    ('Test Cases', 'Test Case', 'testcase'),
    ('API Testing', 'API Tests', 'apitests'),
    ('Systems Development Life Cycle', 'SDLC', 'sdlc'),
    ('Root Cause Analysis', 'RCA', 'rca'),
    ('Requirements Analysis', 'Requirements Engineering', 'requirementsengineering'),
    ('Requirements Analysis', 'Requirement Analysis', 'requirementanalysis'),
    ('Data Analysis', 'Data Analytics', 'dataanalytics'),
    ('MS Excel', 'Microsoft Excel', 'microsoftexcel'),
    ('MS Excel', 'Excel', 'excel'),
    ('UX Design', 'User Experience Design', 'userexperiencedesign'),
    ('BPMN', 'Business Process Model and Notation', 'businessprocessmodelandnotation'),
    ('UML', 'Unified Modeling Language', 'unifiedmodelinglanguage'),
    ('ERP', 'Enterprise Resource Planning', 'enterpriseresourceplanning'),
    ('Communication', 'Komunikacja', 'komunikacja'),  -- 44 mentions
    ('Requirements Analysis', 'Analiza wymagań', 'analizawymagan'),  -- 31 mentions
    ('Project Management', 'Zarządzanie projektem', 'zarzadzanieprojektem'),  -- 24 mentions
    ('Problem Solving', 'Myślenie analityczne', 'myslenieanalityczne'),  -- 22 mentions
    ('Data Analysis', 'Analiza danych', 'analizadanych'),  -- 22 mentions
    ('Stakeholder Management', 'Zarządzanie interesariuszami', 'zarzadzanieinteresariuszami'),  -- 20 mentions
    ('Problem Solving', 'Rozwiązywanie problemów', 'rozwiazywanieproblemow'),  -- 18 mentions
    ('Attention to Detail', 'Dokładność', 'dokladnosc'),  -- 8 mentions
    ('Teamwork', 'Praca zespołowa', 'pracazespolowa'),  -- 7 mentions
    ('Leadership', 'Przywództwo', 'przywodztwo')  -- 6 mentions
) as v(skill_name, alias, normalized_alias)
join canonical_skill cs on cs.name = v.skill_name
on conflict (normalized_alias) do nothing;

-- Every migration that adds aliases owes this: without it the queue keeps offering terms that now
-- resolve, and a reviewer is asked to decide something already decided. Same statement as V15's.
update unmatched_term t
   set status = 'APPROVED', resolved_skill_id = a.canonical_skill_id
  from skill_alias a
 where a.normalized_alias = t.normalized_term
   and t.status = 'PENDING';
