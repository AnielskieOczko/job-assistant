package com.jankowski.rafal.jobassistant.document.internal

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.Margin
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

/**
 * Renders through headless Chromium, so the PDF is exactly what a browser shows.
 *
 * The browser is started on first use and then kept: launching Chromium costs a second or two,
 * far too much to pay per request, but paying it at application startup would make every boot and
 * every test wait for a browser most runs never need.
 */
@Component
internal class PlaywrightDocumentRenderer : DocumentRenderer {

    private val log = LoggerFactory.getLogger(PlaywrightDocumentRenderer::class.java)

    /** Two at a time: each render holds roughly 300 MB, and this is a single-user tool. */
    private val renderSlots = Semaphore(2)
    private val startupLock = Any()

    @Volatile private var playwright: Playwright? = null
    @Volatile private var browser: Browser? = null

    override fun toPdf(html: String): ByteArray {
        val chromium = browser()
        renderSlots.acquire()
        try {
            chromium.newContext().use { context ->
                context.newPage().use { page ->
                    page.setContent(html)
                    // Let webfonts and layout settle, or the first render can differ from later ones.
                    page.waitForLoadState()
                    return page.pdf(
                        Page.PdfOptions()
                            .setFormat("A4")
                            .setPrintBackground(true)
                            // Margins belong to the template's @page rule, not to the renderer.
                            .setMargin(Margin().setTop("0").setBottom("0").setLeft("0").setRight("0"))
                    )
                }
            }
        } finally {
            renderSlots.release()
        }
    }

    private fun browser(): Browser =
        browser ?: synchronized(startupLock) {
            browser ?: run {
                log.info("Starting Chromium; Playwright downloads it to ~/.cache/ms-playwright on first use")
                val created = Playwright.create()
                playwright = created
                created.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
                    .also { browser = it }
            }
        }

    /** Only closes what was actually started, so shutdown never launches a browser. */
    @PreDestroy
    fun close() {
        runCatching { browser?.close() }.onFailure { log.warn("Failed to close Chromium", it) }
        runCatching { playwright?.close() }.onFailure { log.warn("Failed to close Playwright", it) }
        browser = null
        playwright = null
    }
}
