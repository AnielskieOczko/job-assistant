package com.jankowski.rafal.jobassistant.llm

import dev.langchain4j.model.chat.ChatModel

/**
 * Supplies the model to use for a task. Public because substituting the model source is a
 * supported thing to do: tests replace it with a scripted model so everything downstream of the
 * LLM can be exercised deterministically and without a network call.
 *
 * A replacement should attach [ChatModelAuditing.listenerFor] to the models it builds, otherwise
 * its calls go unrecorded.
 */
interface ChatModelRegistry {

    fun forTask(task: LlmTask): ChatModel

    /** Name of the configured profile serving [task]; recorded on every audit row. */
    fun profileNameFor(task: LlmTask): String
}
