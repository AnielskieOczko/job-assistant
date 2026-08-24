package com.jankowski.rafal.jobassistant.llm

import java.time.Instant

/** One recorded model call. */
data class LlmCall(
    val id: Long,
    val task: String,
    val modelProfile: String,
    val modelName: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val latencyMs: Long?,
    val error: String?,
    val createdAt: Instant,
)

interface LlmCallLog {
    fun recent(limit: Int = 50): List<LlmCall>

    /** Full request and response text for one call - the debugging path. */
    fun detail(id: Long): LlmCallDetail?
}

data class LlmCallDetail(
    val call: LlmCall,
    val requestJson: String,
    val responseText: String?,
)
