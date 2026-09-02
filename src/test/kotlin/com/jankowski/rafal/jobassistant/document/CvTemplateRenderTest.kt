package com.jankowski.rafal.jobassistant.document

import com.jankowski.rafal.jobassistant.document.internal.CvCredentialView
import com.jankowski.rafal.jobassistant.document.internal.CvEducationView
import com.jankowski.rafal.jobassistant.document.internal.CvProjectView
import com.jankowski.rafal.jobassistant.document.internal.CvRoleView
import com.jankowski.rafal.jobassistant.document.internal.CvSkillGroupView
import com.jankowski.rafal.jobassistant.document.internal.CvView
import com.jankowski.rafal.jobassistant.document.internal.DocumentRenderer
import com.jankowski.rafal.jobassistant.document.internal.HtmlText
import com.jankowski.rafal.jobassistant.profile.ProfileLink
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.ScreenshotType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.assertTrue

/**
 * Renders the real CV template, which is the only place the layout can actually be wrong.
 *
 * Inherits the prototype render test it replaces (issue #14 built that against throwaway HTML;
 * this one runs the template a generated CV is produced from). The fixture is deliberately fuller
 * than `docs/sample-profile.json` and full of Polish diacritics: a layout that looks right on half
 * a page has not been tested, and a font subset missing ł, ą, ę, ś or ż only shows up on words
 * like Poznań and Wrocław.
 */
