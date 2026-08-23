package com.jankowski.rafal.jobassistant.document.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlTextTest {

    @Test
    fun `tags are removed and text kept`() {
        assertEquals("Built payment services", HtmlText.visibleText("<li>Built <b>payment</b> services</li>"))
    }

    @Test
    fun `the stylesheet is dropped so its vocabulary is not mistaken for content`() {
        val html = "<html><head><style>.chip { background: #eee; }</style></head><body>Kotlin</body></html>"

        assertEquals("Kotlin", HtmlText.visibleText(html))
    }

    @Test
    fun `the doctype does not leak the word html into the text`() {
        assertFalse(HtmlText.visibleText("<!DOCTYPE html><body>Kotlin</body>").contains("html", ignoreCase = true))
    }

    @Test
    fun `scripts are dropped`() {
        assertEquals("Kotlin", HtmlText.visibleText("<body><script>var css = 1;</script>Kotlin</body>"))
    }

    @Test
    fun `comments are dropped`() {
        assertEquals("Kotlin", HtmlText.visibleText("<body><!-- uses CSS grid -->Kotlin</body>"))
    }

    @Test
    fun `entities are unescaped`() {
        assertEquals("R&D at <Acme>", HtmlText.visibleText("<p>R&amp;D at &lt;Acme&gt;</p>"))
    }

    @Test
    fun `whitespace is collapsed`() {
        assertEquals("one two", HtmlText.visibleText("<p>one</p>\n\n   <p>two</p>"))
    }

    @Test
    fun `attribute values do not become visible text`() {
        val text = HtmlText.visibleText("""<div class="kubernetes-cluster" data-x="Kafka">Kotlin</div>""")

        assertEquals("Kotlin", text)
        assertFalse(text.contains("Kafka"))
    }

    @Test
    fun `real content is still caught after stripping`() {
        val html = "<html><head><style>a{}</style></head><body><li>Scaled <b>Kubernetes</b></li></body></html>"

        assertTrue(HtmlText.visibleText(html).contains("Kubernetes"))
    }
}
