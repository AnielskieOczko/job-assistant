package com.jankowski.rafal.jobassistant.privacy

/**
 * Removes contact details from pasted offer text before it is shown to a model.
 *
 * A job advert routinely carries a named recruiter's email and phone number. That is a third
 * party's personal data, the candidate never consented on their behalf, and extraction has no use
 * for it - the extractor is looking for requirements, not for who to call.
 *
 * Only the copy bound into the prompt is scrubbed. `job_offer.raw_text` keeps the original: it is
 * the user's own record of what they were sent, it stays on their machine, and re-analysis reads
 * from it.
 */
object OfferTextScrubber {

    private const val EMAIL_PLACEHOLDER = "[email removed]"
    private const val PHONE_PLACEHOLDER = "[phone removed]"

    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")

    /**
     * Narrow on purpose: an international number, or one of the common nine-digit groupings.
     *
     * An earlier, looser version matched any three punctuated digit groups, which also swallowed
     * salary bands (`8 000 - 12 000`) and dates (`2021.01.15`). Offer text is the input to
     * requirement extraction, so corrupting it to catch a number that may not even be there is the
     * wrong trade - a missed recruiter phone is a far smaller problem than a mangled offer.
     */
    private val PHONE = Regex(
        """(?:\+\d[\d\s\-().]{7,}\d)""" +
            """|(?:\b\d{3}[\s\-.]\d{3}[\s\-.]\d{3}\b)""" +
            """|(?:\b\d{2}[\s\-.]\d{3}[\s\-.]\d{2}[\s\-.]\d{2}\b)"""
    )

    fun scrub(offerText: String): String =
        offerText
            .replace(EMAIL, EMAIL_PLACEHOLDER)
            .replace(PHONE, PHONE_PLACEHOLDER)
}
