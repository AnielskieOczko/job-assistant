package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.ProviderAccountLookup
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedProviderAccountClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The second opinion, and what happens when there isn't one.
 *
 * The unavailable case is the one worth pinning: a profile pointed at a local model has no account
 * to report on, and that has to read as "not applicable" rather than as a broken dashboard.
 */
@IntegrationTest
// Caching is the production behaviour and is tested by its own case; here it would only make the
// suite order-dependent, since one test's answer would be served to the next.
@TestPropertySource(properties = ["job-assistant.llm.account.cache-ttl=PT0S"])
internal class ProviderAccountIntegrationTest(
    @Autowired private val accounts: ProviderAccountLookup,
    @Autowired private val client: ScriptedProviderAccountClient,
) {

    @BeforeEach
    fun reset() = client.reset()

    @Test
    fun `a provider that reports nothing is unavailable, not an error`() {
        val account = accounts.account()

        assertFalse(account.available)
        assertNotNull(account.unavailableReason)
        assertTrue(client.asked.isNotEmpty(), "every configured profile should have been offered")
    }

    @Test
    fun `a reporting provider names the profile its figures describe`() {
        client.snapshot = AccountSnapshot(
            usage = BigDecimal("4.20"),
            usageToday = BigDecimal("0.15"),
            usageMonth = BigDecimal("1.10"),
            limit = BigDecimal("10.00"),
            limitRemaining = BigDecimal("5.80"),
        )

        val account = accounts.account()

        assertTrue(account.available)
        assertEquals(0, BigDecimal("4.20").compareTo(account.usageUsd))
        assertEquals(0, BigDecimal("0.15").compareTo(account.usageTodayUsd))
        // Without this the reader cannot tell whose spend they are being shown, which is the whole
        // point of putting it next to a figure this application computed itself.
        assertNotNull(account.modelProfile)
        assertNotNull(account.checkedAt)
    }
}
