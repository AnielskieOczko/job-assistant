package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.market.MarketSalary
import com.jankowski.rafal.jobassistant.market.MarketSkillLevel
import com.jankowski.rafal.jobassistant.market.SalaryGroup
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

/**
 * Reads the corpus for the dashboard.
 *
 * Separate from [MarketOfferRepository] on the same principle that separates [MarketDemand] from
 * [MarketOfferService][com.jankowski.rafal.jobassistant.market.MarketOfferService]: writing the
 * corpus and describing it are different jobs, and the write side is an upsert with a `jsonb`
 * payload while this side is aggregation that never touches a payload at all.
 *
 * Two rules hold across every query here:
 *
 * - **In scope means the offer also asks for a scope skill**, and **currently valid** means its
 *   stated `valid_to` has not passed. The corpus accumulates and is never pruned, so without the
 *   second predicate a median silently drifts from "now" to "since we started looking".
 * - **Percentiles are `percentile_disc`, never `percentile_cont`.** The discrete form returns a
 *   figure an employer actually stated; interpolation would invent one between two of them, which
 *   is the same thing as reporting a midpoint no offer carried.
 */
@Repository
internal class MarketStatisticsRepository(private val jdbc: JdbcClient) {

    /**
     * Offers in scope and currently valid, plus everything needed to say what that excluded.
     *
     * Callers must not pass an empty scope: `in ()` is not valid SQL, and an empty scope means no
     * measure rather than the whole corpus -- the same rule [MarketDemandService] enforces.
     */
    fun scopeCounts(scopeSkillIds: Collection<Long>): ScopeCounts {
        require(scopeSkillIds.isNotEmpty()) { "an in-scope query needs at least one scope skill" }

        return jdbc.sql(
            """
            with scoped as (
                select distinct market_offer_id as id from market_offer_skill
                where canonical_skill_id in (:scopeSkillIds)
            ), offers as (
                select o.id, ($VALID_NOW) as valid_now from market_offer o join scoped s on s.id = o.id
            )
            select (select count(*) from offers where valid_now)                      as in_scope,
                   (select count(*) from offers where not valid_now)                  as expired,
                   (select count(*) from market_offer)                                as corpus,
                   (select min(first_seen_at) from market_offer)                      as first_seen_at,
                   (select max(last_seen_at) from market_offer)                       as last_seen_at,
                   (select count(*) from market_offer_skill mos
                      join offers f on f.id = mos.market_offer_id and f.valid_now)    as mentions,
                   (select count(*) from market_offer_skill mos
                      join offers f on f.id = mos.market_offer_id and f.valid_now
                      where mos.canonical_skill_id is null)                           as unresolved
            """
        )
            .param("scopeSkillIds", scopeSkillIds)
            .query { rs, _ ->
                ScopeCounts(
                    offersInScope = rs.getInt("in_scope"),
                    expiredInScope = rs.getInt("expired"),
                    corpusOffers = rs.getInt("corpus"),
                    firstSeenAt = rs.getTimestamp("first_seen_at")?.toInstant(),
                    lastSeenAt = rs.getTimestamp("last_seen_at")?.toInstant(),
                    skillMentions = rs.getInt("mentions"),
                    unresolvedMentions = rs.getInt("unresolved"),
                )
            }
            .single()
    }

    /** Boards the corpus was drawn from, so every statistic can name its source. */
    fun sources(): List<String> = jdbc.sql("select distinct source from market_offer order by source")
        .query { rs, _ -> rs.getString("source") }
        .list()

