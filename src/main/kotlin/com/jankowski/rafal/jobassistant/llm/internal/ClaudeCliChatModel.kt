package com.jankowski.rafal.jobassistant.llm.internal

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.internal.JsonSchemaElementUtils
import dev.langchain4j.model.chat.Capability
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.ChatResponseMetadata
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.model.output.TokenUsage
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

internal data class CliProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

internal typealias CliProcessRunner = (args: List<String>, stdin: String, timeout: Duration) -> CliProcessResult

/**
 * [ChatModel] backed by the local `claude -p` CLI so the portal can route tasks to an
 * authenticated Claude subscription without a separate API key.
 *
 * Implements [doChat] and exposes [listeners] so LangChain4j's `ChatModel.chat(...)` template
 * invokes `AuditingChatModelListener` and `InspectingChatModel` enforces `PromptPrivacyInvariant`
 * identically to OpenAI-compatible HTTP profiles.
 */
internal class ClaudeCliChatModel(
    private val command: String,
    private val modelName: String,
    private val timeout: Duration,
    private val strictSchema: Boolean,
    private val listeners: List<ChatModelListener>,
    private val json: JsonMapper,
    private val runner: CliProcessRunner = ::runProcess,
) : ChatModel {

    override fun listeners(): List<ChatModelListener> = listeners

    override fun supportedCapabilities(): Set<Capability> =
        if (strictSchema) setOf(Capability.RESPONSE_FORMAT_JSON_SCHEMA) else emptySet()

    override fun doChat(chatRequest: ChatRequest): ChatResponse {
        val systemText = chatRequest.messages()
            .filterIsInstance<SystemMessage>()
            .joinToString("\n\n") { it.text() }
            .trim()
            .takeIf { it.isNotEmpty() }

        val userText = chatRequest.messages()
            .filterNot { it is SystemMessage }
            .joinToString("\n\n") { msg ->
                when (msg) {
                    is UserMessage -> msg.singleText()
                    else -> msg.toString()
                }
            }

        val args = buildList {
            add(command)
            add("-p")
            add("--output-format")
            add("json")
            add("--no-session-persistence")
            add("--tools")
            add("")
            add("--model")
            add(modelName)
            if (systemText != null) {
                add("--system-prompt")
                add(systemText)
            }
            val rf = chatRequest.responseFormat()
            if (strictSchema && rf?.type() == ResponseFormatType.JSON && rf.jsonSchema() != null) {
                val schemaMap = JsonSchemaElementUtils.toMap(rf.jsonSchema().rootElement())
                add("--json-schema")
                add(json.writeValueAsString(schemaMap))
            }
        }

        val result = runner(args, userText, timeout)
        if (result.exitCode != 0) {
            val detail = result.stderr.ifBlank { result.stdout }.trim()
            throw IllegalStateException("claude CLI exited with ${result.exitCode}: $detail")
        }

        val root = json.readTree(result.stdout)
        if (root.path("is_error").asBoolean(false)) {
            val err = root.path("result").asString("")
            throw IllegalStateException("claude CLI error: $err")
        }

        val structured = root.get("structured_output")
        val content = if (structured != null && !structured.isNull) {
            if (structured.isString) structured.asString() else json.writeValueAsString(structured)
        } else {
            root.path("result").asString("")
        }

        val usageNode = root.path("usage")
        val inputTokens = usageNode.path("input_tokens").asInt(0)
        val outputTokens = usageNode.path("output_tokens").asInt(0)

        return ChatResponse.builder()
            .aiMessage(AiMessage.from(content))
            .metadata(
                ChatResponseMetadata.builder()
                    .modelName(modelName)
                    .tokenUsage(TokenUsage(inputTokens, outputTokens))
                    .finishReason(FinishReason.STOP)
                    .build()
            )
            .build()
    }

    private companion object {
        fun runProcess(args: List<String>, stdin: String, timeout: Duration): CliProcessResult {
            val process = try {
                ProcessBuilder(args).start()
            } catch (e: IOException) {
                throw IllegalStateException("Failed to start '${args.firstOrNull()}': ${e.message}", e)
            }
            process.outputStream.bufferedWriter().use { it.write(stdin) }
            val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw IllegalStateException("claude CLI timed out after $timeout")
            }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            return CliProcessResult(process.exitValue(), stdout, stderr)
        }
    }
}
