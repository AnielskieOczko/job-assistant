package com.jankowski.rafal.jobassistant.llm

import java.math.BigDecimal
import java.time.Instant

/** One recorded model call. */
data class LlmCall(
    val id: Long,
    val task: String,
    val modelProfile: String,
    val modelName: String?,
    /**
     * The upstream provider that served the call, when a router reports one.
     *
     * A router can serve one model slug from providers with different capabilities, so this is
     * what makes a bad result attributable: the model name is identical whether the request was
     * constrained to a JSON schema or answered by a provider that discarded it.
     */
    val servingProvider: String?,
    /**
     * The provider's own id for this generation.
     *
     * Present so a row here can be matched against a line on the provider's billing dashboard
     * without inference. Null for anything that does not report one.
     */
    val providerCallId: String?,
    /**
     * What the account was charged, in the provider's own billing unit.
     *
     * Null is not zero. It means the provider reported no price - a local model, or an
     * OpenAI-compatible endpoint with no such extension - and any total built from these must say
     * how many of its calls were priced at all.
     */
    val costUsd: BigDecimal?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    /** The part of [inputTokens] served from a prompt cache, and billed at a discount. */
    val cachedInputTokens: Int?,
    /** The part of [outputTokens] spent reasoning. Paid for, and absent from the response text. */
    val reasoningOutputTokens: Int?,
    /** Why the model stopped. Anything but `STOP` is worth looking at; `LENGTH` means truncated. */
    val finishReason: String?,
    val latencyMs: Long?,
    val error: String?,
    /** What caused this call, as an opaque label - `"OFFER"` today. */
    val subjectKind: String?,
    val subjectId: Long?,
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
