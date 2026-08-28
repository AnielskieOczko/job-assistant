package com.jankowski.rafal.jobassistant.catalog

import java.text.Normalizer

/**
 * Collapses the many spellings of a skill onto one lookup key: "React.js", "react js" and
 * "REACTJS" all become "reactjs".
 *
 * `+` and `#` are expanded before punctuation is stripped, otherwise "C++" and "C#" would both
 * degrade into "c" and collide with the C language. The seed migration applies exactly this rule
 * when it computes `skill_alias.normalized_alias`; `SkillCatalogIntegrationTest` asserts the two
 * agree.
 *
 * Accented letters are **folded onto their base letter**, not deleted. The final filter is an ASCII
 * allowlist, so without folding "Zarządzanie" would key as `zarzdzanie` and "współpraca" as
 * `wsppraca` - which is not equal to `wspolpraca`, meaning the accented and unaccented spellings of
 * one Polish word would never resolve to the same skill. Since offers are ingested from a Polish
 * job board, that is the difference between a Polish alias working and not existing at all.
 */
object SkillNormalizer {

    /**
     * Latin letters that carry their mark *inside* the glyph rather than as a combining character.
     *
     * These have no NFD decomposition, so [Normalizer] leaves them whole and the ASCII filter then
     * deletes them - the one case where relying on NFD alone looks correct and silently is not.
     * Polish `ł` is the reason this table exists: `ą ć ę ń ó ś ź ż` all decompose, `ł` does not.
     */
    private val NON_DECOMPOSING = mapOf(
        'ł' to "l",
        'ø' to "o",
        'đ' to "d",
        'ð' to "d",
        'æ' to "ae",
        'œ' to "oe",
        'ß' to "ss",
        'þ' to "th",
    )

    fun normalize(term: String): String {
        val folded = buildString(term.length) {
            for (char in term.lowercase()) append(NON_DECOMPOSING[char] ?: char)
        }

        // NFD splits "ą" into "a" + a combining ogonek; the ASCII filter below then drops the mark
        // and keeps the base letter. Order against the +/# expansion is free - NFD touches neither -
        // but folding has to come before the filter or there is nothing left to fold.
        return Normalizer.normalize(folded, Normalizer.Form.NFD)
            .replace("+", "p")
            .replace("#", "sharp")
            .filter { it in 'a'..'z' || it in '0'..'9' }
    }
}
