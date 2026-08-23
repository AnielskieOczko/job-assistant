package com.jankowski.rafal.jobassistant.document.internal

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

internal interface CvTailor {

    @SystemMessage(fromResource = "/prompts/cv-tailoring-system.md")
    @UserMessage(fromResource = "/prompts/cv-tailoring-user.md")
    fun tailor(
        @V("roleTitle") roleTitle: String,
        @V("company") company: String,
        @V("requirements") requirements: String,
        @V("profile") profile: String,
        @V("language") language: String,
    ): TailoredCv
}

/**
 * The model's choices, not the document. Bullets are referenced by id so every line on the
 * finished CV can be traced back to a profile record; a rewritten [TailoredBullet.text] replaces
 * the wording of that record and nothing else.
 */
internal data class TailoredCv(
    val summaryLine: String = "",
    val skillNames: List<String> = emptyList(),
    val bullets: List<TailoredBullet> = emptyList(),
)

internal data class TailoredBullet(
    val bulletId: Long = 0,
    val text: String = "",
)

internal interface CoverLetterWriter {

    @SystemMessage(fromResource = "/prompts/cover-letter-system.md")
    @UserMessage(fromResource = "/prompts/cover-letter-user.md")
    fun write(
        @V("roleTitle") roleTitle: String,
        @V("company") company: String,
        @V("requirements") requirements: String,
        @V("profile") profile: String,
        @V("language") language: String,
    ): CoverLetter
}

internal data class CoverLetter(
    val paragraphs: List<String> = emptyList(),
)
