package com.jankowski.rafal.jobassistant.catalog

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class SkillCatalogIntegrationTest(
    @Autowired private val catalog: SkillCatalog,
    @Autowired private val jdbc: JdbcClient,
) {

    @Test
    fun `seed migration loads a usable catalog`() {
        val all = catalog.findAll()
        assertTrue(all.size >= 150, "expected a substantial seed catalog, got ${all.size}")
        assertTrue(all.map { it.name }.contains("Kotlin"))
    }

    /**
     * Guards against the seed SQL and SkillNormalizer drifting apart. If this fails, a lookup
     * that should work will silently return null in production.
     */
    @Test
    fun `every stored alias matches the normaliser`() {
        val rows = jdbc.sql("select alias, normalized_alias from skill_alias")
            .query { rs, _ -> rs.getString("alias") to rs.getString("normalized_alias") }
            .list()

        assertTrue(rows.isNotEmpty())
        val drifted = rows.filter { (alias, stored) -> SkillNormalizer.normalize(alias) != stored }
        assertTrue(drifted.isEmpty(), "seed normalisation drifted for: $drifted")
    }

    @Test
    fun `resolves spelling variants to the same canonical skill`() {
        val canonical = assertNotNull(catalog.resolve("React"))
        assertEquals(canonical.id, catalog.resolve("React.js")?.id)
        assertEquals(canonical.id, catalog.resolve("ReactJS")?.id)
        assertEquals(canonical.id, catalog.resolve("  react.JS ")?.id)
    }

    @Test
    fun `resolves a paraphrase that was seeded as an alias`() {
        val k8s = assertNotNull(catalog.resolve("Kubernetes"))
        assertEquals(k8s.id, catalog.resolve("K8s")?.id)
        assertEquals(k8s.id, catalog.resolve("container orchestration")?.id)
    }

    @Test
    fun `returns null rather than guessing at an unknown term`() {
        assertNull(catalog.resolve("Underwater Basket Weaving"))
    }

    @Test
    fun `resolveAll batches and preserves unresolved terms as null`() {
        val result = catalog.resolveAll(listOf("Kotlin", "Postgres", "Nonsense Skill XYZ"))

        assertEquals(3, result.size)
        assertNotNull(result["Kotlin"])
        assertEquals("PostgreSQL", result["Postgres"]?.name)
        assertNull(result["Nonsense Skill XYZ"])
    }

    @Test
    fun `IMPLIES edge makes the implied skill MET`() {
        val springBoot = assertNotNull(catalog.resolve("Spring Boot"))
        val spring = assertNotNull(catalog.resolve("Spring"))

        val coverage = catalog.coverageFor(setOf(springBoot.id))

        assertEquals(CoverageStatus.MET, coverage.statusFor(springBoot.id))
        assertEquals(CoverageStatus.MET, coverage.statusFor(spring.id))
    }

    @Test
    fun `RELATED edge makes the adjacent skill PARTIAL, not MET`() {
        val quarkus = assertNotNull(catalog.resolve("Quarkus"))
        val springBoot = assertNotNull(catalog.resolve("Spring Boot"))

        val coverage = catalog.coverageFor(setOf(quarkus.id))

        assertEquals(CoverageStatus.MET, coverage.statusFor(quarkus.id))
        assertEquals(CoverageStatus.PARTIAL, coverage.statusFor(springBoot.id))
    }

    @Test
    fun `a skill covered by IMPLIES is never downgraded to PARTIAL`() {
        // Kotlin RELATED Java and Kotlin IMPLIES JVM; holding both Kotlin and Java must leave
        // Java MET rather than PARTIAL.
        val kotlin = assertNotNull(catalog.resolve("Kotlin"))
        val java = assertNotNull(catalog.resolve("Java"))

        val coverage = catalog.coverageFor(setOf(kotlin.id, java.id))

        assertEquals(CoverageStatus.MET, coverage.statusFor(java.id))
    }

    @Test
    fun `coverage names which held skill explains a PARTIAL`() {
        val quarkus = assertNotNull(catalog.resolve("Quarkus"))
        val springBoot = assertNotNull(catalog.resolve("Spring Boot"))

        val coverage = catalog.coverageFor(setOf(quarkus.id))

        assertEquals(quarkus.id, coverage.coveringSkillFor(springBoot.id))
    }

    @Test
    fun `unheld unrelated skill is MISSING`() {
        val kotlin = assertNotNull(catalog.resolve("Kotlin"))
        val figma = assertNotNull(catalog.resolve("Figma"))

        assertEquals(CoverageStatus.MISSING, catalog.coverageFor(setOf(kotlin.id)).statusFor(figma.id))
    }

    @Test
    fun `empty held set yields empty coverage`() {
        assertEquals(SkillCoverage.EMPTY, catalog.coverageFor(emptySet()))
    }

    @Test
    fun `recording an unmatched term twice increments rather than duplicating`() {
        val term = "Bespoke Frobnicator ${System.nanoTime()}"

        catalog.recordUnmatched(term)
        catalog.recordUnmatched(term.lowercase())

        val recorded = catalog.pendingUnmatchedTerms(500).single { it.term == term }
        assertEquals(2, recorded.occurrences)
    }

    @Test
    fun `approving an unmatched term makes it resolve from then on`() {
        val term = "Kotlin Lang ${System.nanoTime()}"
        val kotlin = assertNotNull(catalog.resolve("Kotlin"))
        catalog.recordUnmatched(term)
        val queued = catalog.pendingUnmatchedTerms(500).single { it.term == term }

        assertNull(catalog.resolve(term))
        catalog.approveUnmatchedTerm(queued.id, kotlin.id)

        assertEquals(kotlin.id, catalog.resolve(term)?.id)
        assertTrue(catalog.pendingUnmatchedTerms(500).none { it.id == queued.id })
    }

    @Test
    fun `rejecting an unmatched term leaves the queue without adding an alias`() {
        val term = "Not A Skill ${System.nanoTime()}"
        catalog.recordUnmatched(term)
        val queued = catalog.pendingUnmatchedTerms(500).single { it.term == term }

        catalog.rejectUnmatchedTerm(queued.id)

        assertNull(catalog.resolve(term))
        assertTrue(catalog.pendingUnmatchedTerms(500).none { it.id == queued.id })
    }

    @Test
    fun `creating a skill makes it and its aliases resolvable`() {
        val name = "Apache Iceberg ${System.nanoTime()}"

        val created = catalog.createSkill(name, SkillCategory.DATABASE, listOf("Iceberg ${System.nanoTime()}"))

        assertEquals(created.id, catalog.resolve(name)?.id)
        assertEquals(SkillCategory.DATABASE, created.category)
    }

    @Test
    fun `creating an existing skill is idempotent rather than a duplicate`() {
        val name = "Duplicate Probe ${System.nanoTime()}"

        val first = catalog.createSkill(name, SkillCategory.TOOL)
        val second = catalog.createSkill(name, SkillCategory.TOOL)

        assertEquals(first.id, second.id)
    }

    @Test
    fun `creating a skill cannot steal an alias owned by another skill`() {
        assertFailsWith<IllegalArgumentException> {
            catalog.createSkill("Something Else ${System.nanoTime()}", SkillCategory.OTHER, listOf("Kotlin"))
        }
    }

    @Test
    fun `blank terms are never queued`() {
        val before = catalog.pendingUnmatchedTerms(500).size
        catalog.recordUnmatched("   ")
        catalog.recordUnmatched("---")
        assertEquals(before, catalog.pendingUnmatchedTerms(500).size)
    }
}
