package com.jankowski.rafal.jobassistant.catalog

import com.jankowski.rafal.jobassistant.catalog.internal.CatalogConflictException
import com.jankowski.rafal.jobassistant.catalog.internal.UnknownSkillException
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

    /**
     * The ten Polish terms measured in the corpus, each resolving to an English canonical name.
     *
     * This is what V15's fold was for: before it, "Dokładność" keyed as `dokadno` and
     * "Praca zespołowa" as `pracazespoowa`, because l-stroke has no NFD decomposition and the ASCII
     * filter deleted it outright.
     */
    @Test
    fun `the measured Polish terms resolve to English catalog entries`() {
        val expected = mapOf(
            "Komunikacja" to "Communication",
            "Analiza wymagań" to "Requirements Analysis",
            "Zarządzanie projektem" to "Project Management",
            "Myślenie analityczne" to "Problem Solving",
            "Analiza danych" to "Data Analysis",
            "Zarządzanie interesariuszami" to "Stakeholder Management",
            "Rozwiązywanie problemów" to "Problem Solving",
            "Dokładność" to "Attention to Detail",
            "Praca zespołowa" to "Teamwork",
            "Przywództwo" to "Leadership",
        )

        expected.forEach { (term, skill) ->
            assertEquals(skill, catalog.resolve(term)?.name, "'$term' should resolve to $skill")
        }
    }

    /** An alias is language-agnostic, so the unaccented spelling has to key the same way. */
    @Test
    fun `an unaccented spelling of a Polish alias resolves too`() {
        assertEquals(catalog.resolve("Dokładność")?.id, catalog.resolve("Dokladnosc")?.id)
        assertEquals(catalog.resolve("Praca zespołowa")?.id, catalog.resolve("Praca zespolowa")?.id)
    }

    /**
     * Left in the queue on purpose. These are collapses rather than translations - team management
     * is not quite Leadership and autonomy is not quite Ownership - so a migration author guessing
     * is a worse decision surface than a reviewer with a suggestion in front of them.
     */
    @Test
    fun `terms that are collapses rather than translations are not seeded`() {
        assertNull(catalog.resolve("Zarządzanie zespołem"))
        assertNull(catalog.resolve("Samodzielność"))
    }

    @Test
    fun `the broadened catalog covers the corpus vocabulary that had nowhere to go`() {
        listOf(
            "Test automation", "Manual testing", "Test cases", "API testing",
            "Requirements Analysis", "Project management", "BPMN", "UML", "ERP",
        ).forEach { assertNotNull(catalog.resolve(it), "'$it' should now resolve") }
    }

    /**
     * The testing and analysis vocabulary is PRACTICE, not TESTING, because CvInvariant scans
     * TESTING and a tailored CV describing honest work as "wrote test cases" must not be rejected
     * as a fabricated claim. Named products stay TOOL, where a false claim should be caught.
     */
    @Test
    fun `activity vocabulary is not put in a category CvInvariant scans`() {
        listOf("Test Cases", "Manual Testing", "Test Automation", "Bug Reporting").forEach {
            assertEquals(SkillCategory.PRACTICE, catalog.resolve(it)?.category, "'$it' category")
        }
        assertEquals(SkillCategory.TOOL, catalog.resolve("TestRail")?.category)
    }

    @Test
    fun `a misspelling suggests the skill it nearly is`() {
        val suggestions = catalog.suggest("Kubernets")

        assertEquals("Kubernetes", suggestions.first().skillName)
        assertTrue(suggestions.first().score >= 0.55)
    }

    /**
     * Suggestions are a shortlist for a person, so they must be a shortlist: three chips a reviewer
     * scans, not a ranked dump of everything that shares a trigram.
     */
    @Test
    fun `suggestions are capped and every one names a real catalog skill`() {
        val suggestions = catalog.suggest("Spring Framework Boot")

        assertTrue(suggestions.size <= 3, "got ${suggestions.size}")
        suggestions.forEach { assertNotNull(catalog.findById(it.skillId), "suggested a skill that is not in the catalog") }
    }

    /** The guarantee that makes suggestions safe to show: they cannot become resolution. */
    @Test
    fun `a suggested term still does not resolve`() {
        val term = "Kubernets"

        assertTrue(catalog.suggest(term).isNotEmpty())
        assertNull(catalog.resolve(term), "a suggestion must never become a resolution")
    }

    @Test
    fun `the batch form agrees with the single form`() {
        val terms = listOf("Kubernets", "Postgres SQL", "AI")

        val batch = catalog.suggestAll(terms)

        terms.forEach { assertEquals(catalog.suggest(it), batch[it], "disagreed on '$it'") }
    }

    @Test
    fun `market mentions collapse spellings onto one queue entry and sum`() {
        val term = "Power Apps ${System.nanoTime()}"

        catalog.recordUnmatchedFromMarket(mapOf(term to 3, term.lowercase() to 4))

        val queued = catalog.pendingUnmatchedTerms(500).single { it.term.equals(term, ignoreCase = true) }
        assertEquals(7, queued.marketOccurrences, "two spellings are one term asked for seven times")
        assertEquals(0, queued.occurrences, "the market must not touch the candidate's own counter")
    }

    /**
     * The counter is the corpus's own count, so re-polling unchanged listings must not move it.
     * Accumulating would rank the queue by how often the poll ran rather than by how many
     * employers asked, and the number would grow without bound.
     */
    @Test
    fun `recording market mentions sets the counter rather than accumulating`() {
        val term = "Recount Probe ${System.nanoTime()}"

        catalog.recordUnmatchedFromMarket(mapOf(term to 12))
        catalog.recordUnmatchedFromMarket(mapOf(term to 12))

        val queued = catalog.pendingUnmatchedTerms(500).single { it.term == term }
        assertEquals(12, queued.marketOccurrences)
    }

    /** A term the corpus stops asking for reports what the corpus now says, not its high-water mark. */
    @Test
    fun `a falling market count is written down as well as up`() {
        val term = "Fading Probe ${System.nanoTime()}"

        catalog.recordUnmatchedFromMarket(mapOf(term to 9))
        catalog.recordUnmatchedFromMarket(mapOf(term to 2))

        assertEquals(2, catalog.pendingUnmatchedTerms(500).single { it.term == term }.marketOccurrences)
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

    /**
     * The queue is the only place a term can be reviewed, so an approval that cannot take effect
     * must not consume it. Aliases are unique on the normalised key, so approving against a second
     * skill silently left the term resolving to the first while marking it APPROVED against the
     * second - a disagreement nothing downstream could ever surface again.
     */
    @Test
    fun `approving a term whose key already aliases another skill is refused`() {
        val stamp = System.nanoTime()
        val term = "Groovy Lang $stamp"
        val owner = catalog.createSkill("Owner Skill $stamp", SkillCategory.LANGUAGE, listOf(term))
        val other = catalog.createSkill("Other Skill $stamp", SkillCategory.LANGUAGE)
        // Queued before the alias existed, which is what a migration adding aliases leaves behind.
        catalog.recordUnmatched(term)
        val queued = catalog.pendingUnmatchedTerms(500).single { it.term == term }

        val failure = assertFailsWith<CatalogConflictException> {
            catalog.approveUnmatchedTerm(queued.id, other.id)
        }

        assertTrue(
            failure.message!!.contains(owner.name),
            "the message must name the owning skill, got: ${failure.message}",
        )
        assertEquals(owner.id, catalog.resolve(term)?.id, "resolution must be untouched")
        assertTrue(
            catalog.pendingUnmatchedTerms(500).any { it.id == queued.id },
            "a refused approval must leave the term reviewable",
        )
    }

    /** Same skill is not a conflict: the alias already says what the approval wants it to say. */
    @Test
    fun `approving a term against the skill it already aliases is idempotent`() {
        val stamp = System.nanoTime()
        val term = "Retry Probe $stamp"
        val skill = catalog.createSkill("Retry Skill $stamp", SkillCategory.TOOL)
        catalog.recordUnmatched(term)
        val queued = catalog.pendingUnmatchedTerms(500).single { it.term == term }

        catalog.approveUnmatchedTerm(queued.id, skill.id)
        catalog.approveUnmatchedTerm(queued.id, skill.id)

        assertEquals(skill.id, catalog.resolve(term)?.id)
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
    fun `updating a skill renames it and keeps the old name resolvable`() {
        val oldName = "Rename Probe ${System.nanoTime()}"
        val newName = "Renamed Probe ${System.nanoTime()}"
        val created = catalog.createSkill(oldName, SkillCategory.TOOL)

        val updated = catalog.updateSkill(created.id, newName, SkillCategory.DATABASE)

        assertEquals(newName, updated.name)
        assertEquals(SkillCategory.DATABASE, updated.category)
        assertEquals(created.id, catalog.resolve(oldName)?.id)
        assertEquals(created.id, catalog.resolve(newName)?.id)
    }

    @Test
    fun `updating a skill to a name already taken is refused`() {
        val first = catalog.createSkill("Taken Name ${System.nanoTime()}", SkillCategory.TOOL)
        val second = catalog.createSkill("Other Name ${System.nanoTime()}", SkillCategory.TOOL)

        assertFailsWith<CatalogConflictException> {
            catalog.updateSkill(second.id, first.name, SkillCategory.TOOL)
        }
    }

    @Test
    fun `updating an unknown skill is refused`() {
        assertFailsWith<UnknownSkillException> {
            catalog.updateSkill(Long.MAX_VALUE, "Whatever", SkillCategory.OTHER)
        }
    }

    @Test
    fun `deleting an unused skill removes it from the catalog`() {
        val created = catalog.createSkill("Delete Probe ${System.nanoTime()}", SkillCategory.TOOL)

        catalog.deleteSkill(created.id)

        assertNull(catalog.findById(created.id))
    }

    @Test
    fun `deleting a skill still held by a profile is refused`() {
        val skill = catalog.createSkill("Held Probe ${System.nanoTime()}", SkillCategory.TOOL)
        val profileId = jdbc.sql("insert into profile (name) values (:name) returning id")
            .param("name", "Test Profile ${System.nanoTime()}")
            .query { rs, _ -> rs.getLong("id") }
            .single()
        jdbc.sql(
            "insert into profile_skill (canonical_skill_id, profile_id, proficiency) " +
                "values (:skillId, :profileId, 'WORKING')"
        ).param("skillId", skill.id).param("profileId", profileId).update()

        assertFailsWith<CatalogConflictException> { catalog.deleteSkill(skill.id) }
        assertNotNull(catalog.findById(skill.id))
    }

    @Test
    fun `deleting an unknown skill is refused`() {
        assertFailsWith<UnknownSkillException> { catalog.deleteSkill(Long.MAX_VALUE) }
    }

    @Test
    fun `blank terms are never queued`() {
        val before = catalog.pendingUnmatchedTerms(500).size
        catalog.recordUnmatched("   ")
        catalog.recordUnmatched("---")
        assertEquals(before, catalog.pendingUnmatchedTerms(500).size)
    }
}
