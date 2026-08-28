package com.jankowski.rafal.jobassistant.analysis

import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a report explains its own score. Pure logic, no container.
 *
 * The case worth guarding is not the new rule but the old one. `matchScore` is read from storage
 * while `scoreExplanation` recomputes its denominator from the stored requirements, so a report
 * scored under V1 has to keep explaining itself in V1's terms. Applying today's rule to yesterday's
 * number would make every existing report contradict itself, and the contradiction would be silent.
 */
class AnalysisReportScoringTest {

    private fun finding(
        id: Long,
        name: String,
        status: RequirementStatus,
        category: SkillCategory?,
        importance: Importance = Importance.MUST_HAVE,
    ) = RequirementFinding(
        id = id, rawText = "needs $name", skillId = id, skillName = name,
        importance = importance, status = status, evidence = null, rationale = null,
        category = category,
    )

    /** Two technical must-haves, one met; plus a missing soft must-have. */
    private fun report(rule: ScoringRule) = AnalysisReport(
        id = 1, offerId = 1, state = AnalysisState.DONE, error = null,
        matchScore = null, summaryMarkdown = null,
        requirements = listOf(
            finding(1, "Kotlin", RequirementStatus.MET, SkillCategory.LANGUAGE),
            finding(2, "Kubernetes", RequirementStatus.MISSING, SkillCategory.DEVOPS),
            finding(3, "Communication", RequirementStatus.MISSING, SkillCategory.SOFT),
        ),
        languageRequirements = emptyList(), learningPlan = emptyList(),
        createdAt = Instant.EPOCH, completedAt = null,
        scoringRule = rule,
    )

    @Test
    fun `a report scored under the old rule still counts soft skills in its denominator`() {
        val explanation = report(ScoringRule.V1_ALL_CATEGORIES).scoreExplanation

        assertEquals("(1 met + 0.5 x 0 partial) / 3 must-have requirements", explanation)
    }

    @Test
    fun `a report scored under the new rule leaves soft skills out and says so`() {
        val explanation = report(ScoringRule.V2_SOFT_EXCLUDED).scoreExplanation

        assertEquals("(1 met + 0.5 x 0 partial) / 2 technical must-have requirements", explanation)
    }

    /** Excluded from the score is not excluded from the report. The offer really did ask for it. */
    @Test
    fun `a soft requirement stays in the requirement list under both rules`() {
        ScoringRule.entries.forEach { rule ->
            val names = report(rule).requirements.map { it.skillName }
            assertTrue(names.contains("Communication"), "missing under $rule")
        }
    }

    @Test
    fun `the new rule names what it left out of the score`() {
        val reported = report(ScoringRule.V2_SOFT_EXCLUDED).reportedNotScored

        assertEquals(listOf("Communication"), reported.map { it.skillName })
    }

    /** Under V1 nothing was excluded, so there is nothing to caveat and the list stays empty. */
    @Test
    fun `the old rule reports nothing as excluded`() {
        assertTrue(report(ScoringRule.V1_ALL_CATEGORIES).reportedNotScored.isEmpty())
    }

    /** A report defaults to V1: that is what every row written before the change holds. */
    @Test
    fun `the default rule is the historical one`() {
        val defaulted = AnalysisReport(
            id = 1, offerId = 1, state = AnalysisState.DONE, error = null, matchScore = null,
            summaryMarkdown = null, requirements = emptyList(), languageRequirements = emptyList(),
            learningPlan = emptyList(), createdAt = Instant.EPOCH, completedAt = null,
        )

        assertEquals(ScoringRule.V1_ALL_CATEGORIES, defaulted.scoringRule)
    }
}