    /**
     * Salary quartiles per comparable slice.
     *
     * Grouped by employment type *and* currency *and* period rather than employment type alone.
     * The corpus is entirely PLN/Month today, which is exactly why the grouping has to be written
     * now: the first EUR offer would otherwise be pooled into a PLN median with no error anywhere.
     */
    fun salaryGroups(scopeSkillIds: Collection<Long>): List<SalaryGroup> {
        require(scopeSkillIds.isNotEmpty()) { "an in-scope query needs at least one scope skill" }

        return jdbc.sql(
            """
            select o.employment_type, o.salary_currency, o.salary_period,
                   count(*) as offers,
                   percentile_disc(0.5) within group (order by o.salary_from)  as median_from,
                   percentile_disc(0.5) within group (order by o.salary_to)    as median_to,
                   percentile_disc(0.25) within group (order by o.salary_from) as p25_from,
                   percentile_disc(0.75) within group (order by o.salary_to)   as p75_to
            from market_offer o
            where $VALID_NOW
              and (o.salary_from is not null or o.salary_to is not null)
              and o.id in (select market_offer_id from market_offer_skill where canonical_skill_id in (:scopeSkillIds))
            group by 1, 2, 3
            order by offers desc, 1
            """
        )
            .param("scopeSkillIds", scopeSkillIds)
            .query { rs, _ ->
                SalaryGroup(
                    employmentType = rs.getString("employment_type"),
                    currency = rs.getString("salary_currency"),
                    period = rs.getString("salary_period"),
                    offers = rs.getInt("offers"),
                    medianFrom = rs.getBigDecimal("median_from"),
                    medianTo = rs.getBigDecimal("median_to"),
                    p25From = rs.getBigDecimal("p25_from"),
                    p75To = rs.getBigDecimal("p75_to"),
                )
            }
            .list()
    }

    /** In-scope offers stating any salary at all -- the coverage numerator for [salaryGroups]. */
    fun offersStatingSalary(scopeSkillIds: Collection<Long>): Int {
        require(scopeSkillIds.isNotEmpty()) { "an in-scope query needs at least one scope skill" }

        return jdbc.sql(
            """
            select count(*) from market_offer o
            where $VALID_NOW
              and (o.salary_from is not null or o.salary_to is not null)
              and o.id in (select market_offer_id from market_offer_skill where canonical_skill_id in (:scopeSkillIds))
            """
        )
            .param("scopeSkillIds", scopeSkillIds)
            .query(Int::class.java)
            .single()
    }

    /**
     * Per-skill demand inside the scope, counting offers rather than mentions.
     *
     * `market_offer_skill` is keyed `(market_offer_id, skill_name)`, so one row is one employer
     * asking once -- but two spellings of one skill resolve to the same canonical id, so the count
     * is over distinct offers rather than rows.
     */
    fun demandTotals(scopeSkillIds: Collection<Long>): List<DemandTotal> {
        require(scopeSkillIds.isNotEmpty()) { "an in-scope query needs at least one scope skill" }

        return jdbc.sql(
            """
            select mos.canonical_skill_id as skill_id,
                   count(distinct mos.market_offer_id)                                            as offers,
                   count(distinct mos.market_offer_id) filter (where mos.level <> 'NICE_TO_HAVE') as required_offers
            from market_offer_skill mos
            join market_offer o on o.id = mos.market_offer_id
            where mos.canonical_skill_id is not null
              and $VALID_NOW
              and o.id in (select market_offer_id from market_offer_skill where canonical_skill_id in (:scopeSkillIds))
            group by mos.canonical_skill_id
            """
        )
            .param("scopeSkillIds", scopeSkillIds)
            .query { rs, _ ->
                DemandTotal(
                    skillId = rs.getLong("skill_id"),
                    offers = rs.getInt("offers"),
                    requiredOffers = rs.getInt("required_offers"),
                )
            }
            .list()
    }

    /**
     * The level mix, as a second query rather than a second grouping of the first.
     *
     * The totals cannot be recovered by summing these. Two spellings of one skill can resolve to
     * the same canonical id on the *same* offer at different stated levels -- `React` Advanced and
     * `React.js` Basic -- so a sum over levels would count that employer twice while
     * [demandTotals]' `count(distinct market_offer_id)` counts it once. The totals are the number
     * that gets ranked and rendered; this mix is descriptive.
     */
    fun demandLevels(scopeSkillIds: Collection<Long>): List<DemandLevelRow> {
        require(scopeSkillIds.isNotEmpty()) { "an in-scope query needs at least one scope skill" }

        return jdbc.sql(
            """
            select mos.canonical_skill_id as skill_id, mos.level,
                   count(distinct mos.market_offer_id) as offers
            from market_offer_skill mos
            join market_offer o on o.id = mos.market_offer_id
            where mos.canonical_skill_id is not null
              and $VALID_NOW
              and o.id in (select market_offer_id from market_offer_skill where canonical_skill_id in (:scopeSkillIds))
            group by mos.canonical_skill_id, mos.level
            """
        )
            .param("scopeSkillIds", scopeSkillIds)
            .query { rs, _ ->
                DemandLevelRow(
                    skillId = rs.getLong("skill_id"),
                    level = MarketSkillLevel.valueOf(rs.getString("level")),
                    offers = rs.getInt("offers"),
                )
            }
            .list()
    }

