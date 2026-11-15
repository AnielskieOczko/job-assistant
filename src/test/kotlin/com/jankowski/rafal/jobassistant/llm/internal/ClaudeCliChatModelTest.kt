package com.jankowski.rafal.jobassistant.llm.internal

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

internal class ClaudeCliChatModelTest {

    private val json = JsonMapper.builder().build()

    @Test
    fun `invokes claude cli with system prompt, json schema, and parses result and token usage`() {
        var capturedArgs: List<String> = emptyList()
        var capturedStdin = ""

        val model = ClaudeCliChatModel(
            command = "claude",
            modelName = "sonnet",
            timeout = Duration.ofSeconds(30),
            strictSchema = true,
            listeners = emptyList(),
            json = json,
            runner = { args, stdin, _ ->
                capturedArgs = args
                capturedStdin = stdin
                CliProcessResult(
                    exitCode = 0,
                    stdout = """
                        {
                          "type": "result",
                          "subtype": "success",
                          "is_error": false,
                          "result": "",
                          "structured_output": {"answer": "ok"},
                          "usage": {"input_tokens": 120, "output_tokens": 15}
                        }
                    """.trimIndent(),
                    stderr = "",
                )
            },
        )

        val request = ChatRequest.builder()
            .messages(
                SystemMessage.from("You are concise."),
                UserMessage.from("Say ok"),
            )
            .responseFormat(
                ResponseFormat.builder()
                    .type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON)
                    .jsonSchema(
                        JsonSchema.builder()
                            .name("Reply")
                            .rootElement(
                                JsonObjectSchema.builder()
                                    .addStringProperty("answer")
                                    .required("answer")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val response = model.chat(request)

        assertThat(capturedArgs).containsSubsequence("claude", "-p", "--output-format", "json")
        assertThat(capturedArgs).contains("--no-session-persistence", "--model", "sonnet", "--system-prompt", "You are concise.")
        assertThat(capturedArgs).contains("--json-schema")
        assertThat(capturedStdin).isEqualTo("Say ok")
        assertThat(response.aiMessage().text()).isEqualTo("""{"answer":"ok"}""")
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isEqualTo(120)
        assertThat(response.metadata().tokenUsage().outputTokenCount()).isEqualTo(15)
    }

    @Test
    fun `falls back to result text when structured_output is absent`() {
        val model = ClaudeCliChatModel(
            command = "claude",
            modelName = "sonnet",
            timeout = Duration.ofSeconds(30),
            strictSchema = false,
            listeners = emptyList(),
            json = json,
            runner = { _, _, _ ->
                CliProcessResult(
                    exitCode = 0,
                    stdout = """{"is_error": false, "result": "Plain answer", "usage": {"input_tokens": 10, "output_tokens": 5}}""",
                    stderr = "",
                )
            },
        )

        val response = model.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())

        assertThat(response.aiMessage().text()).isEqualTo("Plain answer")
    }

    @Test
    fun `throws when claude cli exits non-zero or reports is_error`() {
        val nonZero = ClaudeCliChatModel(
            command = "claude",
            modelName = "sonnet",
            timeout = Duration.ofSeconds(30),
            strictSchema = true,
            listeners = emptyList(),
            json = json,
            runner = { _, _, _ -> CliProcessResult(exitCode = 1, stdout = "", stderr = "not logged in") },
        )

        assertThatThrownBy {
            nonZero.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not logged in")

        val isError = ClaudeCliChatModel(
            command = "claude",
            modelName = "sonnet",
            timeout = Duration.ofSeconds(30),
            strictSchema = true,
            listeners = emptyList(),
            json = json,
            runner = { _, _, _ ->
                CliProcessResult(exitCode = 0, stdout = """{"is_error": true, "result": "quota exceeded"}""", stderr = "")
            },
        )

        assertThatThrownBy {
            isError.chat(ChatRequest.builder().messages(UserMessage.from("Hi")).build())
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("quota exceeded")
    }
}
