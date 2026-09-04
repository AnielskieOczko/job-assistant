package com.jankowski.rafal.jobassistant.market.internal

/**
 * Turns a posting's HTML into the plain text an offer is stored as.
 *
 * `document` has [com.jankowski.rafal.jobassistant.document.internal.HtmlText] and this is
 * deliberately not it - beyond the module boundary that forbids reaching for it, the two want
 * opposite things. That one collapses all whitespace to single spaces, which is right for a
 * fabrication scan over a rendered CV and wrong here: a job posting states its requirements as a
 * list, and a wall of text with the line breaks removed is measurably harder for an extractor to
 * read as separate requirements. Structure is the payload, so `<li>`, `<br>` and `</p>` survive as
 * line breaks.
 *
 * A pure function over a string; the fast tier covers it.
 */
internal object HtmlPosting {

    private val DROPPED_ELEMENTS = Regex(
        "<(script|style)\\b[^>]*>.*?</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val LIST_ITEM = Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val LIST_ITEM_END = Regex("</li\\s*>", RegexOption.IGNORE_CASE)
    private val BREAKING_TAG = Regex(
        "</?(br|p|div|ul|ol|h[1-6]|tr|table|section|article)\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val TAG = Regex("<[^>]*>", RegexOption.DOT_MATCHES_ALL)
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val TRAILING_SPACE = Regex("[ \\t]+(\\n|$)")
    private val BLANK_RUN = Regex("\\n{3,}")

    fun toText(html: String): String = html
        .replace(COMMENT, " ")
        .replace(DROPPED_ELEMENTS, " ")
        // `</li>` is dropped rather than broken on: the next `<li>` already supplies the newline,
        // and breaking on both would put a blank line between every requirement in a list.
        .replace(LIST_ITEM_END, "")
        // Before the generic break, so the bullet marker lands inside the item rather than on the
        // blank line above it.
        .replace(LIST_ITEM, "\n- ")
        .replace(BREAKING_TAG, "\n")
        .replace(TAG, " ")
        .let(::unescape)
        .replace("\r\n", "\n")
        .replace(Regex("[ \\t]+"), " ")
        .replace(TRAILING_SPACE, "$1")
        .replace(Regex("\\n +"), "\n")
        .replace(BLANK_RUN, "\n\n")
        .trim()

    private fun unescape(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&oacute;", "ó")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace("&bull;", "-")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&ldquo;", "\"")
        .replace("&rdquo;", "\"")
        // Last, so an escaped entity such as &amp;nbsp; does not decode twice into a space.
        .replace("&amp;", "&")
}
