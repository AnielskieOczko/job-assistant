package com.jankowski.rafal.jobassistant.market

import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import java.math.BigDecimal
import java.time.Instant

/**
 * What the corpus says about this job hunt, for the dashboard.
 *
 * Separate from [MarketOfferService] (which pulls offers in) and from [MarketDemand] (which ranks
 * the review queue) because it is a third job: describing the slice of the corpus the candidate is
 * actually in, and comparing it against the profile. Nothing here reaches a model and nothing here
 * is stored -- coverage is computed on read, because a stored comparison goes stale the moment the
 * profile is edited and the computation costs microseconds.
 *
 * Every method returns its own denominators. That is not politeness: a median over eleven offers
 * and a median over four hundred render identically unless the count travels with the number, and
 * the whole point of ingesting a corpus was to stop guessing. See
 * `docs/research/13-offer-market-dashboard.md` decision 3, and issue #47 for the four decisions
 * this interface was shaped by.
 */
interface MarketInsights {

    /**
     * The scope line: what population every other number on the dashboard is measured over.
     *
     * Rendered above the statistics rather than beneath them. A reader who has not been told the
     * source, the window and the size cannot tell an authoritative number from an accident.
     */
    fun scope(): MarketScopeReport

    /**
     * The salary shape of the scope, one group per (employment type, currency, period).
     *
     * Grouped rather than pooled because 21,800 B2B and 21,800 UoP are different money, and a
     * single median over both would be a number describing nobody.
     */
    fun salary(): SalaryReport

    /**
     * The demand table: what the scope asks for, and what of it the profile covers.
     *
     * Ranked by **unmet** demand by default -- skills the profile does not cover, most-asked first.
     * This is an observed count ("50 offers ask for this and you do not have it"), never a
     * counterfactual ("50 offers you would win"): see issue #47, decision 1, for the measurement
     * that ruled the counterfactual out of v1.
     */
    fun demand(
        profileId: Long? = null,
        ranking: DemandRanking = DemandRanking.UNMET,
        limit: Int = DEFAULT_LIMIT,
    ): DemandReport

    /** A page of the offers behind the numbers, so a row can be opened rather than trusted. */
    fun offers(
        profileId: Long? = null,
        inScopeOnly: Boolean = true,
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0,
    ): MarketOfferPage

    companion object {
        /**
         * Offers in scope before any salary statistic renders as a number.
         *
         * From `docs/research/13-offer-market-dashboard.md` decision 3. The floors live here rather
         * than in the frontend so that "did this clear the bar" is one answer computed once, not a
         * rule reimplemented in TypeScript where nothing tests it.
         */
        const val MIN_OFFERS_FOR_SALARY = 30

        /** Offers asking for one skill before that row shows a salary band of its own. */
        const val MIN_OFFERS_FOR_SKILL_SALARY = 5

        /** Field coverage within scope before that field renders as a number rather than a count. */
        const val MIN_FIELD_COVERAGE = 0.80

        const val DEFAULT_LIMIT = 100
    }
}

/** Which signal orders the demand table. */
enum class DemandRanking {
    /**
     * Skills the profile does not cover first, most-asked first within that.
     *
     * The default, and the dashboard's actual question. A table led by Java -- held, asked for by
     * 165 offers -- is a true table that answers nothing.
     */
    UNMET,

    /** Plain demand, coverage ignored. What the scope asks for, whoever is asking. */
    TOTAL,
}

/**
 * The population every other statistic is measured over.
 *
 * [unresolvedMentions] is here rather than buried in the demand table because it bounds every
 * demand claim the dashboard can make. A quarter of what these offers ask for is vocabulary the
 * catalog cannot place, and a ranking that does not say so implies the catalog saw everything.
 */
data class MarketScopeReport(
    /** Boards the corpus was drawn from. Named on every chart; never "the market". */
    val sources: List<String>,
    /** Configured scope skills the catalog resolved. What "in scope" actually meant. */
    val scopeSkills: List<String>,
    /**
     * Configured scope skills the catalog could **not** resolve, and therefore silently ignored.
     *
     * Returned rather than only logged: a typo in configuration narrows every number on the page,
     * and that is exactly the failure a reader needs to be able to see.
     */
    val unresolvedScopeSkills: List<String>,
    /** Offers matching the scope and currently valid. The denominator for everything else. */
    val offersInScope: Int,
    /** In-scope offers whose stated validity has passed; excluded from every statistic. */
    val expiredInScope: Int,
    /** The whole corpus, in-scope or not. Context for how narrow the scope is. */
    val corpusOffers: Int,
    /** When the corpus was first and last polled. The window every statistic must declare. */
    val firstSeenAt: Instant?,
    val lastSeenAt: Instant?,
    /** Skill mentions on in-scope offers. */
    val skillMentions: Int,
    /** Of those, mentions the catalog could not place. The ceiling on any demand claim. */
    val unresolvedMentions: Int,
) {
    /** Whether the scope is large enough for a salary statistic to render as a number at all. */
    val meetsSalaryFloor: Boolean get() = offersInScope >= MarketInsights.MIN_OFFERS_FOR_SALARY
}

