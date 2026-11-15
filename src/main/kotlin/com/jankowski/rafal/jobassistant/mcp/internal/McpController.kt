package com.jankowski.rafal.jobassistant.mcp.internal

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/api/mcp")
internal class McpController(
    private val tools: JobAssistantMcpTools,
) {

    @PostMapping
    fun handle(@RequestBody request: JsonRpcRequest): Map<String, Any?> {
        val result: Any = when (request.method) {
            "initialize" -> mapOf(
                "protocolVersion" to "2024-11-05",
                "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                "serverInfo" to mapOf("name" to "job-assistant", "version" to "1.0.0"),
            )
            "notifications/initialized" -> emptyMap<String, Any>()
            "tools/list" -> mapOf("tools" to tools.listTools())
            "tools/call" -> {
                val params = request.params
                val name = params?.get("name")?.takeIf { !it.isNull }?.asString()
                    ?: return rpcError(request.id, -32602, "Missing tool name")
                val arguments = params.get("arguments")
                try {
                    tools.call(name, arguments)
                } catch (ex: IllegalArgumentException) {
                    McpToolResult(
                        content = listOf(McpTextContent(text = ex.message ?: "Invalid tool arguments")),
                        isError = true,
                    )
                }
            }
            else -> return rpcError(request.id, -32601, "Method not found: ${request.method}")
        }
        return mapOf("jsonrpc" to "2.0", "id" to request.id, "result" to result)
    }

    private fun rpcError(id: JsonNode?, code: Int, message: String): Map<String, Any?> = mapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "error" to mapOf("code" to code, "message" to message),
    )
}

internal data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonNode? = null,
    val method: String,
    val params: JsonNode? = null,
)
