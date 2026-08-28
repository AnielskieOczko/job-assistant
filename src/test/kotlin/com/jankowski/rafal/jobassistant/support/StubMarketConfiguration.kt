package com.jankowski.rafal.jobassistant.support

import com.jankowski.rafal.jobassistant.market.internal.SolidJobsClient
import com.jankowski.rafal.jobassistant.market.internal.SolidJobsPage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Replaces the solid.jobs HTTP client with a scripted one for every integration test.
 *
 * The same guarantee [StubLlmConfiguration] gives for models: no test can reach a third party,
 * whether or not the network happens to be up. Ingestion is worth testing precisely because it
 * talks to someone else's API, and a suite that occasionally hit the real one would be both slow
 * and rude.
 */
@TestConfiguration(proxyBeanMethods = false)
class StubMarketConfiguration {

    @Bean
    @Primary
    fun scriptedSolidJobsClient(): ScriptedSolidJobsClient = ScriptedSolidJobsClient()
}

/**
 * Serves pages a test queued, in order, and records what was asked for.
 *
 * An exhausted queue yields an empty page rather than throwing, so a test that scripts one page
 * exercises the loop's own termination instead of a stub's.
 */
class ScriptedSolidJobsClient : SolidJobsClient {

    private val pages = ArrayDeque<SolidJobsPage>()

    /** Every (division, pageIndex, pageSize) the ingestion asked for, in order. */
    val requests = mutableListOf<Request>()

    data class Request(val division: String, val pageIndex: Int, val pageSize: Int)

    fun enqueue(vararg page: SolidJobsPage) = pages.addAll(page)

    fun reset() {
        pages.clear()
        requests.clear()
    }

    override fun fetchPage(division: String, pageIndex: Int, pageSize: Int): SolidJobsPage {
        requests += Request(division, pageIndex, pageSize)
        return pages.removeFirstOrNull() ?: SolidJobsPage()
    }
}
