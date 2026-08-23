package com.jankowski.rafal.jobassistant.profile

/**
 * CEFR levels, declared in ascending order so [atLeast] can compare by ordinal. Offers ask for
 * "English B2"; this is what turns that into a pass/fail rather than a judgement call.
 */
enum class LanguageLevel {
    A1, A2, B1, B2, C1, C2, NATIVE;

    fun atLeast(required: LanguageLevel): Boolean = ordinal >= required.ordinal
}
