package com.jankowski.rafal.jobassistant.profile

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageLevelTest {

    @Test
    fun `a higher level satisfies a lower requirement`() {
        assertTrue(LanguageLevel.C1.atLeast(LanguageLevel.B2))
        assertTrue(LanguageLevel.NATIVE.atLeast(LanguageLevel.A1))
    }

    @Test
    fun `an equal level satisfies the requirement`() {
        assertTrue(LanguageLevel.B2.atLeast(LanguageLevel.B2))
    }

    @Test
    fun `a lower level does not satisfy a higher requirement`() {
        assertFalse(LanguageLevel.B1.atLeast(LanguageLevel.B2))
        assertFalse(LanguageLevel.A1.atLeast(LanguageLevel.NATIVE))
    }

    @Test
    fun `declaration order is the CEFR order the comparison relies on`() {
        assertTrue(
            LanguageLevel.entries.map { it.name } ==
                listOf("A1", "A2", "B1", "B2", "C1", "C2", "NATIVE")
        )
    }
}
