package com.jankowski.rafal.jobassistant.linkedin.internal

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

internal interface LinkedInDrafter {

    @SystemMessage(fromResource = "/prompts/linkedin-draft-system.md")
    @UserMessage(fromResource = "/prompts/linkedin-draft-user.md")
    fun draft(
        @V("section") section: String,
        @V("guidance") guidance: String,
        @V("context") context: String,
        @V("notes") notes: String,
    ): AuthoredLinkedInContent
}

internal data class AuthoredLinkedInContent(
    val content: String = "",
)

@Suppress("USELESS_ELVIS")
internal fun AuthoredLinkedInContent.safeContent(): String = (content ?: "").trim()
