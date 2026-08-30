package com.jankowski.rafal.jobassistant.llm.internal

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.math.BigDecimal

/**
 * Asks a provider what the key has spent.
 *
 * An interface so that no test can reach openrouter.ai, on exactly the principle
 * `ScriptedSolidJobsClient` exists for: a suite that occasionally hit a real third party would be
 * both slow and rude, and this one would be doing it with a live billing key.
 */
internal interface ProviderAccountClient {
    /** Null when the profile is not a provider that reports account spend, or the call failed. */
    fun fetch(profile: ModelProfile): AccountSnapshot?
}

internal data class AccountSnapshot(
    val usage: BigDecimal?,
    val usageToday: BigDecimal?,
    val usageMonth: BigDecimal?,
    val limit: BigDecimal?,
    val limitRemaining: BigDecimal?,
)

/**
 * Reads `GET /api/v1/key` on OpenRouter.
 *
 * That endpoint rather than `/api/v1/credits` or `/api/v1/activity`, both of which need a
 * *management* key and answer 403 to the inference key this application holds. `/key` describes
 * the key doing the calling, which is exactly the one whose spend is worth comparing against.
 *
 * **The request carries the API key and nothing else** - no prompt, no profile field, no
 * identifier - so it is outside the outbound-privacy rule's concern rather than an exception to it.
 *
 * Failures are swallowed into null. This is a comparison, not a dependency: a provider being down
 * must cost the reader the second opinion, never the dashboard.
 */
internal class OpenRouterAccountClient(private val restClient: RestClient) : ProviderAccountClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetch(profile: ModelProfile): AccountSnapshot? {
        if (!profile.baseUrl.contains(OPENROUTER_HOST, ignoreCase = true)) return null

        return runCatching {
            restClient.get()
                .uri("${profile.baseUrl.trimEnd('/')}/key")
                .header("Authorization", "Bearer ${profile.apiKey}")
                .retrieve()
                .body(KeyResponse::class.java)
                ?.data
                ?.toSnapshot()
        }.onFailure { log.debug("Could not read OpenRouter key usage: {}", it.message) }.getOrNull()
    }

    /** Unknown fields are ignored: the provider adds them and none of them are ours to model. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class KeyResponse(val data: KeyData? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class KeyData(
        val usage: BigDecimal? = null,
        val usage_daily: BigDecimal? = null,
        val usage_monthly: BigDecimal? = null,
        val limit: BigDecimal? = null,
        val limit_remaining: BigDecimal? = null,
    ) {
        fun toSnapshot() = AccountSnapshot(
            usage = usage,
            usageToday = usage_daily,
            usageMonth = usage_monthly,
            limit = limit,
            limitRemaining = limit_remaining,
        )
    }

    private companion object {
        const val OPENROUTER_HOST = "openrouter.ai"
    }
}
