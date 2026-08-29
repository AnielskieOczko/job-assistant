package com.jankowski.rafal.jobassistant

import com.jankowski.rafal.jobassistant.analysis.AggregateGapEntry
import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.LanguageFinding
import com.jankowski.rafal.jobassistant.analysis.LearningPlanItem
import com.jankowski.rafal.jobassistant.analysis.RequirementFinding
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillSuggestion
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTerm
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTermStatus
import com.jankowski.rafal.jobassistant.document.DocumentType
import com.jankowski.rafal.jobassistant.document.GeneratedDocument
import com.jankowski.rafal.jobassistant.llm.LlmCall
import com.jankowski.rafal.jobassistant.market.CorpusSummary
import com.jankowski.rafal.jobassistant.market.IngestionReport
import com.jankowski.rafal.jobassistant.market.IngestionSchedule
import com.jankowski.rafal.jobassistant.market.DemandEntry
import com.jankowski.rafal.jobassistant.market.DemandRanking
import com.jankowski.rafal.jobassistant.market.DemandReport
import com.jankowski.rafal.jobassistant.market.MarketOfferPage
import com.jankowski.rafal.jobassistant.market.MarketOfferSummary
import com.jankowski.rafal.jobassistant.market.MarketSalary
import com.jankowski.rafal.jobassistant.market.MarketScopeReport
import com.jankowski.rafal.jobassistant.market.SalaryBand
import com.jankowski.rafal.jobassistant.market.SalaryGroup
import com.jankowski.rafal.jobassistant.market.SalaryReport
import com.jankowski.rafal.jobassistant.offer.Application
import com.jankowski.rafal.jobassistant.offer.ApplicationStatus
import com.jankowski.rafal.jobassistant.offer.JobOffer
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.Credential
import com.jankowski.rafal.jobassistant.profile.CredentialKind
import com.jankowski.rafal.jobassistant.profile.Education
import com.jankowski.rafal.jobassistant.profile.ExperienceBullet
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.LanguageSkill
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.triage.ModelSuggestion
import com.jankowski.rafal.jobassistant.triage.SuggestionRun
import com.jankowski.rafal.jobassistant.triage.TriageEntry
import com.jankowski.rafal.jobassistant.triage.TriageQueue
import com.jankowski.rafal.jobassistant.triage.TriageRanking
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileLink
import com.jankowski.rafal.jobassistant.profile.ProfileSkill
import com.jankowski.rafal.jobassistant.profile.Project
import com.jankowski.rafal.jobassistant.profile.WorkExperience
import com.jankowski.rafal.jobassistant.profile.internal.ProfileSummary
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * Pins the JSON field names of every DTO that crosses the wire.
 *
 * The frontend's `frontend/src/api/types.ts` is hand-written, so a Kotlin rename would otherwise
 * surface as a silently `undefined` field in the browser rather than a build failure. This does
 * not check types - only that the set of keys is what the frontend was written against, which is
 * the realistic failure mode.
 *
 * **If this test fails, update `frontend/src/api/types.ts` in the same commit.**
 *
 * Note that computed Kotlin getters are serialized too, and some of them keep an `is` prefix
 * (`WorkExperience.isCurrent` is emitted as `isCurrent`, not `current`) - that is exactly the kind
 * of detail this test exists to hold still. No Docker and no Spring context: fast tier.
 */
class ApiContractTest {

    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private fun keysOf(value: Any): Set<String> =
        mapper.readTree(mapper.writeValueAsString(value)).propertyNames().toSet()

    private fun assertKeys(value: Any, vararg expected: String) =
        assertEquals(expected.toSet(), keysOf(value), "${value::class.simpleName} field names changed")

    @Test
    fun `offer module wire format`() = assertAll(
        {
            assertKeys(
                JobOffer(
                    id = 1, contentHash = "h", rawText = "text", sourceUrl = null, title = null,
                    company = null, seniority = null, detectedLanguage = null,
                    createdAt = Instant.EPOCH,
                ),
                "id", "contentHash", "rawText", "sourceUrl", "title", "company", "seniority",
                "detectedLanguage", "createdAt", "displayTitle",
            )
        },
        {
            assertKeys(
                Application(
                    id = 1, offerId = 1, status = ApplicationStatus.SAVED,
                    statusChangedAt = Instant.EPOCH, appliedOn = null, notes = null,
                ),
                "id", "offerId", "status", "statusChangedAt", "appliedOn", "notes",
            )
        },
    )

