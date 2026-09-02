package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.catalog.CoverageStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.market.DemandEntry
import com.jankowski.rafal.jobassistant.market.DemandRanking
import com.jankowski.rafal.jobassistant.market.DemandReport
import com.jankowski.rafal.jobassistant.market.MarketInsights
import com.jankowski.rafal.jobassistant.market.MarketOfferPage
import com.jankowski.rafal.jobassistant.market.MarketOfferSummary
import com.jankowski.rafal.jobassistant.market.MarketScopeReport
import com.jankowski.rafal.jobassistant.market.SalaryBand
import com.jankowski.rafal.jobassistant.market.SalaryReport
import com.jankowski.rafal.jobassistant.profile.ProfileCoverage
import org.springframework.stereotype.Service

/**
 * Describes the corpus for the dashboard, comparing it against the profile on every read.
 *
 * Nothing here is stored. A coverage verdict persisted against a market offer would go stale the
 * moment a skill was added to the profile -- the staleness `profile_revision` exists to make
 * visible on analyses -- and expanding the relation graph once for a few hundred offers costs
 * microseconds. Analyses persist because they cost two model calls; that argument does not carry.
 */
@Service
internal class MarketInsightsService(
    private val statistics: MarketStatisticsRepository,
    private val scopeResolver: MarketScopeResolver,
    private val catalog: SkillCatalog,
    private val coverages: ProfileCoverage,
) : MarketInsights {

    override fun scope(): MarketScopeReport = scopeReport(scopeResolver.resolve())

    override fun salary(): SalaryReport {
        val scope = scopeResolver.resolve()
        if (scope.isEmpty) return SalaryReport(emptyList(), offersInScope = 0, offersWithSalary = 0)

        val counts = statistics.scopeCounts(scope.ids)
        return SalaryReport(
            groups = statistics.salaryGroups(scope.ids),
            offersInScope = counts.offersInScope,
            offersWithSalary = statistics.offersStatingSalary(scope.ids),
        )
    }

    override fun demand(profileId: Long?, ranking: DemandRanking, limit: Int): DemandReport {
        require(limit > 0) { "limit must be positive" }

        val scope = scopeResolver.resolve()
        val report = scopeReport(scope)
        if (scope.isEmpty) {
            return DemandReport(emptyList(), report, skillsInScope = 0, unmetSkillsInScope = 0, ranking, limit)
        }

        val coverage = coverages.of(profileId)
        val totals = statistics.demandTotals(scope.ids)
        val levels = statistics.demandLevels(scope.ids).groupBy { it.skillId }
        // Every skill named on the page in one lookup: the ones the corpus asks for, plus the held
        // skills that account for a MET or PARTIAL. Resolving the second set per row would be a
        // query per entry to render a column that is empty on most of them.
        val coveringIds = totals.mapNotNull { coverage.coveringSkillFor(it.skillId) }
        val skills = catalog.findAllById(totals.map { it.skillId } + coveringIds).associateBy { it.id }

        // Bands are computed for the largest comparable slice only. A band pooled over B2B and UoP
        // would be the error salaryGroups() exists to prevent, one level down; picking the biggest
        // group keeps the sample as large as the honesty rule allows and the band names the type it
        // was measured over, so no figure is ever shown unattributed.
        val dominantType = statistics.salaryGroups(scope.ids).maxByOrNull { it.offers }?.employmentType
        val bands = dominantType?.let {
            statistics.skillSalaryBands(scope.ids, it, MarketInsights.MIN_OFFERS_FOR_SKILL_SALARY)
        } ?: emptyMap()

        val entries = totals.mapNotNull { total ->
            // A skill counted in the corpus but absent from the catalog would be a dangling id;
            // dropping it is the same rule CvSelection.from applies to a model's output, for the
            // same reason - the name has nothing behind it.
            val skill = skills[total.skillId] ?: return@mapNotNull null
            val covering = coverage.coveringSkillFor(skill.id)

            DemandEntry(
                skillId = skill.id,
                skillName = skill.name,
                category = skill.category,
                offers = total.offers,
                requiredOffers = total.requiredOffers,
                status = coverage.statusFor(skill.id),
                coveredBySkillId = covering,
                coveredBySkillName = covering?.let { skills[it]?.name },
                levelMix = levels[skill.id].orEmpty().associate { it.level to it.offers },
                salary = bands[skill.id]?.let { band ->
                    SalaryBand(
                        offers = band.offers,
                        medianFrom = band.medianFrom,
                        medianTo = band.medianTo,
                        currency = band.currency,
                        period = band.period,
                        employmentType = dominantType,
                    )
                },
            )
        }

        return DemandReport(
            entries = entries.sortedWith(comparatorFor(ranking)).take(limit),
            scope = report,
            skillsInScope = entries.size,
            unmetSkillsInScope = entries.count { it.status == CoverageStatus.MISSING },
            ranking = ranking,
            limit = limit,
        )
    }

    override fun offers(profileId: Long?, inScopeOnly: Boolean, limit: Int, offset: Int): MarketOfferPage {
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset must not be negative" }

        val scope = scopeResolver.resolve()
        if (inScopeOnly && scope.isEmpty) return MarketOfferPage(emptyList(), total = 0, limit, offset)

        // The covered count is over held-or-reachable skills, not held ones, so an offer asking for
        // Spring against a profile holding Spring Boot reads as covered here exactly as it does in
        // the demand table. Passing only the directly-held set would make the two disagree.
        val coverage = coverages.of(profileId)
        val reachable = coverage.held + coverage.impliedCovered + coverage.relatedCovered

        val rows = statistics.offerPage(scope.ids, reachable, inScopeOnly, limit, offset)
        return MarketOfferPage(
            entries = rows.map {
                MarketOfferSummary(
                    id = it.id,
                    source = it.source,
                    title = it.title,
                    company = it.company,
                    url = it.url,
                    experienceLevel = it.experienceLevel,
                    isRemote = it.isRemote,
                    isHybrid = it.isHybrid,
                    locations = it.locations,
                    salary = it.salary,
                    validTo = it.validTo,
                    lastSeenAt = it.lastSeenAt,
                    skillsResolved = it.skillsResolved,
                    skillsCovered = it.skillsCovered,
                    skillsUnresolved = it.skillsUnresolved,
                )
            },
            total = statistics.countOffers(scope.ids, inScopeOnly),
            limit = limit,
            offset = offset,
        )
    }

    private fun scopeReport(scope: ResolvedScope): MarketScopeReport {
        val counts = if (scope.isEmpty) {
            ScopeCounts(0, 0, 0, null, null, 0, 0)
        } else {
            statistics.scopeCounts(scope.ids)
        }

        return MarketScopeReport(
            sources = statistics.sources(),
            scopeSkills = scope.names,
            unresolvedScopeSkills = scope.unresolvedNames,
            offersInScope = counts.offersInScope,
            expiredInScope = counts.expiredInScope,
            corpusOffers = counts.corpusOffers,
            firstSeenAt = counts.firstSeenAt,
            lastSeenAt = counts.lastSeenAt,
            skillMentions = counts.skillMentions,
            unresolvedMentions = counts.unresolvedMentions,
        )
    }

    /**
     * What the table leads with.
     *
     * [DemandRanking.UNMET] leads with what the candidate lacks, in the order [CoverageStatus]
     * declares once for every surface that ranks this way. The final tie-break on the name is this
     * comparator's own job and is not decoration: without a total order two skills with equal
     * counts could swap between requests, and a page boundary would show one twice and the other
     * never.
     */
    private fun comparatorFor(ranking: DemandRanking): Comparator<DemandEntry> = when (ranking) {
        DemandRanking.UNMET -> compareBy<DemandEntry, CoverageStatus>(CoverageStatus.UNMET_FIRST) { it.status }
            .thenByDescending { it.offers }
            .thenBy { it.skillName }

        DemandRanking.TOTAL -> compareByDescending<DemandEntry> { it.offers }
            .thenBy { it.skillName }
    }
}
