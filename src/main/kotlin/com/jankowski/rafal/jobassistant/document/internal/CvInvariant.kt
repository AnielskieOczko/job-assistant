package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.catalog.CanonicalSkill
import com.jankowski.rafal.jobassistant.catalog.SkillCategory

/**
 * The fabrication guard.
 *
 * Scans finished document text for the name of any catalog skill the candidate does not hold. A
 * hit means the model put a technology on a CV with nothing behind it, and the document is thrown
 * away rather than shown to the user.
 *
 * ### What it deliberately does not scan
 *
 * Only concrete technical skills are checked. Names from [SkillCategory.PRACTICE],
 * [SkillCategory.SOFT] and [SkillCategory.OTHER] are ordinary English - "Communication",
 * "Ownership", "Caching" - and scanning for them would fail every honest CV. Short and ambiguous
 * names are skipped for the same reason: "Go", "C", "R" and "REST" occur constantly in normal
 * prose.
 *
 * The check is therefore a floor, not a ceiling: it reliably catches the fabrications that matter
 * (Kubernetes, Kafka, Terraform, AWS) and knowingly ignores a tail of vocabulary where a false
 * positive would be worse than a miss.
 */
internal object CvInvariant {

    private val SCANNED_CATEGORIES = setOf(
        SkillCategory.LANGUAGE,
        SkillCategory.FRAMEWORK,
        SkillCategory.DATABASE,
        SkillCategory.MESSAGING,
        SkillCategory.CLOUD,
        SkillCategory.DEVOPS,
        SkillCategory.TESTING,
        SkillCategory.FRONTEND,
        SkillCategory.AI,
        SkillCategory.TOOL,
    )

    /** Technical names that are also everyday words; scanning for them produces noise, not signal. */
    private val AMBIGUOUS_NAMES = setOf(
        "c", "c++", "c#", "r", "go", "rest", "h2", "vite", "swift", "spring", "neon", "astro",
    )

    /**
     * @return the display names of skills mentioned in [text] that are not in [heldSkillIds].
     */
    fun violations(text: String, catalog: List<CanonicalSkill>, heldSkillIds: Set<Long>): List<String> {
        val haystack = normalize(text)

        return catalog
            .filter { it.id !in heldSkillIds }
            .filter { it.category in SCANNED_CATEGORIES }
            .filter { it.name.lowercase() !in AMBIGUOUS_NAMES && it.name.length >= 3 }
            .filter { containsAsPhrase(haystack, normalize(it.name)) }
            .map { it.name }
            .distinct()
            .sorted()
    }

    /** Lowercases and reduces everything that is not a letter or digit to a single space. */
    private fun normalize(text: String): String =
        buildString(text.length) {
            var lastWasSeparator = true
            for (character in text.lowercase()) {
                if (character.isLetterOrDigit()) {
                    append(character)
                    lastWasSeparator = false
                } else if (!lastWasSeparator) {
                    append(' ')
                    lastWasSeparator = true
                }
            }
        }.trim()

    /**
     * Whole-token phrase match. "kafka" must not fire on "kafkaesque", and "spring boot" must
     * match across the space that "Spring-Boot" collapsed into.
     */
    private fun containsAsPhrase(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false

        var index = haystack.indexOf(needle)
        while (index >= 0) {
            val startsCleanly = index == 0 || haystack[index - 1] == ' '
            val endIndex = index + needle.length
            val endsCleanly = endIndex == haystack.length || haystack[endIndex] == ' '
            if (startsCleanly && endsCleanly) return true
            index = haystack.indexOf(needle, index + 1)
        }
        return false
    }
}