    @Test
    fun `analysis module wire format`() {
        val requirement = RequirementFinding(
            id = 1, rawText = "Kotlin", skillId = 1, skillName = "Kotlin",
            importance = Importance.MUST_HAVE, status = RequirementStatus.MET,
            evidence = null, rationale = null,
        )

        assertAll(
            {
                assertKeys(
                    requirement,
                    "id", "rawText", "skillId", "skillName", "importance", "status", "evidence",
                    "rationale", "category",
                )
            },
            {
                assertKeys(
                    LanguageFinding("English", LanguageLevel.B2, null, RequirementStatus.MISSING),
                    "language", "requiredLevel", "heldLevel", "status",
                )
            },
            {
                assertKeys(
                    LearningPlanItem(1, "Kotlin", "why", null, null, 1),
                    "skillId", "skillName", "why", "practiceProject", "effortEstimate", "priority",
                )
            },
            {
                assertKeys(
                    AnalysisReport(
                        id = 1, offerId = 1, profileId = 1, state = AnalysisState.DONE, error = null,
                        matchScore = null, summaryMarkdown = null,
                        requirements = listOf(requirement), languageRequirements = emptyList(),
                        learningPlan = emptyList(), createdAt = Instant.EPOCH, completedAt = null,
                    ),
                    "id", "offerId", "profileId", "state", "error", "matchScore", "summaryMarkdown",
                    "requirements", "languageRequirements", "learningPlan", "createdAt",
                    "completedAt", "profileRevision", "scoringRule",
                    // Computed getters the frontend treats as optional convenience.
                    "mustHaves", "niceToHaves", "missingMustHaves", "reportedNotScored",
                    "scoreExplanation",
                )
            },
            {
                assertKeys(
                    AggregateGapEntry(1, "Kotlin", 2, 1, 1),
                    "skillId", "skillName", "demandCount", "gapCount", "mustHaveGapCount",
                    "category", "gapRatio",
                )
            },
        )
    }

    @Test
    fun `profile module wire format`() {
        val bullet = ExperienceBullet(id = 1, text = "Shipped it", skillIds = setOf(1))
        val experience = WorkExperience(
            id = 1, company = "Example", roleTitle = "Engineer", location = null,
            startedOn = LocalDate.EPOCH, endedOn = null, summary = null, bullets = listOf(bullet),
        )

        assertAll(
            { assertKeys(bullet, "id", "text", "skillIds") },
            {
                assertKeys(
                    experience,
                    "id", "company", "roleTitle", "location", "startedOn", "endedOn", "summary",
                    "bullets",
                    // Kotlin's `is` prefix survives here; the frontend types it as `isCurrent`.
                    "isCurrent",
                )
            },
            {
                assertKeys(
                    ProfileSkill(1, 1, Proficiency.EXPERT, null, null),
                    "id", "skillId", "proficiency", "yearsOfExperience", "lastUsedYear",
                )
            },
            { assertKeys(ProfileLink(1, "GitHub", "https://example.com"), "id", "label", "url") },
            { assertKeys(LanguageSkill(1, "English", LanguageLevel.C1), "id", "language", "level") },
            {
                assertKeys(
                    Education(1, "University", "BSc", null, null, null),
                    "id", "institution", "degree", "fieldOfStudy", "startedOn", "endedOn",
                )
            },
            {
                assertKeys(
                    Credential(1, "AWS Solutions Architect", "AWS", CredentialKind.CERTIFICATION, null, null, null, null),
                    "id", "title", "issuer", "kind", "url", "credentialId", "issuedOn", "expiresOn",
                )
            },
            {
                assertKeys(
                    Project(1, "Side project", null, null, null, null, emptySet(), listOf(bullet)),
                    "id", "name", "url", "description", "startedOn", "endedOn", "skillIds", "bullets",
                )
            },
            {
                assertKeys(
                    ProfileDetails(fullName = "Rafal"),
                    "fullName", "headline", "email", "phone", "location", "summary", "careerGoal",
                )
            },
            {
                assertKeys(
                    CandidateProfile(
                        details = ProfileDetails(fullName = "Rafal"), links = emptyList(),
                        skills = emptyList(), experiences = listOf(experience),
                        education = emptyList(), credentials = emptyList(), projects = emptyList(),
                        languages = emptyList(),
                    ),
                    "details", "links", "skills", "experiences", "education", "credentials", "projects",
                    "languages", "revision",
                    // Computed; `bullets` duplicates experiences[].bullets and the UI ignores it.
                    "heldSkillIds", "bullets",
                )
            },
            { assertKeys(ProfileSummary(1, "Java developer", true), "id", "name", "isDefault") },
        )
    }

