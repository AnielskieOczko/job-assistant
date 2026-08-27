package com.jankowski.rafal.jobassistant.llm.internal

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.model.output.TokenUsage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Pure logic, so it runs in the fast tier with no container and no provider.
 *
 * The case it covers can only otherwise be produced by a live model, which is exactly why it went
 * unnoticed until the eval tier first ran in CI: every `@IntegrationTest` scripts well-formed JSON,
 * so no test could produce a response with no content at all.
 */
class InspectingChatModelTest {

    private val request: ChatRequest = ChatRequest.builder()
        .messages(UserMessage.from("anything"))
        .build()

    private fun respondingWith(message: AiMessage) = object : ChatModel {
        override fun doChat(chatRequest: ChatRequest): ChatResponse = ChatResponse.builder()
            .aiMessage(message)
            .modelName("stub-model")
            .tokenUsage(TokenUsage(7, 13))
            .finishReason(FinishReason.STOP)
            .build()
    }

    private fun wrap(message: AiMessage) = InspectingChatModel(respondingWith(message), emptyList())

    /**
     * A reasoning model that emits only `thinking` returns null content. Left alone, LangChain4j
     * 1.19 dereferences that null in `OutputGuardrailExecutor.rewriteResult` and destroys the
     * reprompt that had already recovered the call.
     */
    @Test
    fun `a response with no content becomes an empty string, not a null`() {
        val empty = AiMessage.builder().text(null).build()

        val response = wrap(empty).chat(request)

        assertEquals("", response.aiMessage().text())
    }

    @Test
    fun `normalising an empty response keeps the rest of it intact`() {
        val empty = AiMessage.builder().text(null).thinking("a long silent deliberation").build()

        val response = wrap(empty).chat(request)

        assertEquals("stub-model", response.modelName())
        assertEquals(7, response.tokenUsage().inputTokenCount())
        assertEquals(13, response.tokenUsage().outputTokenCount())
        assertEquals(FinishReason.STOP, response.finishReason())
        assertEquals("a long silent deliberation", response.aiMessage().thinking())
    }

    @Test
    fun `a response that already has text is passed through untouched`() {
        val message = AiMessage.from("""{"ok":true}""")

        val response = wrap(message).chat(request)

        assertSame(message, response.aiMessage())
        assertEquals("""{"ok":true}""", response.aiMessage().text())
    }

    /** The lower entry point is normalised too, so nothing reaches a guardrail unfiltered. */
    @Test
    fun `doChat normalises as well as chat`() {
        val response = wrap(AiMessage.builder().text(null).build()).doChat(request)

        assertNotNull(response.aiMessage().text())
        assertEquals("", response.aiMessage().text())
    }
}
