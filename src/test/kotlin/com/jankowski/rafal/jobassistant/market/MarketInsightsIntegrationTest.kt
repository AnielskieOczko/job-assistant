package com.jankowski.rafal.jobassistant.market

import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * The dashboard's read side over a corpus built one offer at a time.
 *
 * Hand-placed rather than ingested, for the reason `TriageQueueIntegrationTest` gives: every
 * assertion here is about which single rule decided an outcome, and that is unreadable against real
 * data where the scope filter, the validity filter and the coverage lookup all move at once.
 *
 * The scope is the configured default (Java / Kotlin / Spring / Spring Boot), so an offer listing
 * Java is in scope and one listing only Kubernetes is not.
 */
@IntegrationTest
class MarketInsightsIntegrationTest {

    @Autowired lateinit var insights: MarketInsights
    @Autowired lateinit var catalog: SkillCatalog
    @Autowired lateinit var jdbc: JdbcClient

    private var profileId: Long = 0

    @BeforeEach
    fun reset() {
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from unmatched_term").update()
        profileId = jdbc.sql("insert into profile (name) values (:name) returning id")
            .param("name", "Market Insights ${System.nanoTime()}")
            .query { rs, _ -> rs.getLong("id") }
            .single()
        // A profile row on its own reads as absent: ProfileService.current returns null until the
        // details row exists, which is what makes "created a persona" and "filled it in" separate
        // steps. Without this the coverage lookup would quietly see an empty profile and every
        // assertion about MET would fail for the wrong reason.
        jdbc.sql("insert into profile_details (profile_id, full_name) values (:id, 'Test Candidate')")
            .param("id", profileId)
            .update()
    }

    private fun hold(vararg names: String) = names.forEach { name ->
        val skill = requireNotNull(catalog.resolve(name)) { "seed catalog should carry $name" }
        jdbc.sql(
            "insert into profile_skill (canonical_skill_id, profile_id, proficiency) " +
                "values (:skillId, :profileId, 'WORKING')"
        ).param("skillId", skill.id).param("profileId", profileId).update()
    }