    @Test
    fun `triage wire formats`() = assertAll(
        {
            assertKeys(
                TriageEntry(
                    termId = 1, term = "Test automation", occurrences = 2, marketOccurrences = 91,
                    inScopeDemand = 7, firstSeenAt = Instant.EPOCH, lastSeenAt = Instant.EPOCH,
                ),
                "termId", "term", "occurrences", "marketOccurrences", "inScopeDemand",
                "firstSeenAt", "lastSeenAt", "suggestions", "modelSuggestions",
            )
        },
        {
            assertKeys(
                SkillSuggestion(1, "Kubernetes", SkillCategory.DEVOPS, "k8s", 0.82),
                "skillId", "skillName", "category", "matchedAlias", "score",
            )
        },
        {
            assertKeys(
                TriageQueue(
                    entries = emptyList(), matching = 412, pending = 1540, minOccurrences = 3,
                    ranking = TriageRanking.SCOPE, scopeSkills = listOf("Java"),
                ),
                "entries", "matching", "pending", "minOccurrences", "ranking", "scopeSkills",
            )
        },
        {
            assertKeys(
                ModelSuggestion(1, "Kubernetes", SkillCategory.DEVOPS, "Means the same thing.", "openrouter"),
                "skillId", "skillName", "category", "rationale", "modelProfile",
            )
        },
        {
            assertKeys(
                SuggestionRun(termsConsidered = 25, termsSent = 12, suggestionsStored = 4),
                "termsConsidered", "termsSent", "suggestionsStored", "droppedUnresolvable",
                "droppedUnrequested",
            )
        },
    )

    @Test
    fun `catalog document and llm wire formats`() = assertAll(
        { assertKeys(CanonicalSkill(1, "Kotlin", SkillCategory.LANGUAGE), "id", "name", "category") },
        {
            assertKeys(
                UnmatchedTerm(1, "iceberg", 2, 47, Instant.EPOCH, Instant.EPOCH, UnmatchedTermStatus.PENDING, null),
                "id", "term", "occurrences", "marketOccurrences", "firstSeenAt", "lastSeenAt", "status",
                "resolvedSkillId",
            )
        },
        {
            assertKeys(
                GeneratedDocument(
                    id = 1, offerId = 1, profileId = 1, analysisId = 1, type = DocumentType.CV,
                    language = "English", html = "<html/>", createdAt = Instant.EPOCH,
                ),
                "id", "offerId", "profileId", "analysisId", "type", "language", "html", "createdAt",
                "profileRevision", "droppedBulletCount", "droppedSkillCount",
            )
        },
        {
            assertKeys(
                LlmCall(1, "EXTRACTION", "openrouter", "model", "CoreWeave", 1, 1, 1L, null, Instant.EPOCH),
                "id", "task", "modelProfile", "modelName", "servingProvider", "inputTokens",
                "outputTokens", "latencyMs", "error", "createdAt",
            )
        },
    )