@Tag("pdf")
@IntegrationTest
internal class CvTemplateRenderTest(
    @Autowired private val templates: TemplateEngine,
    @Autowired private val renderer: DocumentRenderer,
) {

    private val badgeSkills = listOf("Kotlin", "Spring Boot", "PostgreSQL", "Kafka", "Testcontainers")

    private fun view(portrait: String? = null) = CvView(
        fullName = "Rafał Jankowski",
        headline = "Backend Engineer · JVM",
        summaryLine = "Backend engineer building payment systems in Kotlin and Spring Boot, " +
            "with a preference for deterministic pipelines over clever ones.",
        contacts = listOf("kandydat@example.com", "+48 600 000 000", "Poznań, Polska"),
        links = listOf(ProfileLink(1, "github.com/example", "https://github.com/example")),
        skillGroups = listOf(
            CvSkillGroupView("LANGUAGE", listOf("Kotlin", "Java", "SQL")),
            CvSkillGroupView("FRAMEWORK", listOf("Spring Boot", "Spring Data")),
            CvSkillGroupView("DATABASE", listOf("PostgreSQL", "Flyway")),
            CvSkillGroupView("MESSAGING", listOf("Kafka")),
            CvSkillGroupView("TESTING", listOf("JUnit 5", "Testcontainers")),
            CvSkillGroupView("PRACTICE", listOf("Współpraca", "Analiza wymagań")),
        ),
        experiences = (1..4).map { index ->
            CvRoleView(
                company = "Spółka Płatnicza $index",
                roleTitle = "Senior Backend Engineer",
                period = "mar 2021 — obecnie",
                bullets = (1..5).map {
                    "Zbudował usługę rozliczeń w Kotlinie, obsługującą ćwierć miliona zdarzeń " +
                        "dziennie, i skrócił czas księgowania z godzin do minut ($it)"
                },
                skills = badgeSkills,
            )
        },
        education = listOf(
            CvEducationView("Informatyka, Uniwersytet im. Adama Mickiewicza w Poznaniu", "2014 — 2018")
        ),
        credentials = listOf(
            CvCredentialView("Certyfikat Świadomości Bezpieczeństwa", "Ośrodek Szkoleń", "Issued Jan 2024")
        ),
        projects = listOf(
            CvProjectView(
                name = "Asystent Rekrutacyjny",
                url = "https://github.com/example/job-assistant",
                period = "2025 — obecnie",
                bullets = listOf("Zbudował deterministyczny raport luk kompetencyjnych w Kotlinie."),
                skills = listOf("Kotlin", "PostgreSQL"),
            )
        ),
        languages = listOf("Polski (native)", "Angielski (C1)", "Niemiecki (B1)"),
        portrait = portrait,
    )

    private val html: String get() = htmlWith(null)

    private fun htmlWith(portrait: String?): String =
        templates.process(
            "cv",
            Context(
                Locale.ENGLISH,
                mapOf(
                    "cv" to view(portrait),
                    "langCode" to "pl",
                    "consentClause" to "Wyrażam zgodę na przetwarzanie moich danych osobowych.",
                ),
            ),
        )

    @Test
    fun `renders the CV template to a real PDF`() {
        val pdf = renderer.toPdf(html)

        assertTrue(pdf.size > 5_000, "expected a non-trivial PDF, got ${pdf.size} bytes")
        assertTrue(pdf.decodeToString(0, 5).startsWith("%PDF-"), "expected a PDF header")
    }

    /**
     * The guard a redesign is most likely to break silently. `CvInvariant` scans
     * [HtmlText.visibleText], so a skill name rendered through `::before` content or a background
     * image would display on the page while being invisible to the fabrication check.
     */
    @Test
    fun `every skill a reader sees is still reachable as text`() {
        val visible = HtmlText.visibleText(html)

        badgeSkills.forEach { assertTrue(it in visible, "badge '$it' is not in the visible text") }
        assertTrue("Spółka Płatnicza 1" in visible, "employer text is not visible")
        assertTrue("Uniwersytet im. Adama Mickiewicza" in visible, "education text is not visible")

        // 149 KB of base64 font data lives in a <style> element; none of it may leak into what the
        // invariant scans, or every CV would carry a haystack of accidental substrings.
        assertTrue("@font-face" !in visible, "stylesheet content leaked into the visible text")
    }

    /**
     * The portrait is optional *by construction*: with no photo there is no image element and no
     * second grid column, so the header occupies exactly the space it did before the feature
     * existed. That is what the layout was designed for, and it is only true if nothing renders.
     */
    @Test
    fun `a portrait adds a column when present and nothing at all when absent`() {
        val dataUri = "data:image/png;base64,iVBORw0KGgo="

        // The class attribute on the element, not the stylesheet: `.head.has-photo` is declared in
        // the CSS either way, so searching the whole document for the name proves nothing.
        val withPhoto = htmlWith(dataUri)
        assertTrue("class=\"head has-photo\"" in withPhoto, "the header did not switch to its two-column form")
        assertTrue(dataUri in withPhoto, "the portrait did not reach the document")

        val without = html
        assertTrue("class=\"head has-photo\"" !in without, "the header reserved a column for an absent photo")
        assertTrue("<img" !in without, "an image element rendered with no portrait to show")
    }

    /**
     * A `data:` URI rather than a path, for the reason the fonts are embedded:
     * `PlaywrightDocumentRenderer` calls `setContent` with no base URL, so `/api/profiles/1/portrait`
     * would render as a broken image in the PDF - and only in the PDF, which is the worst place to
     * find out.
     */
    @Test
    fun `the portrait is inlined rather than linked`() {
        val rendered = htmlWith("data:image/png;base64,iVBORw0KGgo=")

        assertTrue("src=\"data:image/png;base64," in rendered, "the portrait is not inlined")
        assertTrue("/api/profiles/" !in rendered, "the document links out to an endpoint it cannot reach")
    }

    /**
     * A real image rather than a truncated header, so the preview shows what a photo costs the
     * page. Generated rather than committed: a portrait fixture in the repository would be a
     * binary nobody can review in a diff.
     */
    private fun samplePortrait(): String {
        val image = BufferedImage(240, 300, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            paint = Color(0x8A, 0x92, 0x9C)
            fillRect(0, 0, 240, 300)
            dispose()
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Writes a preview beside the PDF so the layout can be looked at rather than only opened.
     *
     * A4 is 794 x 1123 CSS px, and the width is not a detail: an early prototype previewed at
     * 1240px, wrapped differently from the PDF, and showed one page where the PDF had two.
     */
    @Test
    fun `writes a print-width preview and a PDF to target for inspection`() {
        val out = Path.of("target", "cv-preview")
        Files.createDirectories(out)

        // Both header forms, because the photo's cost in vertical space is the thing #14 weighed
        // and the thing a later change is most likely to spoil.
        val variants = mapOf("cv" to html, "cv-portrait" to htmlWith(samplePortrait()))
        variants.forEach { (name, source) -> Files.write(out.resolve("$name.pdf"), renderer.toPdf(source)) }

        Playwright.create().use { playwright ->
            playwright.chromium().launch().use { browser ->
                browser.newContext(
                    Browser.NewContextOptions().setViewportSize(794, 1123).setDeviceScaleFactor(2.0)
                ).use { context ->
                    variants.forEach { (name, source) ->
                        context.newPage().use { page ->
                            page.setContent(source)
                            page.waitForLoadState()
                            page.screenshot(
                                Page.ScreenshotOptions()
                                    .setPath(out.resolve("$name.png"))
                                    .setFullPage(true)
                                    .setType(ScreenshotType.PNG)
                            )
                        }
                    }
                }
            }
        }

        variants.keys.forEach {
            assertTrue(Files.size(out.resolve("$it.png")) > 10_000, "$it preview is suspiciously small")
        }
    }
}
