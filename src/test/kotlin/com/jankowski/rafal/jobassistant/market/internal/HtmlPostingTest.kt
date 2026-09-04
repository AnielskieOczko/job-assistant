package com.jankowski.rafal.jobassistant.market.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The posting-to-text conversion, in the fast tier because it is a pure function.
 *
 * The rule it exists to keep is that **structure survives**. A posting states its requirements as a
 * list, and the extractor reads that list; flattening it into one line is the behaviour
 * `document`'s `HtmlText` wants and the wrong one here.
 */
class HtmlPostingTest {

    @Test
    fun `a list becomes one line per requirement`() {
        val text = HtmlPosting.toText("<ul><li>Kotlin</li><li>Spring Boot</li><li>PostgreSQL</li></ul>")

        assertEquals("- Kotlin\n- Spring Boot\n- PostgreSQL", text)
    }

    @Test
    fun `paragraphs and line breaks are kept apart`() {
        val text = HtmlPosting.toText("<p>Your role</p><p>Building services<br>and running them</p>")

        assertEquals("Your role\n\nBuilding services\nand running them", text)
    }

    @Test
    fun `tags and attributes never reach the text`() {
        val text = HtmlPosting.toText("""<div class="well job - description"><span style="color:red">Java</span></div>""")

        assertEquals("Java", text)
        assertFalse(text.contains("class"))
        assertFalse(text.contains("style"))
    }

    @Test
    fun `script and style content is dropped rather than read as prose`() {
        val text = HtmlPosting.toText("<style>.a{color:red}</style><script>track()</script><p>Kotlin</p>")

        assertEquals("Kotlin", text)
    }

    /** Polish postings are the normal case, and an entity left encoded is a word the extractor misreads. */
    @Test
    fun `entities are decoded`() {
        val text = HtmlPosting.toText("<p>R&amp;D w Krak&oacute;wie&nbsp;&mdash;&nbsp;zdalnie</p>")

        assertTrue(text.startsWith("R&D w Krakówie"))
    }

    /** Decoded once: an escaped entity must not turn into the thing it was escaping. */
    @Test
    fun `an escaped entity stays escaped`() {
        assertEquals("&nbsp;", HtmlPosting.toText("<p>&amp;nbsp;</p>"))
    }

    @Test
    fun `runs of blank lines collapse rather than padding the offer`() {
        val text = HtmlPosting.toText("<p>One</p><div></div><div></div><p>Two</p>")

        assertEquals("One\n\nTwo", text)
    }

    @Test
    fun `text with no markup is returned as it stands`() {
        assertEquals("Senior Kotlin Engineer", HtmlPosting.toText("  Senior Kotlin Engineer  "))
    }

    @Test
    fun `an empty posting yields an empty string rather than whitespace`() {
        assertEquals("", HtmlPosting.toText("<div>   </div>"))
    }
}