    @Test
    fun `market dashboard wire formats`() {
        val scope = MarketScopeReport(
            sources = listOf("solid.jobs"), scopeSkills = listOf("Java"), unresolvedScopeSkills = emptyList(),
            offersInScope = 205, expiredInScope = 9, corpusOffers = 1493,
            firstSeenAt = Instant.EPOCH, lastSeenAt = Instant.EPOCH,
            skillMentions = 1689, unresolvedMentions = 498,
        )

        assertAll(
            {
                assertKeys(
                    scope,
                    "sources", "scopeSkills", "unresolvedScopeSkills", "offersInScope", "expiredInScope",
                    "corpusOffers", "firstSeenAt", "lastSeenAt", "skillMentions", "unresolvedMentions",
                    // Computed: the honesty floors live in Kotlin so the UI renders a decision
                    // rather than re-deriving one nothing tests.
                    "meetsSalaryFloor",
                )
            },
            {
                assertKeys(
                    SalaryGroup(
                        employmentType = "B2B", currency = "PLN", period = "Month", offers = 183,
                        medianFrom = BigDecimal.ONE, medianTo = BigDecimal.ONE,
                        p25From = BigDecimal.ONE, p75To = BigDecimal.ONE,
                    ),
                    "employmentType", "currency", "period", "offers", "medianFrom", "medianTo",
                    "p25From", "p75To", "meetsSampleFloor",
                )
            },
            {
                assertKeys(
                    SalaryReport(groups = emptyList(), offersInScope = 205, offersWithSalary = 205),
                    "groups", "offersInScope", "offersWithSalary", "coverage", "meetsCoverageFloor",
                )
            },
            {
                assertKeys(
                    DemandEntry(
                        skillId = 1, skillName = "Kubernetes", category = SkillCategory.DEVOPS,
                        offers = 31, requiredOffers = 29, status = CoverageStatus.MISSING,
                    ),
                    "skillId", "skillName", "category", "offers", "requiredOffers", "status",
                    "coveredBySkillId", "coveredBySkillName", "levelMix", "salary",
                )
            },
            {
                assertKeys(
                    SalaryBand(
                        offers = 31, medianFrom = BigDecimal.ONE, medianTo = BigDecimal.ONE,
                        currency = "PLN", period = "Month", employmentType = "B2B",
                    ),
                    "offers", "medianFrom", "medianTo", "currency", "period", "employmentType",
                )
            },
            {
                assertKeys(
                    DemandReport(
                        entries = emptyList(), scope = scope, skillsInScope = 340,
                        unmetSkillsInScope = 280, ranking = DemandRanking.UNMET, limit = 100,
                    ),
                    "entries", "scope", "skillsInScope", "unmetSkillsInScope", "ranking", "limit",
                )
            },
            {
                assertKeys(
                    MarketSalary(BigDecimal.ONE, BigDecimal.ONE, "PLN", "Month", "B2B"),
                    "from", "to", "currency", "period", "employmentType",
                )
            },
            {
                assertKeys(
                    MarketOfferSummary(
                        id = 1, source = "solid.jobs", title = "Backend Engineer", company = null,
                        url = null, experienceLevel = "Senior", isRemote = true, isHybrid = false,
                        locations = listOf("Kraków"), salary = null, validTo = null,
                        lastSeenAt = Instant.EPOCH, skillsResolved = 6, skillsCovered = 4,
                        skillsUnresolved = 3,
                    ),
                    "id", "source", "title", "company", "url", "experienceLevel", "isRemote",
                    "isHybrid", "locations", "salary", "validTo", "lastSeenAt", "skillsResolved",
                    "skillsCovered", "skillsUnresolved",
                )
            },
            {
                assertKeys(
                    MarketOfferPage(entries = emptyList(), total = 205, limit = 100, offset = 0),
                    "entries", "total", "limit", "offset",
                )
            },
            {
                assertKeys(
                    CorpusSummary("solid.jobs", 1493, 1449, Instant.EPOCH, Instant.EPOCH),
                    "source", "offers", "currentlyValid", "firstSeenAt", "lastSeenAt",
                )
            },
            {
                // The computed properties are part of the wire shape: the UI reports the counts and
                // the rate together, and skillResolutionRate is deliberately null below its sample
                // floor rather than a misleading 1.0.
                assertKeys(
                    IngestionReport(
                        source = "solid.jobs", startedAt = Instant.EPOCH, finishedAt = Instant.EPOCH,
                        pagesFetched = 3, offersSeen = 1493, offersInserted = 0, offersUpdated = 1493,
                        skillMentions = 9318, skillsResolved = 4160, distinctUnresolvedTerms = 900,
                    ),
                    "source", "startedAt", "finishedAt", "pagesFetched", "offersSeen",
                    "offersInserted", "offersUpdated", "skillMentions", "skillsResolved",
                    "distinctUnresolvedTerms", "error", "skillsUnresolved", "skillResolutionRate",
                )
            },
            {
                assertKeys(
                    IngestionSchedule(
                        scheduled = true, cron = "0 20 4 * * *", nextPollAt = Instant.EPOCH,
                        lastPolledAt = Instant.EPOCH,
                    ),
                    "scheduled", "cron", "nextPollAt", "lastPolledAt",
                )
            },
        )
    }
}
