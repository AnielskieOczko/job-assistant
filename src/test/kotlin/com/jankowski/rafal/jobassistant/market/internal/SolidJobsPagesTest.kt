package com.jankowski.rafal.jobassistant.market.internal

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parsing a source page, in the fast tier because it is a pure function over a string.
 *
 * The property under test is the one V14 asked for and did not get: **the offer's own JSON
 * survives**, including the fields nothing models. Losing them is not visible anywhere — the
 * mapped columns look perfectly correct — which is why it went unnoticed until promotion needed
 * the posting prose.
 */
class SolidJobsPagesTest {

    // The Kotlin module, because the application's mapper has it: without it a missing `isRemote`
    // is a null rather than the declared default, and the test would exercise a mapper production
    // never uses.
    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private val page = """
        {"pageIndex":0,"pageSize":2,"totalCount":2,"totalPages":1,"jobs":[
          {"jobOfferKey":"a","title":"Java Developer","description":"<p>Kotlin</p>",
           "companyLogoUrl":"https://example.com/a.png","benefits":["Pakiet medyczny"]},
          {"jobOfferKey":"b","title":"Data Engineer","languages":["English"]}
        ]}
    """.trimIndent()

    @Test
    fun `the mapped fields are read as before`() {
        val parsed = SolidJobsPages.parse(page, mapper)

        assertEquals(1, parsed.totalPages)
        assertEquals(listOf("Java Developer", "Data Engineer"), parsed.jobs.map { it.title })
        assertEquals("<p>Kotlin</p>", parsed.jobs.first().description)
        assertNull(parsed.jobs.last().description)
    }

    @Test
    fun `each offer keeps its own json, including fields nothing models`() {
        val parsed = SolidJobsPages.parse(page, mapper)

        val raw = assertNotNull(parsed.jobs.first().raw)
        assertTrue(raw.contains("companyLogoUrl"), "unmodelled fields must survive: $raw")
        assertTrue(raw.contains("Pakiet medyczny"))
        assertTrue(assertNotNull(parsed.jobs.last().raw).contains("languages"))
    }

    /** One offer's JSON is its own: a payload carrying the neighbouring row would be worse than none. */
    @Test
    fun `raw json is not shared between offers on a page`() {
        val parsed = SolidJobsPages.parse(page, mapper)

        assertTrue(assertNotNull(parsed.jobs.first().raw).contains("Java Developer"))
        assertTrue(assertNotNull(parsed.jobs.last().raw).contains("Data Engineer"))
        assertTrue(!assertNotNull(parsed.jobs.first().raw).contains("Data Engineer"))
    }

    @Test
    fun `a page with no jobs is an empty page rather than a failure`() {
        val parsed = SolidJobsPages.parse("""{"pageIndex":0,"totalPages":0}""", mapper)

        assertTrue(parsed.jobs.isEmpty())
    }
}
