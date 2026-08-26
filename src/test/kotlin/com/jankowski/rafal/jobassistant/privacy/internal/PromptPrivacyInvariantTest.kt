package com.jankowski.rafal.jobassistant.privacy.internal

import com.jankowski.rafal.jobassistant.profile.ProfileIdentity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard is a hard refusal, so its false-positive behaviour matters as much as what it catches:
 * a check that fired on ordinary offer text would take document generation down entirely.
 */
class PromptPrivacyInvariantTest {

    private val identity = ProfileIdentity(
        profileId = 1L,
        fullName = "Alex Novak",
        email = "someone@example.com",
        phone = "+48 555 123 456",
        linkUrls = listOf("https://github.com/some-handle"),
    )

    private fun violations(prompt: String) = PromptPrivacyInvariant.violations(prompt, listOf(identity))

    @Test
    fun `a clean prompt has no violations`() {
        assertEquals(emptyList(), violations("Skills: Kotlin, Spring Boot. Backend Engineer at Acme (2021-2023)."))
    }

    @Test
    fun `the full name is caught`() {
        assertEquals(listOf("fullName"), violations("Name: Alex Novak\nSkills: Kotlin"))
    }

    @Test
    fun `the name is caught regardless of case or spacing`() {
        assertEquals(listOf("fullName"), violations("prepared by  ALEX   NOVAK  today"))
    }

    @Test
    fun `the email is caught`() {
        assertTrue("email" in violations("Contact: SOMEONE@example.com"))
    }

    @Test
    fun `a phone is caught despite different formatting`() {
        assertTrue("phone" in violations("Call 0048-555-123-456 for details"))
    }

    @Test
    fun `a link is caught without its scheme`() {
        assertTrue("links" in violations("see github.com/some-handle for code"))
    }

    @Test
    fun `every violated field is reported once`() {
        val found = violations("Alex Novak, someone@example.com, Alex Novak again")
        assertEquals(listOf("fullName", "email"), found)
    }

    // The false-positive cases below are the reason the invariant is deliberately narrow.

    @Test
    fun `a surname alone is not a violation`() {
        // Offers legitimately contain ordinary words, and many surnames are ordinary words.
        assertEquals(emptyList(), violations("Novak Consulting is not the hiring company"))
    }

    @Test
    fun `a location can never be guarded because it is not carried at all`() {
        // ProfileIdentity has no location field, which is the enforcement: "Poland" is the real
        // value in the sample profile, and guarding it would fail extraction on every Polish offer.
        // Kept out of the type rather than filtered in the matcher, so it cannot be reintroduced
        // by accident.
        assertEquals(emptyList(), violations("Fully remote, candidates based in Poland preferred."))
    }

    @Test
    fun `adjacent years are not mistaken for a phone number`() {
        // Flattening the whole prompt to digits would splice these into one long run.
        assertEquals(emptyList(), violations("Backend Engineer 2021 2022 2023 delivering services"))
    }

    @Test
    fun `a short identifier is ignored`() {
        val short = ProfileIdentity(2L, "Bo", null, null, emptyList())
        assertEquals(emptyList(), PromptPrivacyInvariant.violations("Both roles used Kotlin", listOf(short)))
    }

    @Test
    fun `no profiles means nothing to check`() {
        assertEquals(emptyList(), PromptPrivacyInvariant.violations("Alex Novak", emptyList()))
    }

    @Test
    fun `a second profile is checked too`() {
        val other = identity.copy(profileId = 2L, fullName = "Someone Else", email = null, phone = null)
        val found = PromptPrivacyInvariant.violations("written by Someone Else", listOf(identity, other))
        assertEquals(listOf("fullName"), found)
    }
}
