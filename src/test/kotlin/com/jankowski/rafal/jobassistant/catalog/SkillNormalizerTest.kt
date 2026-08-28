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

    @ParameterizedTest
    @CsvSource(
        "Komunikacja, komunikacja",
        "Analiza wymagań, analizawymagan",
        "Zarządzanie projektem, zarzadzanieprojektem",
        "Rozwiązywanie problemów, rozwiazywanieproblemow",
        "Myślenie analityczne, myslenieanalityczne",
        "Dokładność, dokladnosc",
        "Praca zespołowa, pracazespolowa",
        "Przywództwo, przywodztwo",
    )
    fun `folds accented letters onto their base letter`(input: String, expected: String) {
        assertEquals(expected, SkillNormalizer.normalize(input))
    }

    @Test
    fun `an accented spelling keys the same as its unaccented one`() {
        // The whole point of folding. Without it these differ - the accented form loses the letter
        // entirely rather than folding it, so no Polish alias could ever match both spellings.
        assertEquals(SkillNormalizer.normalize("Wspolpraca"), SkillNormalizer.normalize("Współpraca"))
        assertEquals(SkillNormalizer.normalize("Zarzadzanie"), SkillNormalizer.normalize("Zarządzanie"))
        assertEquals(SkillNormalizer.normalize("Dokladnosc"), SkillNormalizer.normalize("Dokładność"))
    }

    @Test
    fun `stroked letters fold, because NFD alone leaves them whole`() {
        // The trap this table exists for. Every other Polish diacritic decomposes under NFD and
        // would survive the ASCII filter as its base letter; l-stroke does not decompose at all, so
        // NFD on its own would delete it and quietly produce a different key.
        assertEquals("l", SkillNormalizer.normalize("ł"))
        assertEquals("zespolem", SkillNormalizer.normalize("zespołem"))
    }

    @Test
    fun `folding leaves pure ASCII input untouched`() {
        // This is what lets the seed's 362 hand-written normalized_alias values stay correct: the
        // rule is provably identical on ASCII, so the drift test passes without a single edit.
        listOf("React.js", "Spring Boot", "C++", "C#", "OAuth 2.0", "CI/CD", "Kubernetes")
            .forEach { assertEquals(legacyNormalize(it), SkillNormalizer.normalize(it), it) }
    }

    /** The rule exactly as it stood before folding was added. */
    private fun legacyNormalize(term: String): String =
        term.lowercase()
            .replace("+", "p")
            .replace("#", "sharp")
            .filter { it in 'a'..'z' || it in '0'..'9' }

    @Test
    fun `punctuation-only input normalises to empty and is never a lookup key`() {
        assertEquals("", SkillNormalizer.normalize("---"))
        assertEquals("", SkillNormalizer.normalize("  "))
    }
}
