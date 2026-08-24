package com.jankowski.rafal.jobassistant.offer.internal

import java.security.MessageDigest

/**
 * Identity for a pasted offer.
 *
 * Whitespace is collapsed before hashing because the same posting copied twice from a browser
 * routinely differs in line breaks, non-breaking spaces and trailing padding - none of which make
 * it a different job. Case is preserved: two postings differing only in case are vanishingly
 * unlikely, and folding it would risk merging genuinely different offers.
 */
internal object OfferContentHash {

    fun of(rawText: String): String {
        val normalized = rawText
            .replace(' ', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
