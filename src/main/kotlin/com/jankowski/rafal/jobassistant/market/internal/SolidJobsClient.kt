package com.jankowski.rafal.jobassistant.market.internal

/**
 * One page of offers from a source.
 *
 * An interface rather than a concrete client for the same reason every model is a
 * `ScriptedChatModel` under test: no test may reach a real third party, whether or not the network
 * happens to be up. The stub is the test seam, not a mock of an HTTP library.
 */
internal interface SolidJobsClient {
    fun fetchPage(division: String, pageIndex: Int, pageSize: Int): SolidJobsPage
}
