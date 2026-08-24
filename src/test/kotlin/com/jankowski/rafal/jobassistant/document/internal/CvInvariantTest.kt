package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The single most valuable test in the codebase: it is what turns "the prompt says don't lie"
 * into "the build fails if it lies". Pure logic, so it runs in the fast tier with no browser and
 * no database.
 */
class CvInvariantTest {

    private val kotlin = CanonicalSkill(1, "Kotlin", SkillCategory.LANGUAGE)
    private val kubernetes = CanonicalSkill(2, "Kubernetes", SkillCategory.DEVOPS)
    private val kafka = CanonicalSkill(3, "Apache Kafka", SkillCategory.MESSAGING)
    private val springBoot = CanonicalSkill(4, "Spring Boot", SkillCategory.FRAMEWORK)
    private val teamwork = CanonicalSkill(5, "Teamwork", SkillCategory.SOFT)
    private val caching = CanonicalSkill(6, "Caching", SkillCategory.PRACTICE)
    private val go = CanonicalSkill(7, "Go", SkillCategory.LANGUAGE)

    private val catalog = listOf(kotlin, kubernetes, kafka, springBoot, teamwork, caching, go)
    private val held = setOf(kotlin.id, springBoot.id)

    private fun check(text: String) = CvInvariant.violations(text, catalog, held)

    @Test
    fun `a CV using only held skills passes`() {
        assertTrue(check("Built payment services in Kotlin on Spring Boot.").isEmpty())
    }

    @Test
    fun `a fabricated technology is caught`() {
        assertEquals(listOf("Kubernetes"), check("Deployed the platform to Kubernetes."))
    }

    @Test
    fun `a multi-word skill name is caught`() {
        assertEquals(listOf("Apache Kafka"), check("Streamed events through Apache Kafka."))
    }

    @Test
    fun `punctuation and casing do not hide a fabrication`() {
        assertEquals(listOf("Kubernetes"), check("Ran workloads on KUBERNETES, at scale."))
        assertEquals(listOf("Apache Kafka"), check("Used apache-kafka for ingest."))
    }

    @Test
    fun `every fabrication is reported, sorted and deduplicated`() {
        val violations = check("Kubernetes and Apache Kafka and Kubernetes again.")

        assertEquals(listOf("Apache Kafka", "Kubernetes"), violations)
    }

    @Test
    fun `a substring of a longer word is not a match`() {
        assertTrue(check("The process felt kafkaesque and overly bureaucratic.").isEmpty())
    }

    @Test
    fun `held skills are never reported even when mentioned repeatedly`() {
        assertTrue(check("Kotlin, Kotlin and more Kotlin. Spring Boot too.").isEmpty())
    }

    @Test
    fun `soft skills and practices are not scanned, so ordinary English passes`() {
        assertTrue(check("Strong teamwork, and I introduced caching to cut latency.").isEmpty())
    }

    @Test
    fun `short ambiguous names are not scanned`() {
        assertTrue(check("I go to the office on Tuesdays.").isEmpty())
        assertTrue(check("We had to go fast.").isEmpty())
    }

    @Test
    fun `empty text has nothing to report`() {
        assertTrue(check("").isEmpty())
    }

    @Test
    fun `a fabrication inside HTML markup is still caught`() {
        assertEquals(listOf("Kubernetes"), check("<li class=\"bullet\">Scaled <b>Kubernetes</b> clusters</li>"))
    }

    @Test
    fun `holding everything means nothing can be violated`() {
        val everything = catalog.map { it.id }.toSet()

        assertTrue(CvInvariant.violations("Kubernetes Kafka Go", catalog, everything).isEmpty())
    }
}
