package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.ProviderAccount
import com.jankowski.rafal.jobassistant.llm.ProviderAccountLookup
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Picks the profile worth asking, asks it, and remembers the answer for a few minutes.
 *
 * Cached because the figure it fetches moves in cents per day while a dashboard can be refreshed
 * every ten seconds, and every refresh would otherwise be an outbound request against a billing
 * endpoint. The staleness is disclosed rather than hidden: [ProviderAccount.checkedAt] is when the
 * number was actually read, not when it was served.
 *
 * "Not available" is a first-class answer, not an error. A profile pointed at Ollama has no account
 * to report on, and that must read as *not applicable* rather than as something broken.
 */
@Service
internal class CachingProviderAccounts(
    private val client: ProviderAccountClient,
    private val properties: LlmProperties,
) : ProviderAccountLookup {

    private val cached = AtomicReference<Cached?>()

    override fun account(): ProviderAccount {
        cached.get()?.takeIf { it.isFresh(properties.account.cacheTtl) }?.let { return it.account }

        val account = lookUp()
        cached.set(Cached(account, Instant.now()))
        return account
    }

    private fun lookUp(): ProviderAccount {
        if (properties.profiles.isEmpty()) {
            return ProviderAccount.unavailable("No model profiles are configured.")
        }

        // Every profile is tried rather than only the one some task routes to: a profile can be
        // configured and unused, and reporting the key's spend is still the right answer for it.
        properties.profiles.forEach { (name, profile) ->
            val snapshot = client.fetch(profile) ?: return@forEach
            return ProviderAccount(
                modelProfile = name,
                usageUsd = snapshot.usage,
                usageTodayUsd = snapshot.usageToday,
                usageMonthUsd = snapshot.usageMonth,
                limitUsd = snapshot.limit,
                limitRemainingUsd = snapshot.limitRemaining,
                checkedAt = Instant.now(),
                unavailableReason = null,
            )
        }

        return ProviderAccount.unavailable(
            "No configured provider reports account spend, or the provider could not be reached."
        )
    }

    private data class Cached(val account: ProviderAccount, val at: Instant) {
        fun isFresh(ttl: Duration) = !ttl.isZero && Duration.between(at, Instant.now()) < ttl
    }
}
