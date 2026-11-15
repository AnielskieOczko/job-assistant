package com.jankowski.rafal.jobassistant.catalog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeniorityLevelTest {

    @Test
    fun `parses observed corpus seniority labels`() {
        assertEquals(SeniorityLevel.SENIOR, SeniorityLevel.parse("Senior"))
        assertEquals(SeniorityLevel.REGULAR, SeniorityLevel.parse("Regular"))
        assertEquals(SeniorityLevel.REGULAR, SeniorityLevel.parse("mid"))
        assertEquals(SeniorityLevel.JUNIOR, SeniorityLevel.parse("Junior"))
        assertEquals(SeniorityLevel.INTERN, SeniorityLevel.parse("Intern"))
    }

    @Test
    fun `treats None, blank, null and unknown values as null`() {
        assertNull(SeniorityLevel.parse("None"))
        assertNull(SeniorityLevel.parse("  "))
        assertNull(SeniorityLevel.parse(null))
        assertNull(SeniorityLevel.parse("Staff"))
    }
}
