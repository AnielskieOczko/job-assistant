package com.jankowski.rafal.jobassistant.document.internal

/**
 * Extracts the text a reader actually sees from a rendered document.
 *
 * The fabrication guard must run over this rather than the raw markup: a CV page contains the
 * words "HTML" and "CSS" in its own doctype and stylesheet, and both are catalog skills, so
 * scanning the source reports a fabrication on every honest document.
 */
internal object HtmlText {

    private val DROPPED_ELEMENTS = Regex(
        "<(script|style|head)\\b[^>]*>.*?</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TAG = Regex("<[^>]*>", RegexOption.DOT_MATCHES_ALL)
    private val DOCTYPE = Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE)
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

    fun visibleText(html: String): String =
        html
            .replace(COMMENT, " ")
            .replace(DOCTYPE, " ")
            .replace(DROPPED_ELEMENTS, " ")
            .replace(TAG, " ")
            .let(::unescape)
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun unescape(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}
