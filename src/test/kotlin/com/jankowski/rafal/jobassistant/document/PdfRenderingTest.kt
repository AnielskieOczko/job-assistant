package com.jankowski.rafal.jobassistant.document

import com.jankowski.rafal.jobassistant.document.internal.DocumentRenderer
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

/**
 * Excluded from the default build: the first run downloads Chromium (a few hundred MB) and each
 * render costs a second or so. Run with `./mvnw test -Dgroups=pdf`.
 */
@Tag("pdf")
@IntegrationTest
internal class PdfRenderingTest(@Autowired private val renderer: DocumentRenderer) {

    private val html = """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"><title>CV</title>
        <style>
          @page { size: A4; margin: 14mm; }
          body { font-family: system-ui, sans-serif; }
          /* Grid and flexbox are the reason this renders in Chromium rather than a JVM library. */
          .skills { display: flex; gap: 6px; }
          .chip { background: #eef3f7; border-radius: 3px; padding: 2px 7px; }
        </style></head>
        <body>
          <h1>Rafal Jankowski</h1>
          <div class="skills"><span class="chip">Kotlin</span><span class="chip">Spring Boot</span></div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `renders HTML to a real PDF`() {
        val pdf = renderer.toPdf(html)

        assertTrue(pdf.size > 1000, "expected a non-trivial PDF, got ${pdf.size} bytes")
        assertTrue(
            pdf.decodeToString(0, 5).startsWith("%PDF-"),
            "expected a PDF header, got ${pdf.decodeToString(0, 8)}",
        )
    }

    @Test
    fun `rendering is repeatable`() {
        val first = renderer.toPdf(html)
        val second = renderer.toPdf(html)

        // Byte equality is not guaranteed (PDFs embed a creation date), but size should be stable.
        assertTrue(
            kotlin.math.abs(first.size - second.size) < 500,
            "renders differed wildly in size: ${first.size} vs ${second.size}",
        )
    }
}
