package com.jankowski.rafal.jobassistant.document.internal

/**
 * Turns finished HTML into a PDF.
 *
 * An interface rather than a direct Playwright call because Chromium is the heaviest thing in the
 * stack - roughly a 2 GB image and 300 MB per render - and swapping it for a lighter typesetter
 * should stay a one-class change if hosting cost ever outweighs template freedom.
 */
internal interface DocumentRenderer {
    fun toPdf(html: String): ByteArray
}
