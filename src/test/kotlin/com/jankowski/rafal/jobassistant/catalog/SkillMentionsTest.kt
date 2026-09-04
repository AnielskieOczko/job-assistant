package com.jankowski.rafal.jobassistant.catalog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared reading behind two guards with two different consequences: a finished CV is thrown
 * away over a hit, and a polish suggestion is merely flagged. Both have to agree about what counts
 * as a mention, which is why it is one function rather than two.
 *
 * `CvInvariantTest` exercises the same reading from the document side and is deliberately left
 * alone - if extraction ever changes behaviour, that suite fails without having been touched, which
 * is the check worth having. This one states the reading as its own subject: pure logic, fast tier,
 * no container.
 */
class SkillMentionsTest {

    private val kotlin = CanonicalSkill(1, "Kotlin", SkillCategory.LANGUAGE)
    private val kubernetes = CanonicalSkill(2, "Kubernetes", SkillCategory.DEVOPS)
    private val kafka = CanonicalSkill(3, "Apache Kafka", SkillCategory.MESSAGING)
    private val terraform = CanonicalSkill(4, "Terraform", SkillCategory.DEVOPS)
    private val ownership = CanonicalSkill(5, "Ownership", SkillCategory.SOFT)
    private val caching = CanonicalSkill(6, "Caching", SkillCategory.PRACTICE)

    private val catalog = listOf(kotlin, kubernetes, kafka, terraform, ownership, caching)
    private val held = setOf(kotlin.id)

    private fun unheld(text: String) = SkillMentions.unheld(text, catalog, held)

    @Test
    fun `a skill the profile does not hold is reported by name`() {
        assertEquals(listOf("Terraform"), unheld("Provisioned the whole stack with Terraform."))
    }

    @Test
    fun `a skill the profile holds is never reported`() {
        assertTrue(unheld("Rewrote the ingest path in Kotlin.").isEmpty())
    }

    @Test
    fun `several mentions come back sorted and deduplicated`() {
        assertEquals(
            listOf("Apache Kafka", "Kubernetes"),
            unheld("Ran Kubernetes clusters, moved events over Apache Kafka, then more Kubernetes."),
        )
    }

    @Test
    fun `the report names the skill and never the sentence it was found in`() {
        // What comes back is rendered next to the suggestion, so it must be the term to look at -
        // a caller that wanted the surrounding text already has the text it passed in.
        assertEquals(listOf("Kubernetes"), unheld("Migrated a legacy deployment onto Kubernetes in 2024."))
    }

    @Test
    fun `soft skills and practices are not scanned, so ordinary prose passes`() {
        assertTrue(unheld("Took ownership of the rollout and added caching where it paid.").isEmpty())
    }

    @Test
    fun `a mention at either end of the text is still a mention`() {
        assertEquals(listOf("Terraform"), unheld("Terraform"))
        assertEquals(listOf("Terraform"), unheld("Everything was managed in Terraform"))
    }

    @Test
    fun `holding everything leaves nothing to report`() {
        val everything = catalog.map { it.id }.toSet()

        assertTrue(SkillMentions.unheld("Kubernetes, Apache Kafka, Terraform", catalog, everything).isEmpty())
    }

    @Test
    fun `an empty catalog and empty text both report nothing`() {
        assertTrue(SkillMentions.unheld("", catalog, held).isEmpty())
        assertTrue(SkillMentions.unheld("Kubernetes everywhere", emptyList(), held).isEmpty())
    }
}