    /**
     * One corpus offer. [skills] are catalog names the seed can resolve; [unresolved] are terms it
     * cannot, which is the normal case rather than an error.
     */
    private fun offer(
        skills: List<String> = emptyList(),
        niceToHave: List<String> = emptyList(),
        unresolved: List<String> = emptyList(),
        salaryFrom: Int? = null,
        salaryTo: Int? = null,
        employmentType: String? = null,
        expired: Boolean = false,
    ): Long {
        val validTo = if (expired) "now() - interval '1 day'" else "now() + interval '30 days'"
        val id = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, payload, valid_to,
                                      salary_from, salary_to, salary_currency, salary_period, employment_type)
            values ('test', :key, 'Test Offer', cast('{}' as jsonb), $validTo,
                    :salaryFrom, :salaryTo, :currency, :period, :employmentType)
            returning id
            """
        )
            .param("key", "key-${System.nanoTime()}")
            .param("salaryFrom", salaryFrom)
            .param("salaryTo", salaryTo)
            .param("currency", salaryFrom?.let { "PLN" })
            .param("period", salaryFrom?.let { "Month" })
            .param("employmentType", employmentType)
            .query { rs, _ -> rs.getLong("id") }
            .single()

        fun addSkill(name: String, level: String, canonicalId: Long?) = jdbc.sql(
            """
            insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
            values (:id, :name, :level, :skillId)
            """
        ).param("id", id).param("name", name).param("level", level).param("skillId", canonicalId).update()

        skills.forEach { name ->
            val skill = requireNotNull(catalog.resolve(name)) { "seed catalog should carry $name" }
            addSkill(name, "ADVANCED", skill.id)
        }
        niceToHave.forEach { name ->
            val skill = requireNotNull(catalog.resolve(name)) { "seed catalog should carry $name" }
            addSkill(name, "NICE_TO_HAVE", skill.id)
        }
        unresolved.forEach { addSkill(it, "UNKNOWN", null) }

        return id
    }

    @Test
    fun `the scope excludes expired offers and offers outside it, and says how many it excluded`() {
        offer(skills = listOf("Java"))
        offer(skills = listOf("Java"), expired = true)
        offer(skills = listOf("Kubernetes"))

        val scope = insights.scope()

        assertThat(scope.offersInScope).isEqualTo(1)
        // Named rather than dropped: the corpus is never pruned, so the gap between these two
        // numbers is what stops a median drifting from "now" to "since we started looking".
        assertThat(scope.expiredInScope).isEqualTo(1)
        assertThat(scope.corpusOffers).isEqualTo(3)
        assertThat(scope.scopeSkills).contains("Java")
        assertThat(scope.sources).containsExactly("test")
    }

    @Test
    fun `the scope reports the unresolved mentions that bound every demand claim`() {
        offer(skills = listOf("Java"), unresolved = listOf("Zwinność", "Samodzielność"))

        val scope = insights.scope()

        assertThat(scope.skillMentions).isEqualTo(3)
        assertThat(scope.unresolvedMentions).isEqualTo(2)
    }

    @Test
    fun `demand ranks a skill the profile lacks above one it holds, however loudly the held one is asked for`() {
        hold("Java")
        repeat(6) { offer(skills = listOf("Java")) }
        repeat(2) { offer(skills = listOf("Java", "Kubernetes")) }

        val unmet = insights.demand(profileId, DemandRanking.UNMET)

        // Java is asked for by eight offers and Kubernetes by two, and Kubernetes still leads: a
        // table headed by a skill you already have is a true table that answers nothing.
        assertThat(unmet.entries.first().skillName).isEqualTo("Kubernetes")
        assertThat(unmet.entries.first().status).isEqualTo(CoverageStatus.MISSING)
        assertThat(unmet.unmetSkillsInScope).isEqualTo(1)

        val total = insights.demand(profileId, DemandRanking.TOTAL)
        assertThat(total.entries.first().skillName).isEqualTo("Java")
        assertThat(total.entries.first().offers).isEqualTo(8)
    }

    @Test
    fun `the dashboard renders with no persona behind it, reporting an honest all-MISSING table`() {
        // A profile row with no details reads as absent - the same shape a fresh install has before
        // anyone has filled anything in. The table is still worth serving: every row MISSING is a
        // true statement about a candidate who has declared nothing, not a placeholder.
        val emptyProfile = jdbc.sql("insert into profile (name) values (:name) returning id")
            .param("name", "No Details ${System.nanoTime()}")
            .query { rs, _ -> rs.getLong("id") }
            .single()
        offer(skills = listOf("Java", "Spring"))

        val demand = insights.demand(emptyProfile)

        assertThat(demand.entries).isNotEmpty
        assertThat(demand.entries).allMatch { it.status == CoverageStatus.MISSING }
        assertThat(demand.unmetSkillsInScope).isEqualTo(demand.skillsInScope)
    }

    @Test
    fun `a demand entry carries the held skill that earned a MET, so the verdict can be explained`() {
        // Spring Boot IMPLIES Spring in the seed relation graph, so holding one covers the other.
        hold("Spring Boot")
        offer(skills = listOf("Spring"))

        val spring = insights.demand(profileId).entries.single { it.skillName == "Spring" }

        assertThat(spring.status).isEqualTo(CoverageStatus.MET)
        assertThat(spring.coveredBySkillName).isEqualTo("Spring Boot")
    }

    @Test
    fun `a skill asked for by fewer than five offers gets no salary band at all`() {
        repeat(6) { offer(skills = listOf("Java", "Kubernetes"), salaryFrom = 20000, salaryTo = 25000, employmentType = "B2B") }
        repeat(2) { offer(skills = listOf("Java", "Terraform"), salaryFrom = 30000, salaryTo = 35000, employmentType = "B2B") }

        val entries = insights.demand(profileId).entries.associateBy { it.skillName }

        val kubernetes = requireNotNull(entries["Kubernetes"])
        assertThat(kubernetes.salary).isNotNull
        assertThat(kubernetes.salary?.medianFrom?.toInt()).isEqualTo(20000)
        assertThat(kubernetes.salary?.employmentType).isEqualTo("B2B")

        // Null, not zero. "Fewer than five offers" and "these offers pay nothing" are different
        // statements, and the offers count is right there to say which one this is.
        val terraform = requireNotNull(entries["Terraform"])
        assertThat(terraform.salary).isNull()
        assertThat(terraform.offers).isEqualTo(2)
    }

    @Test
    fun `salary groups never pool employment types`() {
        repeat(3) { offer(skills = listOf("Java"), salaryFrom = 20000, salaryTo = 25000, employmentType = "B2B") }
        offer(skills = listOf("Java"), salaryFrom = 12000, salaryTo = 15000, employmentType = "UoP")

        val salary = insights.salary()

        assertThat(salary.groups).hasSize(2)
        val b2b = salary.groups.single { it.employmentType == "B2B" }
        val uop = salary.groups.single { it.employmentType == "UoP" }
        assertThat(b2b.offers).isEqualTo(3)
        assertThat(b2b.medianFrom?.toInt()).isEqualTo(20000)
        assertThat(uop.offers).isEqualTo(1)

        // Four offers is nowhere near the floor, and the group says so rather than hiding.
        assertThat(b2b.meetsSampleFloor).isFalse()
        assertThat(salary.offersWithSalary).isEqualTo(4)
        assertThat(salary.coverage).isEqualTo(1.0)
    }

    @Test
    fun `an offer whose every resolved skill is held still reports the terms the catalog could not place`() {
        hold("Java")
        offer(skills = listOf("Java"), unresolved = listOf("Zwinność", "Samodzielność"))

        val listed = insights.offers(profileId).entries.single()

        assertThat(listed.skillsResolved).isEqualTo(1)
        assertThat(listed.skillsCovered).isEqualTo(1)
        // The whole point: "covered 1 of 1" alongside two terms nobody placed is not a covered
        // offer. Measured on the real corpus, nine of ten apparently-covered offers looked that way
        // only because their unresolved terms had been dropped.
        assertThat(listed.skillsUnresolved).isEqualTo(2)
    }

    @Test
    fun `nice-to-have is counted separately from what an offer actually requires`() {
        offer(skills = listOf("Java"), niceToHave = listOf("Kubernetes"))

        val kubernetes = insights.demand(profileId).entries.single { it.skillName == "Kubernetes" }

        assertThat(kubernetes.offers).isEqualTo(1)
        assertThat(kubernetes.requiredOffers).isZero()
        assertThat(kubernetes.levelMix[MarketSkillLevel.NICE_TO_HAVE]).isEqualTo(1)
    }

    @Test
    fun `the offer page reports the total it is a page of`() {
        repeat(5) { offer(skills = listOf("Java")) }

        val page = insights.offers(profileId, limit = 2)

        assertThat(page.entries).hasSize(2)
        // A page carrying only its rows lets two of five read as the whole corpus - the same
        // failure shape as an empty denominator reading as success.
        assertThat(page.total).isEqualTo(5)
    }
}
