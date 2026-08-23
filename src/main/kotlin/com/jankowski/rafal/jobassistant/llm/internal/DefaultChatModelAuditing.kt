package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.ChatModelAuditing
import com.jankowski.rafal.jobassistant.llm.LlmTask
import dev.langchain4j.model.chat.listener.ChatModelListener
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
internal class DefaultChatModelAuditing(
    private val auditor: LlmCallAuditor,
    private val json: JsonMapper,
) : ChatModelAuditing {

    override fun listenerFor(task: LlmTask, profileName: String): ChatModelListener =
        AuditingChatModelListener(task, profileName, auditor, json)
}