/**
 * Salary across the scope, split so that nothing incomparable is pooled.
 *
 * There is one group per (employment type, currency, period) present. Five contract types appear in
 * the source and the smallest of them has two offers, so the API returns them all with their counts
 * and lets the caller render a residual line -- rather than dropping them, which would make the
 * percentages of a "B2B versus employment" split quietly wrong.
 */
data class SalaryReport(
    val groups: List<SalaryGroup>,
    /** In-scope offers, including any with no salary stated. The coverage denominator. */
    val offersInScope: Int,
    /** In-scope offers stating a salary. Rendered as coverage, never assumed to be all of them. */
    val offersWithSalary: Int,
) {
    /** Share of the scope stating a salary at all, for the coverage caption. */
    val coverage: Double get() = if (offersInScope == 0) 0.0 else offersWithSalary.toDouble() / offersInScope

    val meetsCoverageFloor: Boolean get() = coverage >= MarketInsights.MIN_FIELD_COVERAGE
}

/**
 * One comparable slice of salaries.
 *
 * Quartiles are `percentile_disc`, not `percentile_cont`: the discrete form returns a figure some
 * employer actually stated, where interpolation would invent one between two of them. That is the
 * same instinct as reporting a band rather than a midpoint -- the dashboard reports what was
 * offered, not what the arithmetic mean of two offers would have been.
 */
data class SalaryGroup(
    /** As the source states it: B2B, UoP, UZ, UoD, Staż. Never regrouped without saying so. */
    val employmentType: String?,
    val currency: String?,
    val period: String?,
    val offers: Int,
    /** Low end of the band -- the median of what offers state as their floor. */
    val medianFrom: BigDecimal?,
    /** High end of the band. Varies where [medianFrom] is pinned by the source's bucketing. */
    val medianTo: BigDecimal?,
    val p25From: BigDecimal?,
    val p75To: BigDecimal?,
) {
    /**
     * Whether this group has enough offers to report a band rather than a bare count.
     *
     * A group that fails this still ships, carrying its [offers] count, so the caller can render
     * "salary stated on 2 offers" rather than a greyed-out tile that reads as still loading.
     */
    val meetsSampleFloor: Boolean get() = offers >= MarketInsights.MIN_OFFERS_FOR_SALARY
}

/**
 * The demand table and the scope it was measured in.
 *
 * [scope] is embedded rather than left to a second request, so no caller can render a ranking
 * without the denominators that make it mean anything.
 */
data class DemandReport(
    val entries: List<DemandEntry>,
    val scope: MarketScopeReport,
    /** Distinct catalog skills the scope asks for. The denominator for "showing 100 of 340". */
    val skillsInScope: Int,
    /** Of those, how many the profile does not cover. The size of the actual gap. */
    val unmetSkillsInScope: Int,
    val ranking: DemandRanking,
    val limit: Int,
)

/**
 * One skill the scope asks for.
 *
 * [status] comes from `SkillCoverage`, so a PARTIAL carries the held skill that earned it and the
 * table can say "you have Quarkus" instead of showing an unexplained amber dot.
 */
data class DemandEntry(
    val skillId: Long,
    val skillName: String,
    val category: SkillCategory,
    /** In-scope offers asking for this skill at any level. */
    val offers: Int,
    /** Of those, offers asking for it as something other than nice-to-have. */
    val requiredOffers: Int,
    val status: CoverageStatus,
    /** The held skill accounting for a MET or PARTIAL, so the verdict can be explained. */
    val coveredBySkillId: Long? = null,
    val coveredBySkillName: String? = null,
    /** Offers per stated level, so "asked at Advanced in 60% of them" is renderable. */
    val levelMix: Map<MarketSkillLevel, Int> = emptyMap(),
    /**
     * The salary band of the offers asking for this skill, or null below the per-skill floor.
     *
     * Null is the honest answer rather than a zero: "fewer than five offers" is a different
     * statement from "these offers pay nothing", and [offers] is right there to say which.
     */
    val salary: SalaryBand? = null,
)

/** A band for one row of the demand table, over a stated employment type. */
data class SalaryBand(
    val offers: Int,
    val medianFrom: BigDecimal?,
    val medianTo: BigDecimal?,
    val currency: String?,
    val period: String?,
    val employmentType: String?,
)

data class MarketOfferPage(
    val entries: List<MarketOfferSummary>,
    /** Offers matching the same filter, so a page cannot read as the whole corpus. */
    val total: Int,
    val limit: Int,
    val offset: Int,
)

/**
 * One offer as the dashboard lists it.
 *
 * [skillsUnresolved] is not decoration. An offer whose every *resolved* skill is held still asks
 * for whatever the catalog could not place, so "covered 6 of 6" alongside "3 unresolved" is the
 * only honest way to render it. Measured on the corpus: of ten in-scope offers with nothing
 * missing, nine had unresolved terms on them.
 */
data class MarketOfferSummary(
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
    /** Skills the offer lists that the catalog placed. */
    val skillsResolved: Int,
    /** Of those, how many the profile covers (held, implied or related). */
    val skillsCovered: Int,
    /** Listed skills the catalog could not place. Neither covered nor missing -- unknown. */
    val skillsUnresolved: Int,
)
