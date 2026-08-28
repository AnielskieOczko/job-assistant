package com.jankowski.rafal.jobassistant.catalog

import com.jankowski.rafal.jobassistant.catalog.internal.SkillSimilarity
import com.jankowski.rafal.jobassistant.catalog.internal.SkillSimilarity.Candidate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure scoring, no container. Candidates are written as normalised keys because that is what the
 * real index holds - scoring raw text would be a different function.
 */
class SkillSimilarityTest {

    private fun candidate(id: Long, spelling: String) =
        Candidate(id, spelling, SkillNormalizer.normalize(spelling))

    private fun rank(term: String, vararg spellings: String, limit: Int = 3) =
        SkillSimilarity.rank(
            SkillNormalizer.normalize(term),
            spellings.mapIndexed { index, spelling -> candidate(index.toLong(), spelling) },
            limit,
        )

    @Test
    fun `a near-miss spelling finds its skill`() {
        val matches = rank("Kubernets", "Kubernetes", "Kafka", "Terraform")

        assertEquals("Kubernetes", matches.first().spelling)
    }

    @Test
    fun `a longer name containing a catalog name is suggested`() {
        val matches = rank("Spring Boot Framework", "Spring Boot", "Hibernate")

        assertEquals("Spring Boot", matches.first().spelling)
    }

    /**
     * The case the whole `MIN_QUERY_LENGTH` guard exists for. A two-letter key shares a trigram
     * with half the catalog, and a chip row that is always wrong teaches the reviewer to ignore
     * chips entirely.
     */
    @Test
    fun `a very short term suggests nothing rather than everything`() {
        assertTrue(rank("AI", "Java", "Kotlin", "Airflow", "AWS").isEmpty())
        assertTrue(rank("Go", "Golang", "Google Cloud").isEmpty())
    }

    @Test
    fun `an unrelated term suggests nothing`() {
        assertTrue(rank("Zarządzanie zespołem", "Kubernetes", "PostgreSQL", "React").isEmpty())
    }

    /** A skill with many aliases must not fill the shortlist with itself. */
    @Test
    fun `one entry per skill, however many aliases matched`() {
        val matches = SkillSimilarity.rank(
            SkillNormalizer.normalize("Postgres SQL"),
            listOf(
                Candidate(1, "PostgreSQL", SkillNormalizer.normalize("PostgreSQL")),
                Candidate(1, "Postgres", SkillNormalizer.normalize("Postgres")),
                Candidate(1, "postgresql", SkillNormalizer.normalize("postgresql")),
            ),
        )

        assertEquals(1, matches.size)
        assertEquals(1L, matches.single().skillId)
    }

    @Test
    fun `the limit is honoured`() {
        val matches = rank("Testing", "Testing", "Test Automation", "Manual Testing", limit = 2)

        assertTrue(matches.size <= 2)
    }

    /** Chips that reshuffle between page loads are unusable, so ties resolve the same way twice. */
    @Test
    fun `ranking is stable across calls and independent of candidate order`() {
        val spellings = listOf("Spring Boot", "Spring", "Spring Data", "Spring Security")
        val forward = SkillSimilarity.rank(
            SkillNormalizer.normalize("Spring Framework"),
            spellings.mapIndexed { i, s -> candidate(i.toLong(), s) },
        )
        val reversed = SkillSimilarity.rank(
            SkillNormalizer.normalize("Spring Framework"),
            spellings.mapIndexed { i, s -> candidate(i.toLong(), s) }.reversed(),
        )

        assertEquals(forward, reversed)
    }

    /**
     * Containment earns a hearing, not a verdict: "test" sits inside "test automation" without
     * being what the reviewer meant, so it must never outrank a genuine similarity.
     */
    @Test
    fun `containment never outranks a real similarity`() {
        val matches = rank("Testing", "Test Automation", "Testing")

        assertEquals("Testing", matches.first().spelling)
        assertTrue(matches.first().score > SkillSimilarity.THRESHOLD)
    }

    /** Every returned score has to clear the bar, or the threshold is decorative. */
    @Test
    fun `nothing below the threshold is returned`() {
        val matches = rank("Requirements Analysis", "React", "Redis", "Requirements Engineering")

        assertTrue(matches.all { it.score >= SkillSimilarity.THRESHOLD }, "got $matches")
    }

    /**
     * The reason scoring runs over normalised keys rather than raw text: a Polish spelling and its
     * unaccented form are one key, so they cannot score differently.
     */
    @Test
    fun `accented and unaccented spellings score identically`() {
        val accented = rank("Współpraca zespołowa", "Współpraca")
        val plain = rank("Wspolpraca zespolowa", "Współpraca")

        assertEquals(accented.map { it.score }, plain.map { it.score })
        assertTrue(accented.isNotEmpty())
    }
}
