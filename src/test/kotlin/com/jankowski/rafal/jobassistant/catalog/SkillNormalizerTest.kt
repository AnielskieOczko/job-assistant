package com.jankowski.rafal.jobassistant.catalog

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SkillNormalizerTest {

    @ParameterizedTest
    @CsvSource(
        "React.js, reactjs",
        "ReactJS, reactjs",
        "react js, reactjs",
        "  REACT.JS  , reactjs",
        "Spring Boot, springboot",
        "spring-boot, springboot",
        "Node.js, nodejs",
        "CI/CD, cicd",
        "OAuth 2.0, oauth20",
    )
    fun `collapses spelling variants onto one key`(input: String, expected: String) {
        assertEquals(expected, SkillNormalizer.normalize(input))
    }

    @Test
    fun `expands plus and hash so C, C++ and C# stay distinct`() {
        assertEquals("c", SkillNormalizer.normalize("C"))
        assertEquals("cpp", SkillNormalizer.normalize("C++"))
        assertEquals("csharp", SkillNormalizer.normalize("C#"))

        assertNotEquals(SkillNormalizer.normalize("C"), SkillNormalizer.normalize("C++"))
        assertNotEquals(SkillNormalizer.normalize("C"), SkillNormalizer.normalize("C#"))
    }

    @Test
    fun `punctuation-only input normalises to empty and is never a lookup key`() {
        assertEquals("", SkillNormalizer.normalize("---"))
        assertEquals("", SkillNormalizer.normalize("  "))
    }
}
