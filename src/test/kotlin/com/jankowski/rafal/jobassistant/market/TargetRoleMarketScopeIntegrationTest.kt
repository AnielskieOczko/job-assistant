package com.jankowski.rafal.jobassistant.market

import com.jankowski.rafal.jobassistant.catalog.SeniorityLevel
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.internal.ProfileTargetRoles
import com.jankowski.rafal.jobassistant.profile.internal.TargetRoleRequest
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.LocalDate

@IntegrationTest
internal class TargetRoleMarketScopeIntegrationTest {

    @Autowired lateinit var insights: MarketInsights
    @Autowired lateinit var catalog: SkillCatalog
    @Autowired lateinit var profiles: ProfileService
    @Autowired lateinit var targetRoles: ProfileTargetRoles
    @Autowired lateinit var jdbc: JdbcClient

    private var profileId: Long = 0

    @BeforeEach
    fun reset() {
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from profile").update()
        profileId = jdbc.sql("insert into profile (name, is_default) values ('Default', true) returning id")
            .query(Long::class.java)
            .single()

        insertOffer("offer-java", listOf("Java", "PostgreSQL"))
        insertOffer("offer-k8s", listOf("Kubernetes", "Docker"))
    }

    @Test
    fun `demand falls back to configured scope until a target role with skills is set`() {
        val baseline = insights.demand(profileId, DemandRanking.UNMET, 20)
        assertThat(baseline.scope.scopeSkills).contains("Java", "Kotlin", "Spring", "Spring Boot")
        assertThat(baseline.entries.map { it.skillName }).containsExactlyInAnyOrder("Java", "PostgreSQL")

        val kubernetes = catalog.resolve("Kubernetes")!!
        val beforeRevision = profiles.revision(profileId)
        targetRoles.put(
            profileId,
            TargetRoleRequest(
                targetLevel = SeniorityLevel.SENIOR,
                skillIds = listOf(kubernetes.id),
                titleKeywords = listOf("Platform Engineer"),
            ),
        )
        assertThat(profiles.revision(profileId)).isEqualTo(beforeRevision + 1)

        val targeted = insights.demand(profileId, DemandRanking.UNMET, 20)
        assertThat(targeted.scope.scopeSkills).containsExactly("Kubernetes")
        assertThat(targeted.entries.map { it.skillName }).containsExactlyInAnyOrder("Kubernetes", "Docker")

        targetRoles.delete(profileId)
        val afterDelete = insights.demand(profileId, DemandRanking.UNMET, 20)
        assertThat(afterDelete).isEqualTo(baseline)
    }

    private fun insertOffer(key: String, skillNames: List<String>) {
        val id = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, payload, valid_to)
            values ('solid.jobs', :key, 'Engineer', cast('{}' as jsonb), now() + interval '1 day')
            returning id
            """
        )
            .param("key", key)
            .query { rs, _ -> rs.getLong("id") }
            .single()

        skillNames.forEach { name ->
            val skill = catalog.resolve(name)!!
            jdbc.sql(
                """
                insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
                values (:offerId, :name, 'ADVANCED', :skillId)
                """
            )
                .param("offerId", id)
                .param("name", name)
                .param("skillId", skill.id)
                .update()
        }
    }
}
