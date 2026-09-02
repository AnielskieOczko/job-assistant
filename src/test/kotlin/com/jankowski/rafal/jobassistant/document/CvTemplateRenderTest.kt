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
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
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

    private val view = CvView(
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
    )

    private val html: String
        get() = templates.process(
            "cv",
            Context(
                Locale.ENGLISH,
                mapOf(
                    "cv" to view,
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
     * Writes a preview beside the PDF so the layout can be looked at rather than only opened.
     *
     * A4 is 794 x 1123 CSS px, and the width is not a detail: an early prototype previewed at
     * 1240px, wrapped differently from the PDF, and showed one page where the PDF had two.
     */
    @Test
    fun `writes a print-width preview and a PDF to target for inspection`() {
        val out = Path.of("target", "cv-preview")
        Files.createDirectories(out)
        Files.write(out.resolve("cv.pdf"), renderer.toPdf(html))

        Playwright.create().use { playwright ->
            playwright.chromium().launch().use { browser ->
                browser.newContext(
                    Browser.NewContextOptions().setViewportSize(794, 1123).setDeviceScaleFactor(2.0)
                ).use { context ->
                    context.newPage().use { page ->
                        page.setContent(html)
                        page.waitForLoadState()
                        page.screenshot(
                            Page.ScreenshotOptions()
                                .setPath(out.resolve("cv.png"))
                                .setFullPage(true)
                                .setType(ScreenshotType.PNG)
                        )
                    }
                }
            }
        }

        assertTrue(Files.size(out.resolve("cv.png")) > 10_000, "preview image is suspiciously small")
    }
}
