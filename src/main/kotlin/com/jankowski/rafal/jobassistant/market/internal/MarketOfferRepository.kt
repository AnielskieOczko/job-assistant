package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.CorpusSummary
import com.jankowski.rafal.jobassistant.market.MarketOfferSkill
import com.jankowski.rafal.jobassistant.market.MarketSalary
import com.jankowski.rafal.jobassistant.market.MarketSkillLevel
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Whether an upsert created a row or refreshed one already in the corpus. */
internal enum class UpsertOutcome { INSERTED, UPDATED }

/**
 * Writes the corpus with [JdbcClient] rather than Spring Data JDBC.
 *
 * The row carries a `jsonb` column and a `text[]`, and the skills table has a composite key -- all
 * three sit badly with the mapping, and the write is a single `on conflict` upsert that an
 * aggregate save cannot express anyway.
 */
@Repository
internal class MarketOfferRepository(private val jdbc: JdbcClient) {

    /**
     * Inserts an offer or refreshes the one already stored under the same key.
     *
     * `first_seen_at` is deliberately never overwritten: it is half of the window every statistic
     * drawn from this corpus has to declare, and an offer re-seen on every poll would otherwise
     * report itself as new forever.
     */
    fun upsert(
        offer: SolidJobsOffer,
        source: String,
        payload: String,
        locationsJson: String,
        seenAt: Instant,
    ): Pair<Long, UpsertOutcome> {
        val row = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, company, division, category, sub_category,
                                      url, description, experience_level, contract_time, is_remote, is_hybrid, locations,
                                      salary_from, salary_to, salary_currency, salary_period, employment_type,
                                      valid_from, valid_to, source_updated_at, first_seen_at, last_seen_at, payload)
            values (:source, :offerKey, :title, :company, :division, :category, :subCategory,
                    :url, :description, :experienceLevel, :contractTime, :isRemote, :isHybrid,
                    array(select jsonb_array_elements_text(cast(:locations as jsonb))),
                    :salaryFrom, :salaryTo, :salaryCurrency, :salaryPeriod, :employmentType,
                    :validFrom, :validTo, :sourceUpdatedAt, :seenAt, :seenAt, cast(:payload as jsonb))
            on conflict (source, offer_key) do update set
                title = excluded.title, company = excluded.company, division = excluded.division,
                category = excluded.category, sub_category = excluded.sub_category, url = excluded.url,
                -- Never overwritten with a null: a re-poll that stops carrying the prose would
                -- otherwise silently un-promotable every offer already in the corpus.
                description = coalesce(excluded.description, market_offer.description),
                experience_level = excluded.experience_level, contract_time = excluded.contract_time,
                is_remote = excluded.is_remote, is_hybrid = excluded.is_hybrid,
                locations = excluded.locations, salary_from = excluded.salary_from,
                salary_to = excluded.salary_to, salary_currency = excluded.salary_currency,
                salary_period = excluded.salary_period, employment_type = excluded.employment_type,
                valid_from = excluded.valid_from, valid_to = excluded.valid_to,
                source_updated_at = excluded.source_updated_at, last_seen_at = excluded.last_seen_at,
                payload = excluded.payload
            returning id, (xmax = 0) as inserted
            """
        )
            .param("source", source)
            .param("offerKey", offer.jobOfferKey)
            .param("title", offer.title)
            .param("company", offer.company)
            .param("division", offer.division)
            .param("category", offer.category)
            .param("subCategory", offer.subCategory)
            .param("url", offer.url)
            .param("description", offer.description)
            .param("experienceLevel", offer.experienceLevel)
            .param("contractTime", offer.contractTime)
            .param("isRemote", offer.isRemote)
            .param("isHybrid", offer.isHybrid)
            .param("locations", locationsJson)
            .param("salaryFrom", offer.salary?.from)
            .param("salaryTo", offer.salary?.to)
            .param("salaryCurrency", offer.salary?.currency)
            .param("salaryPeriod", offer.salary?.period)
            .param("employmentType", offer.salary?.employmentType)
            // pgjdbc cannot bind an Instant through raw JDBC - Spring Data JDBC converts one, but
            // JdbcClient hands the value straight to the driver, which asks for an explicit type.
            .param("validFrom", offer.validFrom?.let(::parseTimestamp).atUtc())
            .param("validTo", offer.validTo?.let(::parseTimestamp).atUtc())
            .param("sourceUpdatedAt", offer.updatedAt?.let(::parseTimestamp).atUtc())
            .param("seenAt", seenAt.atUtc())
            .param("payload", payload)
            .query { rs, _ -> rs.getLong("id") to rs.getBoolean("inserted") }
            .single()

        return row.first to if (row.second) UpsertOutcome.INSERTED else UpsertOutcome.UPDATED
    }

    /**
     * Replaces an offer's skill rows.
     *
     * Delete-then-insert rather than a merge: an offer that drops a requirement must stop counting
     * toward that skill's demand, and a diff would be more code for a table with no identity of
     * its own.
     */
    fun replaceSkills(marketOfferId: Long, skills: List<MarketOfferSkill>) {
        jdbc.sql("delete from market_offer_skill where market_offer_id = :id")
            .param("id", marketOfferId)
            .update()

        skills.distinctBy { it.name }.forEach { skill ->
            jdbc.sql(
                """
                insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
                values (:id, :name, :level, :skillId)
                """
            )
                .param("id", marketOfferId)
                .param("name", skill.name)
                .param("level", skill.level.name)
                .param("skillId", skill.canonicalSkillId)
                .update()
        }
    }

    /**
     * How many corpus offers ask for each skill name the catalog could not place.
     *
     * The primary key is (market_offer_id, skill_name), so one row is one offer asking for one
     * term: counting rows counts employers rather than repetitions inside a single listing. Read
     * over the whole corpus rather than one poll, because the counter it feeds is a standing
     * measure of demand, not a record of what the last fetch happened to serve.
     */
    fun unresolvedSkillMentions(): Map<String, Int> = jdbc.sql(
        """
        select skill_name, count(*) as mentions
        from market_offer_skill
        where canonical_skill_id is null
        group by skill_name
        """
    ).query { rs, _ -> rs.getString("skill_name") to rs.getInt("mentions") }
        .list()
        .toMap()

    /**
     * The same count as [unresolvedSkillMentions], restricted to offers that also ask for one of
     * [scopeSkillIds].
     *
     * The subquery picks the offers first and the outer query counts every unresolved term on them,
     * so a term is credited for appearing *alongside* a scope skill rather than for being one. That
     * is the whole point: it is how "Test automation" sinks below terms that show up on the offers
     * this candidate would actually read.
     *
     * Callers must not pass an empty list -- `in ()` is not valid SQL, and an empty scope means no
     * measure rather than the whole corpus.
     */
    fun unresolvedSkillMentionsInOffersWith(scopeSkillIds: Collection<Long>): Map<String, Int> {
        require(scopeSkillIds.isNotEmpty()) { "an in-scope query needs at least one scope skill" }

        return jdbc.sql(
            """
            select skill_name, count(*) as mentions
            from market_offer_skill
            where canonical_skill_id is null
              and market_offer_id in (
                  select distinct market_offer_id
                  from market_offer_skill
                  where canonical_skill_id in (:scopeSkillIds)
              )
            group by skill_name
            """
        )
            .param("scopeSkillIds", scopeSkillIds)
            .query { rs, _ -> rs.getString("skill_name") to rs.getInt("mentions") }
            .list()
            .toMap()
    }

    fun summaries(): List<CorpusSummary> = jdbc.sql(
        """
        select source,
               count(*)                                          as offers,
               count(*) filter (where valid_to is null or valid_to > now()) as currently_valid,
               min(first_seen_at)                                as first_seen_at,
               max(last_seen_at)                                 as last_seen_at
        from market_offer
        group by source
        order by source
        """
    ).query { rs, _ ->
        CorpusSummary(
            source = rs.getString("source"),
            offers = rs.getInt("offers"),
            currentlyValid = rs.getInt("currently_valid"),
            firstSeenAt = rs.getTimestamp("first_seen_at")?.toInstant(),
            lastSeenAt = rs.getTimestamp("last_seen_at")?.toInstant(),
        )
    }.list()

    fun skillsFor(marketOfferId: Long): List<MarketOfferSkill> = jdbc.sql(
        "select skill_name, level, canonical_skill_id from market_offer_skill where market_offer_id = :id order by skill_name"
    )
        .param("id", marketOfferId)
        .query { rs, _ ->
            MarketOfferSkill(
                name = rs.getString("skill_name"),
                level = MarketSkillLevel.valueOf(rs.getString("level")),
                canonicalSkillId = rs.getLong("canonical_skill_id").takeUnless { rs.wasNull() },
            )
        }
        .list()

    /**
     * The one row promotion needs, read as its own query rather than through the dashboard's page.
     *
     * The dashboard never selects `description` - it lists thousands of rows and a posting is
     * kilobytes - so a listing read and a promotion read want genuinely different columns.
     */
    fun findForPromotion(marketOfferId: Long): PromotableOffer? = jdbc.sql(
        """
        select id, source, title, company, url, description, experience_level, contract_time,
               is_remote, is_hybrid, locations, salary_from, salary_to, salary_currency,
               salary_period, employment_type
        from market_offer
        where id = :id
        """
    )
        .param("id", marketOfferId)
        .query { rs, _ ->
            PromotableOffer(
                id = rs.getLong("id"),
                source = rs.getString("source"),
                title = rs.getString("title"),
                company = rs.getString("company"),
                url = rs.getString("url"),
                description = rs.getString("description"),
                experienceLevel = rs.getString("experience_level"),
                contractTime = rs.getString("contract_time"),
                isRemote = rs.getBoolean("is_remote"),
                isHybrid = rs.getBoolean("is_hybrid"),
                locations = (rs.getArray("locations")?.array as? Array<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty(),
                salary = MarketSalary(
                    from = rs.getBigDecimal("salary_from"),
                    to = rs.getBigDecimal("salary_to"),
                    currency = rs.getString("salary_currency"),
                    period = rs.getString("salary_period"),
                    employmentType = rs.getString("employment_type"),
                ),
            )
        }
        .optional()
        .orElse(null)
}

/** A corpus row read for promotion: the listing's facts, plus the prose that may not be there. */
internal data class PromotableOffer(
    val id: Long,
    val source: String,
    val title: String,
    val company: String?,
    val url: String?,
    val description: String?,
    val experienceLevel: String?,
    val contractTime: String?,
    val isRemote: Boolean,
    val isHybrid: Boolean,
    val locations: List<String>,
    val salary: MarketSalary,
)

/**
 * The source stamps offsets (`2026-08-28T10:25:00.308+02:00`), which [Instant.parse] rejects.
 *
 * A date that will not parse must not lose the offer: the field is nullable in the schema and the
 * verbatim value survives in the payload either way.
 */
internal fun parseTimestamp(raw: String): Instant? = runCatching {
    OffsetDateTime.parse(raw).toInstant()
}.getOrElse { runCatching { Instant.parse(raw) }.getOrNull() }

private fun Instant?.atUtc(): OffsetDateTime? = this?.atOffset(ZoneOffset.UTC)
