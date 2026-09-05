package com.jankowski.rafal.jobassistant.privacy.internal

import com.jankowski.rafal.jobassistant.privacy.PrivacyState
import com.jankowski.rafal.jobassistant.profile.ProfileIdentity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The test that makes the manifest honest.
 *
 * The enforced set is computed by running `PromptPrivacyInvariant.violations` against a synthetic
 * prompt carrying every value it can check - not by re-reading `PrivacyManifests`' own list - so a
 * change to what the guard actually catches is caught here rather than by a reader trusting a
 * badge the guard no longer backs. This is the same failure #68 describes for a hand-maintained
 * wire contract, aimed at a hand-maintained privacy claim instead.
 */
class PrivacyManifestTest {

    private val identity = ProfileIdentity(
        profileId = 1L,
        fullName = "Alex Novak",
        email = "someone@example.com",
        phone = "+48 555 123 456",
        linkUrls = listOf("https://github.com/some-handle"),
    )

    /** Every value the invariant can catch, present at once, so `violations` reports its full reach. */
    private val promptCarryingEveryIdentifier =
        "Name: ${identity.fullName}. Email: ${identity.email}. Phone: ${identity.phone}. " +
            "Link: ${identity.linkUrls.single()}"

    @Test
    fun `the manifest's enforced fields are exactly what the invariant can catch`() {
        val actuallyEnforced = PromptPrivacyInvariant.violations(
            promptCarryingEveryIdentifier,
            listOf(identity),
        ).toSet()

        val claimedEnforced = PrivacyManifests.MANIFEST.fields
            .filter { it.state == PrivacyState.ENFORCED }
            .map { it.name }
            .toSet()

        assertEquals(actuallyEnforced, claimedEnforced)
    }

    @Test
    fun `every field name is unique`() {
        val names = PrivacyManifests.MANIFEST.fields.map { it.name }
        assertEquals(names.distinct(), names)
    }

    @Test
    fun `no entry echoes an identifier value, only names and prose`() {
        val identifiers = listOf(
            identity.fullName, identity.email!!, identity.phone!!, identity.linkUrls.single(),
        )
        PrivacyManifests.MANIFEST.fields.forEach { field ->
            identifiers.forEach { identifier ->
                assertTrue(
                    identifier !in field.label && identifier !in field.mechanism,
                    "${field.name} must describe the field, not echo a value",
                )
            }
        }
    }
}
