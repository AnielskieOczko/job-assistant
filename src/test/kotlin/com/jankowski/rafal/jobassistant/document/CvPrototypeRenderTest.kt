package com.jankowski.rafal.jobassistant.document

import com.jankowski.rafal.jobassistant.document.internal.HtmlText
import com.jankowski.rafal.jobassistant.document.internal.PlaywrightDocumentRenderer
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.ScreenshotType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.test.assertTrue

/**
 * Renders the throwaway CV layout prototypes for issue #14 so they can be judged as PDFs rather
 * than as markup. Deliberately not an `@IntegrationTest`: a layout prototype needs Chromium, not
 * Postgres, and requiring a container would make this un-runnable for the one thing it is for.
 *
 * Skips silently when `target/cv-prototypes` holds no HTML, so it never fails a pdf-tier run for
 * someone who has not generated the prototypes.
 */
@Tag("pdf")
internal class CvPrototypeRenderTest {

    private val dir: Path = Path.of("target", "cv-prototypes")

    @Test
    fun `renders every prototype to PDF and a preview image`() {
        if (!dir.exists()) return
        val pages = dir.listDirectoryEntries()
            .filter { it.extension == "html" && it.name.first().isLetter() && "-" in it.name }
            .sortedBy { it.name }
        if (pages.isEmpty()) return

        val renderer = PlaywrightDocumentRenderer()
        try {
            Playwright.create().use { playwright ->
                playwright.chromium().launch().use { browser ->
                    for (source in pages) {
                        val html = source.readText()

                        val pdf = renderer.toPdf(html)
                        assertTrue(pdf.size > 5_000, "${source.name} produced a suspiciously small PDF")
                        Files.write(dir.resolve("${source.nameWithoutExtension}.pdf"), pdf)

                        // A preview image so the layout can be looked at, not just opened.
                        // A4 is 794 x 1123 CSS px. Previewing at any other width wraps the text
                        // differently from the PDF, which makes the preview a different document.
                        browser.newContext(
                            com.microsoft.playwright.Browser.NewContextOptions()
                                .setViewportSize(794, 1123)
                                .setDeviceScaleFactor(2.0)
                        ).use { context -> context.newPage().use { page ->
                            page.setContent(html)
                            page.waitForLoadState()
                            page.screenshot(
                                com.microsoft.playwright.Page.ScreenshotOptions()
                                    .setPath(dir.resolve("${source.nameWithoutExtension}.png"))
                                    .setFullPage(true)
                                    .setType(ScreenshotType.PNG)
                            )
                        } }

                        // The guard that a redesign is most likely to break silently: every word a
                        // reader sees must still be reachable as text, or CvInvariant stops seeing it.
                        val visible = HtmlText.visibleText(html)
                        assertTrue("Kotlin" in visible, "${source.name} hides skill text from HtmlText")
                        assertTrue("Nordkraft" in visible, "${source.name} hides employer text")

                        println("rendered ${source.nameWithoutExtension}: ${pdf.size / 1024}KB PDF")
                    }
                }
            }
        } finally {
            renderer.close()
        }
    }
}
