package com.jankowski.rafal.jobassistant.career.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.catalog.SkillCoverage
import com.jankowski.rafal.jobassistant.catalog.SkillSuggestion
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTerm
import com.jankowski.rafal.jobassistant.catalog.UnmatchedTermStatus
import com.jankowski.rafal.jobassistant.market.DemandEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CareerPlanSelectionTest {

    private val kafka = CanonicalSkill(10L, "Kafka", SkillCategory.MESSAGING)
    private val docker = CanonicalSkill(11L, "Docker", SkillCategory.DEVOPS)
    private val rust = CanonicalSkill(12L, "Rust", SkillCategory.LANGUAGE)

    private val catalog = object : SkillCatalog {
        private val byName = mapOf("kafka" to kafka, "docker" to docker, "rust" to rust)
        override fun findById(id: Long) = byName.values.firstOrNull { it.id == id }
        override fun findAllById(ids: Collection<Long>) = byName.values.filter { it.id in ids }
        override fun findAll() = byName.values.toList()
        override fun resolve(term: String) = byName[term.trim().lowercase()]
        override fun resolveAll(terms: Collection<String>) = terms.associateWith { resolve(it) }
        override fun suggest(term: String, limit: Int) = emptyList<SkillSuggestion>()
        override fun suggestAll(terms: Collection<String>, limit: Int) = emptyMap<String, List<SkillSuggestion>>()
        override fun coverageFor(heldSkillIds: Set<Long>) = SkillCoverage.EMPTY
        override fun recordUnmatched(term: String) = Unit
        override fun recordUnmatchedFromMarket(mentions: Map<String, Int>) = Unit
        override fun pendingUnmatchedTerms(limit: Int) = emptyList<UnmatchedTerm>()
        override fun allPendingUnmatchedTerms() = emptyList<UnmatchedTerm>()
        override fun approveUnmatchedTerm(termId: Long, skillId: Long) = kafka
        override fun rejectUnmatchedTerm(termId: Long) = Unit
        override fun createSkill(name: String, category: SkillCategory, aliases: Collection<String>) = kafka
        override fun updateSkill(id: Long, name: String, category: SkillCategory) = kafka
        override fun deleteSkill(id: Long) = Unit
    }

    @Test
    fun `drops unbacked or unknown topics, deduplicates, and computes modal seniority from evidence`() {
        val evidence = listOf(
            DemandEntry(
                skillId = kafka.id,
                skillName = kafka.name,
                category = kafka.category,
                offers = 18,
                requiredOffers = 15,
                status = CoverageStatus.MISSING,
                seniorityMix = mapOf(SeniorityLevel.SENIOR to 9, SeniorityLevel.REGULAR to 5),
            ),
            DemandEntry(
                skillId = docker.id,
                skillName = docker.name,
                category = docker.category,
                offers = 10,
                requiredOffers = 8,
                status = CoverageStatus.PARTIAL,
                seniorityMix = emptyMap(),
            ),
        )

        val authored = AuthoredCareerPlan(
            topics = listOf(
                AuthoredTopic(skill = "Kafka", priority = 1, why = "High demand", practiceProject = "Build consumer", effortEstimate = "2 weeks"),
                AuthoredTopic(skill = "HallucinatedTech", priority = 2, why = "Invented", practiceProject = null, effortEstimate = null),
                AuthoredTopic(skill = "Rust", priority = 3, why = "Not in evidence", practiceProject = null, effortEstimate = null),
                AuthoredTopic(skill = "kafka", priority = 4, why = "Duplicate", practiceProject = null, effortEstimate = null),
                AuthoredTopic(skill = "Docker", priority = 5, why = "Containers", practiceProject = "Compose stack", effortEstimate = "1 week"),
            ),
        )

        val selection = CareerPlanSelection.from(
            authored = authored,
            evidence = evidence,
            catalog = catalog,
            fallbackLevel = SeniorityLevel.REGULAR,
        )

        assertThat(selection.droppedTopicCount).isEqualTo(3)
        assertThat(selection.topics).hasSize(2)
        assertThat(selection.topics[0].skillId).isEqualTo(kafka.id)
        assertThat(selection.topics[0].targetLevel).isEqualTo(SeniorityLevel.SENIOR)
        assertThat(selection.topics[1].skillId).isEqualTo(docker.id)
        assertThat(selection.topics[1].targetLevel).isEqualTo(SeniorityLevel.REGULAR)
    }
}
