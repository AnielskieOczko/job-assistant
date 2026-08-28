package com.jankowski.rafal.jobassistant.triage

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillNormalizer
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * The ranked, filtered review queue over a corpus built to exercise each signal separately.
 *
 * The fixtures are deliberately small and hand-placed: the point of every assertion here is which
 * of three counters decided an ordering, and that is unreadable against ingested data where all
 * three move at once.
 */
@IntegrationTest
class TriageQueueIntegrationTest {

    @Autowired lateinit var triage: TriageService
    @Autowired lateinit var catalog: SkillCatalog
    @Autowired lateinit var jdbc: JdbcClient

    private val suffix = System.nanoTime()

    @BeforeEach
    fun clearCorpusAndQueue() {
        jdbc.sql("delete from market_offer").update()
        jdbc.sql("delete from unmatched_term").update()
    }

    /** An offer asking for [scopeSkill] (resolved) alongside [unresolvedTerms] (not). */
    private fun offer(scopeSkill: String?, vararg unresolvedTerms: String) {
        val id = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, payload)
            values ('test', :key, 'Test Offer', cast('{}' as jsonb))
            returning id
            """
        ).param("key", "key-${System.nanoTime()}").query(Long::class.java).single()

        scopeSkill?.let { name ->
            val skill = requireNotNull(catalog.resolve(name)) { "seed catalog should carry $name" }
            jdbc.sql(
                """
                insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
                values (:id, :name, 'UNKNOWN', :skillId)
                """
            ).param("id", id).param("name", name).param("skillId", skill.id).update()
        }

        unresolvedTerms.forEach { term ->
            jdbc.sql(
                """
                insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
                values (:id, :name, 'UNKNOWN', null)
                """
            ).param("id", id).param("name", term).update()
        }
    }

    private fun queueTerm(term: String, key: String, own: Int = 0, market: Int = 0) {
        jdbc.sql(
            """
            insert into unmatched_term (term, normalized_term, occurrences, market_occurrences)
            values (:term, :key, :own, :market)
            """
        ).param("term", term).param("key", key).param("own", own).param("market", market).update()
    }

    private fun TriageQueue.termsInOrder() = entries.map { it.term }

    /**
     * The filter's whole reason for existing. Every corpus term has `occurrences = 0`, so a
     * threshold applied to that column alone would hide the entire market behind a control whose
     * only job is to cut the tail of terms seen once.
     */
    @Test
    fun `the threshold applies to the sum of both counters, not the candidate's alone`() {
        val wanted = "Wanted By Market $suffix"
        queueTerm(wanted, "wantedbymarket$suffix", own = 0, market = 9)
        queueTerm("Seen Once $suffix", "seenonce$suffix", own = 0, market = 1)

        val queue = triage.queue(minOccurrences = 3, ranking = TriageRanking.CORPUS, limit = 100)

        assertThat(queue.termsInOrder()).containsExactly(wanted)
        assertThat(queue.matching).isEqualTo(1)
        assertThat(queue.pending).isEqualTo(2)
    }

    @Test
    fun `a term the candidate read survives a threshold the market alone would not clear`() {
        val read = "Read In An Offer $suffix"
        queueTerm(read, "readinanoffer$suffix", own = 4, market = 0)

        val queue = triage.queue(minOccurrences = 3, ranking = TriageRanking.SCOPE, limit = 100)

        assertThat(queue.termsInOrder()).containsExactly(read)
    }

    /**
     * A truncated queue must not read as a finished one, so the response carries what it left out.
     */
    @Test
    fun `the response reports what it did not return`() {
        repeat(5) { queueTerm("Bulk Term $it $suffix", "bulkterm$it$suffix", market = 7) }
        queueTerm("Below Threshold $suffix", "belowthreshold$suffix", market = 1)

        val queue = triage.queue(minOccurrences = 3, ranking = TriageRanking.CORPUS, limit = 2)

        assertThat(queue.entries).hasSize(2)
        assertThat(queue.matching).isEqualTo(5)
        assertThat(queue.pending).isEqualTo(6)
        assertThat(queue.minOccurrences).isEqualTo(3)
    }

    /**
     * Scope ranking's purpose: two terms the market wants equally, one of them on offers this
     * candidate would actually read.
     */
    @Test
    fun `scope ranking lifts a term that appears alongside a scope skill`() {
        val inScope = "In Scope Term $suffix"
        val outOfScope = "Out Of Scope Term $suffix"
        offer("Java", inScope)
        offer(null, outOfScope)
        queueTerm(inScope, "inscopeterm$suffix", market = 5)
        queueTerm(outOfScope, "outofscopeterm$suffix", market = 5)

        val queue = triage.queue(minOccurrences = 3, ranking = TriageRanking.SCOPE, limit = 100)

        assertThat(queue.termsInOrder()).containsExactly(inScope, outOfScope)
        assertThat(queue.entries.single { it.term == inScope }.inScopeDemand).isEqualTo(1)
        assertThat(queue.entries.single { it.term == outOfScope }.inScopeDemand).isZero()
    }

    /** The toggle has to actually change something, or it is a decoration. */
    @Test
    fun `corpus ranking ignores scope and ranks on the whole market`() {
        val inScope = "Narrow Term $suffix"
        val broad = "Broad Term $suffix"
        offer("Java", inScope)
        offer(null, broad)
        queueTerm(inScope, "narrowterm$suffix", market = 4)
        queueTerm(broad, "broadterm$suffix", market = 40)

        val scoped = triage.queue(minOccurrences = 3, ranking = TriageRanking.SCOPE, limit = 100)
        val corpus = triage.queue(minOccurrences = 3, ranking = TriageRanking.CORPUS, limit = 100)

        assertThat(scoped.termsInOrder()).containsExactly(inScope, broad)
        assertThat(corpus.termsInOrder()).containsExactly(broad, inScope)
    }

    /**
     * The queue serves one job hunt rather than describing a market, so a term met in an offer the
     * candidate actually analysed outranks anything the corpus says under either ranking.
     */
    @Test
    fun `what the candidate read outranks market demand under both rankings`() {
        val read = "Personally Read $suffix"
        val shouted = "Market Favourite $suffix"
        offer("Java", shouted)
        queueTerm(read, "personallyread$suffix", own = 1, market = 0)
        queueTerm(shouted, "marketfavourite$suffix", own = 0, market = 500)

        TriageRanking.entries.forEach { ranking ->
            val queue = triage.queue(minOccurrences = 1, ranking = ranking, limit = 100)
            assertThat(queue.termsInOrder())
                .describedAs("ranking %s", ranking)
                .containsExactly(read, shouted)
        }
    }

    /** Spellings are one queue row, so their in-scope mentions have to land on that one row. */
    @Test
    fun `in-scope demand sums the spellings that share a queue row`() {
        val term = "Power Apps $suffix"
        offer("Java", term)
        offer("Kotlin", term.lowercase())
        queueTerm(term, SkillNormalizer.normalize(term), market = 2)

        val queue = triage.queue(minOccurrences = 1, ranking = TriageRanking.SCOPE, limit = 100)

        assertThat(queue.entries.single { it.term == term }.inScopeDemand).isEqualTo(2)
    }

    @Test
    fun `a queued near-miss arrives with its suggestion attached`() {
        queueTerm("Kubernets", SkillNormalizer.normalize("Kubernets"), own = 3)

        val queue = triage.queue(minOccurrences = 1, ranking = TriageRanking.SCOPE, limit = 100)

        val entry = queue.entries.single { it.term == "Kubernets" }
        assertThat(entry.suggestions.map { it.skillName }).contains("Kubernetes")
    }

    /**
     * A suggestion is a candidate, never a decision. Nothing about producing one may change what
     * the term resolves to, or the review queue would have stopped being the only way in.
     */
    @Test
    fun `showing a suggestion does not resolve or dequeue the term`() {
        queueTerm("Kubernets", SkillNormalizer.normalize("Kubernets"), own = 3)

        triage.queue(minOccurrences = 1, ranking = TriageRanking.SCOPE, limit = 100)

        assertThat(catalog.resolve("Kubernets")).isNull()
        assertThat(triage.queue(minOccurrences = 1, limit = 100).entries.map { it.term })
            .contains("Kubernets")
    }

    /** Scoring 1,500 terms to render 100 would throw away the work of the whole queue each request. */
    @Test
    fun `suggestions are computed only for the page returned`() {
        repeat(4) { queueTerm("Kubernets $it", SkillNormalizer.normalize("Kubernets $it"), own = 3) }

        val queue = triage.queue(minOccurrences = 1, ranking = TriageRanking.CORPUS, limit = 2)

        assertThat(queue.entries).hasSize(2)
        assertThat(queue.matching).isEqualTo(4)
        assertThat(queue.entries).allSatisfy { assertThat(it.suggestions).isNotEmpty }
    }

    /** A number called "in-scope demand" is unreadable without saying what the scope was. */
    @Test
    fun `the queue states the scope it ranked by`() {
        val queue = triage.queue()

        assertThat(queue.scopeSkills).contains("Java", "Kotlin")
        assertThat(queue.ranking).isEqualTo(TriageRanking.SCOPE)
        assertThat(queue.minOccurrences).isEqualTo(TriageService.DEFAULT_MIN_OCCURRENCES)
    }

    /** Only unresolved mentions are queue business; a placed skill is not awaiting a decision. */
    @Test
    fun `a resolved mention does not count toward in-scope demand`() {
        val term = "Counted Once $suffix"
        offer("Java", term)
        // A second offer asking for the same thing, but with the term already placed by the catalog.
        val id = jdbc.sql(
            """
            insert into market_offer (source, offer_key, title, payload)
            values ('test', :key, 'Resolved', cast('{}' as jsonb))
            returning id
            """
        ).param("key", "resolved-${System.nanoTime()}").query(Long::class.java).single()
        val java = requireNotNull(catalog.resolve("Java"))
        jdbc.sql(
            """
            insert into market_offer_skill (market_offer_id, skill_name, level, canonical_skill_id)
            values (:id, :name, 'UNKNOWN', :skillId)
            """
        ).param("id", id).param("name", term).param("skillId", java.id).update()

        queueTerm(term, SkillNormalizer.normalize(term), market = 1)

        val queue = triage.queue(minOccurrences = 1, ranking = TriageRanking.SCOPE, limit = 100)

        assertThat(queue.entries.single { it.term == term }.inScopeDemand).isEqualTo(1)
    }
}
