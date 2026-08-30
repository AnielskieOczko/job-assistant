package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.ProviderAccountLookup
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedProviderAccountClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * A dashboard refreshing every few seconds must not become a request per refresh against a
 * provider's billing endpoint. Runs on the default TTL, unlike [ProviderAccountIntegrationTest].
 */
@IntegrationTest
internal class ProviderAccountCachingTest(
    @Autowired private val accounts: ProviderAccountLookup,
    @Autowired private val client: ScriptedProviderAccountClient,
) {

    @Test
    fun `repeated reads do not re-ask the provider`() {
        client.reset()

        // One lookup offers every configured profile in turn, so the baseline is that whole sweep
        // rather than a single request. What must not grow is the sweep count.
        accounts.account()
        val afterFirstRead = client.asked.size
        repeat(4) { accounts.account() }

        assertEquals(afterFirstRead, client.asked.size, "the provider was consulted more than once")
    }
}
