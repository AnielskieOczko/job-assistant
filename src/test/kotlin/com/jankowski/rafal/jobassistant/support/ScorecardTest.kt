package com.jankowski.rafal.jobassistant.support

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The scorecard is the only part of the eval tier that runs in the fast tier. It has to be,
 * because the tier it serves costs tokens and is run by hand - a scorecard that silently wrote
 * nothing would not be noticed until someone wanted to compare two runs and had neither.
 */
internal class ScorecardTest {

    @Test
    fun `metrics recorded for one fixture from two tests are merged, not duplicated`(@TempDir dir: Path) {
        val scorecard = Scorecard()
        scorecard.record("offer-extraction", "01-fixture", mapOf("recall" to 0.8), emptyMap())
        scorecard.record("offer-extraction", "01-fixture", mapOf("mustHaveRetention" to 0.5), emptyMap())
        scorecard.write(dir)

        val markdown = dir.resolve("eval-report.md").readText()
        assertEquals(1, markdown.lines().count { it.startsWith("| 01-fixture ") }, markdown)
        assertContains(markdown, "0.80")
        assertContains(markdown, "0.50")
    }

    @Test
    fun `a mean ignores fixtures that never reported that metric`(@TempDir dir: Path) {
        val scorecard = Scorecard()
        scorecard.record("cv-tailoring", "a", mapOf("bulletDropRate" to 0.0), emptyMap())
        scorecard.record("cv-tailoring", "b", mapOf("bulletDropRate" to 1.0), emptyMap())
        scorecard.record("cv-tailoring", "c", mapOf("summaryWords" to 20.0), emptyMap())
        scorecard.write(dir)

        // Mean of 0.0 and 1.0, not of 0.0, 1.0 and an absent third.
        assertContains(dir.resolve("eval-report.md").readText(), "| **mean** | **0.50** |")
    }

    @Test
    fun `notes carry the diagnosis alongside the numbers`(@TempDir dir: Path) {
        val scorecard = Scorecard()
        scorecard.record("cv-tailoring", "01", mapOf("skillDropRate" to 0.5), mapOf("unheld skills claimed" to "Kubernetes"))
        scorecard.write(dir)

        val markdown = dir.resolve("eval-report.md").readText()
        assertContains(markdown, "unheld skills claimed: Kubernetes")
    }

    @Test
    fun `run context records which model produced the numbers`(@TempDir dir: Path) {
        val scorecard = Scorecard()
        scorecard.describe("extraction.profile", "openrouter")
        scorecard.record("offer-extraction", "01", mapOf("recall" to 1.0), emptyMap())
        scorecard.write(dir)

        assertContains(dir.resolve("eval-report.md").readText(), "| extraction.profile | openrouter |")
        assertContains(dir.resolve("eval-report.json").readText(), "\"extraction.profile\":\"openrouter\"")
    }

    @Test
    fun `a run with no eval results writes nothing at all`(@TempDir dir: Path) {
        val scorecard = Scorecard()
        assertTrue(scorecard.isEmpty)
        scorecard.write(dir)

        assertFalse(dir.resolve("eval-report.md").toFile().exists())
        assertFalse(dir.resolve("eval-report.json").toFile().exists())
    }
}
