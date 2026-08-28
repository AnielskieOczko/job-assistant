package com.jankowski.rafal.jobassistant.market.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.market.CorpusSummary
import com.jankowski.rafal.jobassistant.market.IngestionReport
import com.jankowski.rafal.jobassistant.market.MarketOfferService
import com.jankowski.rafal.jobassistant.market.MarketOfferSkill
import com.jankowski.rafal.jobassistant.market.MarketSkillLevel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * Pulls each configured source once and upserts what it finds.
 *
 * No model is involved anywhere in this class, and that is the point: the source states salary,
 * skills and experience level as structured data, so there is nothing to extract and nothing to
 * hallucinate. Skill *names* still have to be resolved against the catalog, but resolution is a
 * lookup -- an unplaced term is queued for a human, never invented into a canonical skill.
 */
@Service
internal class MarketIngestion(
    private val client: SolidJobsClient,
    private val repository: MarketOfferRepository,
    private val catalog: SkillCatalog,
    private val properties: MarketProperties,
    private val jsonMapper: JsonMapper,
) : MarketOfferService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun ingest(): IngestionReport {
        val startedAt = Instant.now()
        var pages = 0
        var seen = 0
        var inserted = 0
        var updated = 0
        var mentions = 0
        var resolved = 0
        val unresolvedTerms = mutableSetOf<String>()
        var error: String? = null

        try {
            for (division in properties.solidJobs.divisions) {
                var pageIndex = 0
                var totalPages = 1

                while (pageIndex < totalPages && pageIndex < properties.solidJobs.maxPages) {
                    val page = client.fetchPage(division, pageIndex, properties.solidJobs.pageSize)
                    pages++
                    totalPages = maxOf(page.totalPages, 1)

                    for (offer in page.jobs) {
                        // An offer with no key cannot be deduplicated, and a corpus that duplicates
                        // on every poll would corrupt every count drawn from it.
                        if (offer.jobOfferKey.isBlank()) {
                            log.warn("Skipping solid.jobs offer without a key: {}", offer.title)
                            continue
                        }
                        seen++

                        val payload = jsonMapper.writeValueAsString(offer)
                        val locationsJson = jsonMapper.writeValueAsString(offer.locations)
                        val (id, outcome) =
                            repository.upsert(offer, SOURCE, payload, locationsJson, startedAt)
                        when (outcome) {
                            UpsertOutcome.INSERTED -> inserted++
                            UpsertOutcome.UPDATED -> updated++
                        }

                        val skills = resolveSkills(offer)
                        mentions += skills.size
                        resolved += skills.count { it.canonicalSkillId != null }
                        skills.filter { it.canonicalSkillId == null }.forEach { unresolvedTerms += it.name }
                        repository.replaceSkills(id, skills)
                    }

                    // A page that comes back empty ends the division regardless of totalPages: a
                    // source that reports more pages than it will serve must not loop forever.
                    if (page.jobs.isEmpty()) break
                    pageIndex++
                }
            }

            // Queued once per run rather than per mention, so the market counter measures how many
            // runs saw a term rather than how many rows repeated it.
            if (unresolvedTerms.isNotEmpty()) catalog.recordUnmatchedFromMarket(unresolvedTerms)
        } catch (e: Exception) {
            // A partial corpus is worth keeping -- everything upserted before the failure is
            // already committed and correct. The run is still reported as failed, because a report
            // that hid it would make a truncated poll look like a shrinking market.
            log.error("Market ingestion failed after {} offers", seen, e)
            error = "${e.javaClass.simpleName}: ${e.message}"
        }

        return IngestionReport(
            source = SOURCE,
            startedAt = startedAt,
            finishedAt = Instant.now(),
            pagesFetched = pages,
            offersSeen = seen,
            offersInserted = inserted,
            offersUpdated = updated,
            skillMentions = mentions,
            skillsResolved = resolved,
            distinctUnresolvedTerms = unresolvedTerms.size,
            error = error,
        )
    }

    override fun corpusSummary(): List<CorpusSummary> = repository.summaries()

    /**
     * Maps the source's skill names onto catalog ids.
     *
     * Unresolved is the normal case, not an error: a 500-offer sample carried 900 distinct names
     * against a 210-entry catalog, many of them Polish soft skills. The name is kept either way, so
     * a term approved in review later becomes countable without a re-ingest.
     */
    private fun resolveSkills(offer: SolidJobsOffer): List<MarketOfferSkill> {
        val named = offer.skills.filter { it.name.isNotBlank() }
        if (named.isEmpty()) return emptyList()

        val resolutions = catalog.resolveAll(named.map { it.name })
        return named.map { skill ->
            MarketOfferSkill(
                name = skill.name.trim(),
                level = MarketSkillLevel.parse(skill.level),
                canonicalSkillId = resolutions[skill.name]?.id,
            )
        }
    }

    private companion object {
        const val SOURCE = "solid.jobs"
    }
}
