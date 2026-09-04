package com.jankowski.rafal.jobassistant.catalog

/**
 * "Does this text name a catalog skill the candidate does not hold?" - answered once, for every
 * surface that has to ask.
 *
 * The question was born inside the CV fabrication guard, where the answer is a hard refusal. It is
 * stated here because a second caller arrived that needs the same reading and a *different*
 * consequence: a polish suggestion naming an unheld technology is flagged for the candidate rather
 * than thrown away, because nothing is going to an employer yet and the honest response may be to
 * declare the skill. Two callers deriving "nearly the same notion of a mention" separately is how
 * a document and a suggestion start disagreeing about the same sentence - the reason `pg_trgm` was
 * rejected for [SkillCatalog.suggest], in a new place.
 *
 * It lives in `catalog` because it reads catalog vocabulary and holds nothing: the entries are
 * passed in, the held set is passed in, and `catalog` goes on depending on nothing.
 *
 * ### What it deliberately does not scan
 *
 * Only concrete technical skills are checked. Names from [SkillCategory.PRACTICE],
 * [SkillCategory.SOFT] and [SkillCategory.OTHER] are ordinary English - "Communication",
 * "Ownership", "Caching" - and scanning for them would fire on every honest sentence. Short and
 * ambiguous names are skipped for the same reason: "Go", "C", "R" and "REST" occur constantly in
 * normal prose.
 *
 * The check is therefore a floor, not a ceiling: it reliably catches the mentions that matter
 * (Kubernetes, Kafka, Terraform, AWS) and knowingly ignores a tail of vocabulary where a false
 * positive would be worse than a miss.
 */
object SkillMentions {

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
     * @return the display names of skills mentioned in [text] that are not in [heldSkillIds],
     *   name-ordered and without duplicates.
     */
    fun unheld(text: String, catalog: List<CanonicalSkill>, heldSkillIds: Set<Long>): List<String> {
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