    /**
     * A salary band per skill, within one employment type.
     *
     * Restricted to a single [employmentType] because a band pooled across contract types is the
     * error [salaryGroups] exists to avoid, one level down. The caller passes the group it is
     * rendering, and the band carries that label back so the number is never shown unattributed.
     */
    fun skillSalaryBands(
        scopeSkillIds: Collection<Long>,
        employmentType: String,
        minOffers: Int,
    ): Map<Long, SkillSalaryRow> {
        require(scopeSkillIds.isNotEmpty()) { "an in-scope query needs at least one scope skill" }

        return jdbc.sql(
            """
            select mos.canonical_skill_id as skill_id,
                   count(distinct o.id)                                        as offers,
                   percentile_disc(0.5) within group (order by o.salary_from)  as median_from,
                   percentile_disc(0.5) within group (order by o.salary_to)    as median_to,
                   max(o.salary_currency)                                      as currency,
                   max(o.salary_period)                                        as period
            from market_offer_skill mos
            join market_offer o on o.id = mos.market_offer_id
            where mos.canonical_skill_id is not null
              and o.employment_type = :employmentType
              and (o.salary_from is not null or o.salary_to is not null)
              and $VALID_NOW
              and o.id in (select market_offer_id from market_offer_skill where canonical_skill_id in (:scopeSkillIds))
            group by mos.canonical_skill_id
            having count(distinct o.id) >= :minOffers
            """
        )
            .param("scopeSkillIds", scopeSkillIds)
            .param("employmentType", employmentType)
            .param("minOffers", minOffers)
            .query { rs, _ ->
                rs.getLong("skill_id") to SkillSalaryRow(
                    offers = rs.getInt("offers"),
                    medianFrom = rs.getBigDecimal("median_from"),
                    medianTo = rs.getBigDecimal("median_to"),
                    currency = rs.getString("currency"),
                    period = rs.getString("period"),
                )
            }
            .list()
            .toMap()
    }

