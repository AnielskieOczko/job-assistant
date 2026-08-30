package com.jankowski.rafal.jobassistant.support

import com.jankowski.rafal.jobassistant.llm.internal.AccountSnapshot
import com.jankowski.rafal.jobassistant.llm.internal.ModelProfile
import com.jankowski.rafal.jobassistant.llm.internal.ProviderAccountClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Keeps every integration test away from the provider's billing endpoint.
 *
 * The same guarantee [StubLlmConfiguration] and [StubMarketConfiguration] give, and the one that
 * matters most of the three: this is the client that carries a live API key to a third party.
 */
@TestConfiguration(proxyBeanMethods = false)
internal class StubProviderAccountConfiguration {

    @Bean
    @Primary
    internal fun scriptedProviderAccountClient(): ScriptedProviderAccountClient =
        ScriptedProviderAccountClient()
}

/**
 * Answers with whatever a test set, and null by default.
 *
 * Null is the honest default: most profiles are not routers with a billing API, so "this provider
 * reports nothing" is the ordinary case rather than a failure to be scripted around.
 */
internal class ScriptedProviderAccountClient : ProviderAccountClient {

    var snapshot: AccountSnapshot? = null

    /** Every profile the lookup asked about, in order. */
    val asked = mutableListOf<ModelProfile>()

    fun reset() {
        snapshot = null
        asked.clear()
    }

    override fun fetch(profile: ModelProfile): AccountSnapshot? {
        asked += profile
        return snapshot
    }
}
