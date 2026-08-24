package com.jankowski.rafal.jobassistant.catalog

/**
 * Collapses the many spellings of a skill onto one lookup key: "React.js", "react js" and
 * "REACTJS" all become "reactjs".
 *
 * `+` and `#` are expanded before punctuation is stripped, otherwise "C++" and "C#" would both
 * degrade into "c" and collide with the C language. The seed migration applies exactly this rule
 * when it computes `skill_alias.normalized_alias`; [SkillCatalogSeedTest] asserts the two agree.
 */
object SkillNormalizer {

    fun normalize(term: String): String =
        term.lowercase()
            .replace("+", "p")
            .replace("#", "sharp")
            .filter { it in 'a'..'z' || it in '0'..'9' }
}
