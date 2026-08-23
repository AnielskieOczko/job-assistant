package com.jankowski.rafal.jobassistant.llm

import dev.langchain4j.model.chat.listener.ChatModelListener

/**
 * Produces the listener that records every model call to `llm_call`.
 *
 * Exposed so that an alternative [ChatModelRegistry] can keep the audit trail intact rather than
 * silently losing it.
 */
interface ChatModelAuditing {

    fun listenerFor(task: LlmTask, profileName: String): ChatModelListener
}
