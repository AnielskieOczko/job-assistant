package com.jankowski.rafal.jobassistant.privacy.internal

import com.jankowski.rafal.jobassistant.profile.ProfileIdentity

/**
 * Decides whether a rendered prompt carries a direct identifier belonging to any profile.
 *
 * Like `CvInvariant`, this is a floor and not a ceiling. It checks a small set of high-specificity
 * values and deliberately ignores everything where a false positive would be worse than a miss,
 * because the consequence of a match is a hard refusal: a check that fired on ordinary text would
 * take the whole feature down.
 *
 * The two fields most obviously absent are the reason this class is small:
 *
 * - **`location`** is free text that in practice reads "Poland". Guarding it would fail extraction
 *   on every job offer that mentions the country.
 * - **Individual name tokens.** A surname is often an ordinary word - Green, Baker, Young - and
 *   matching one against pasted offer text would reject honest offers. Only the complete name is
 *   matched.
 *
 * Neither field is *sent*; both are simply kept out of prompts by construction rather than policed
 * here. Detection is the backstop for the values worth failing over.
 */
internal object PromptPrivacyInvariant {

    /**
     * Values shorter than this are skipped. Guards against a one-character name or a placeholder
     * turning every prompt into a violation.
     */
    private const val MIN_LENGTH = 5

    /** A phone needs this many digits before a digit-run match means anything. */
    private const val MIN_PHONE_DIGITS = 7

    /** Runs of digits and the punctuation phone numbers are written with. */
    private val PHONE_LIKE = Regex("""[+(]?\d[\d\s\-().]{5,}\d""")

    private val WHITESPACE = Regex("""\s+""")

    /**
     * @return the *names* of the fields found, never their values. Callers put this in an exception
     *   message that is persisted to `analysis.error` and served over HTTP, so echoing the value
     *   would write out the very data this class exists to withhold.
     */
    fun violations(renderedPrompt: String, identities: List<ProfileIdentity>): List<String> {
        if (renderedPrompt.isBlank() || identities.isEmpty()) return emptyList()

        val flattened = renderedPrompt.replace(WHITESPACE, " ").lowercase()
        val phoneCandidates by lazy { digitRunsIn(renderedPrompt) }

        val found = linkedSetOf<String>()
        identities.forEach { identity ->
            if (containsText(flattened, identity.fullName)) found += "fullName"
            if (containsText(flattened, identity.email)) found += "email"
            if (containsPhone(phoneCandidates, identity.phone)) found += "phone"
            if (identity.linkUrls.any { containsText(flattened, stripScheme(it)) }) found += "links"
        }
        return found.toList()
    }

    private fun containsText(flattenedPrompt: String, value: String?): Boolean {
        val needle = value?.replace(WHITESPACE, " ")?.trim()?.lowercase() ?: return false
        if (needle.length < MIN_LENGTH) return false
        return flattenedPrompt.contains(needle)
    }

    /**
     * Compared digit-run by digit-run rather than over the whole prompt flattened to digits: that
     * would splice unrelated numbers together - two adjacent years, say - and invent a match that
     * is not in the text.
     */
    private fun containsPhone(candidates: List<String>, phone: String?): Boolean {
        val digits = phone?.filter(Char::isDigit) ?: return false
        if (digits.length < MIN_PHONE_DIGITS) return false
        return candidates.any { it.contains(digits) }
    }

    private fun digitRunsIn(prompt: String): List<String> =
        PHONE_LIKE.findAll(prompt).map { match -> match.value.filter(Char::isDigit) }.toList()

    /** So `https://github.com/x` in the profile still matches a bare `github.com/x` in a prompt. */
    private fun stripScheme(url: String): String =
        url.substringAfter("://").trimEnd('/')
}