    /**
     * A page of offers with their per-offer skill counts.
     *
     * The counts are three numbers rather than a ratio, because the third one is the point:
     * resolved, of which held, plus however many terms the catalog could not place at all. An
     * offer covered "6 of 6" with three unresolved terms on it is not a covered offer.
     */
    fun offerPage(
        scopeSkillIds: Collection<Long>,
        heldSkillIds: Set<Long>,
        inScopeOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<OfferRow> {
        // `in ()` is invalid SQL and an empty held set is the normal case for a fresh profile, so
        // the predicate degenerates to a literal false rather than being built with no operands.
        val heldPredicate = if (heldSkillIds.isEmpty()) "false" else "mos.canonical_skill_id in (:heldSkillIds)"
        val scopePredicate = if (inScopeOnly) {
            "and o.id in (select market_offer_id from market_offer_skill where canonical_skill_id in (:scopeSkillIds))"
        } else {
            ""
        }

        var spec = jdbc.sql(
            """
            select o.id, o.source, o.title, o.company, o.url, o.experience_level,
                   o.is_remote, o.is_hybrid, o.locations,
                   o.salary_from, o.salary_to, o.salary_currency, o.salary_period, o.employment_type,
                   o.valid_to, o.last_seen_at,
                   count(*) filter (where mos.canonical_skill_id is not null) as resolved,
                   count(*) filter (where $heldPredicate)                     as covered,
                   -- The market_offer_id guard is the left join's doing: an offer listing no
                   -- skills at all still produces one row here, with every mos column null, and
                   -- without it that row would report itself as an unresolved term.
                   count(*) filter (where mos.market_offer_id is not null
                                      and mos.canonical_skill_id is null)     as unresolved
            from market_offer o
            left join market_offer_skill mos on mos.market_offer_id = o.id
            where $VALID_NOW $scopePredicate
            group by o.id
            order by o.last_seen_at desc, o.id
            limit :limit offset :offset
            """
        )
            .param("limit", limit)
            .param("offset", offset)

        if (inScopeOnly) spec = spec.param("scopeSkillIds", scopeSkillIds)
        if (heldSkillIds.isNotEmpty()) spec = spec.param("heldSkillIds", heldSkillIds)

        return spec.query { rs, _ ->
            OfferRow(
                id = rs.getLong("id"),
                source = rs.getString("source"),
                title = rs.getString("title"),
                company = rs.getString("company"),
                url = rs.getString("url"),
                experienceLevel = rs.getString("experience_level"),
                isRemote = rs.getBoolean("is_remote"),
                isHybrid = rs.getBoolean("is_hybrid"),
                locations = (rs.getArray("locations")?.array as? Array<*>)?.map { it.toString() } ?: emptyList(),
                salary = salaryOf(
                    rs.getBigDecimal("salary_from"),
                    rs.getBigDecimal("salary_to"),
                    rs.getString("salary_currency"),
                    rs.getString("salary_period"),
                    rs.getString("employment_type"),
                ),
                validTo = rs.getTimestamp("valid_to")?.toInstant(),
                lastSeenAt = rs.getTimestamp("last_seen_at").toInstant(),
                skillsResolved = rs.getInt("resolved"),
                skillsCovered = rs.getInt("covered"),
                skillsUnresolved = rs.getInt("unresolved"),
            )
        }.list()
    }

    /** How many offers [offerPage] is a page *of*, so a page can never read as the whole corpus. */
    fun countOffers(scopeSkillIds: Collection<Long>, inScopeOnly: Boolean): Int {
        val scopePredicate = if (inScopeOnly) {
            "and o.id in (select market_offer_id from market_offer_skill where canonical_skill_id in (:scopeSkillIds))"
        } else {
            ""
        }

        var spec = jdbc.sql("select count(*) from market_offer o where $VALID_NOW $scopePredicate")
        if (inScopeOnly) spec = spec.param("scopeSkillIds", scopeSkillIds)
        return spec.query(Int::class.java).single()
    }

    private fun salaryOf(
        from: BigDecimal?,
        to: BigDecimal?,
        currency: String?,
        period: String?,
        employmentType: String?,
    ): MarketSalary? =
        if (from == null && to == null && currency == null) null
        else MarketSalary(from, to, currency, period, employmentType)

    private companion object {
        /**
         * Currently valid as the *source* states it, not as our polling implies.
         *
         * `valid_to is null` counts as valid: the column is nullable in the schema because a date
         * that would not parse must not lose the offer, and dropping those rows would silently
         * shrink every denominator on the page.
         */
        const val VALID_NOW = "(o.valid_to is null or o.valid_to > now())"
    }
}

internal data class ScopeCounts(
    val offersInScope: Int,
    val expiredInScope: Int,
    val corpusOffers: Int,
    val firstSeenAt: Instant?,
    val lastSeenAt: Instant?,
    val skillMentions: Int,
    val unresolvedMentions: Int,
)

/** Offers asking for one skill, counted once per employer however many spellings they used. */
internal data class DemandTotal(val skillId: Long, val offers: Int, val requiredOffers: Int)

/** One (skill, level) cell. Descriptive only -- these do not sum to [DemandTotal.offers]. */
internal data class DemandLevelRow(val skillId: Long, val level: MarketSkillLevel, val offers: Int)

internal data class SkillSalaryRow(
    val offers: Int,
    val medianFrom: BigDecimal?,
    val medianTo: BigDecimal?,
    val currency: String?,
    val period: String?,
)

internal data class OfferRow(
    val id: Long,
    val source: String,
    val title: String,
    val company: String?,
    val url: String?,
    val experienceLevel: String?,
    val isRemote: Boolean,
    val isHybrid: Boolean,
    val locations: List<String>,
    val salary: MarketSalary?,
    val validTo: Instant?,
    val lastSeenAt: Instant,
    val skillsResolved: Int,
    val skillsCovered: Int,
    val skillsUnresolved: Int,
)
