package com.jankowski.rafal.jobassistant.support

import com.jankowski.rafal.jobassistant.llm.ChatModelAuditing
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmTask
import dev.langchain4j.model.chat.ChatModel
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Replaces every configured model with a [ScriptedChatModel] while keeping the real audit
 * listener attached. Tests queue the exact JSON a model would return and then assert on
 * everything downstream: parsing, the deterministic diff, the CV invariant.
 */
@TestConfiguration(proxyBeanMethods = false)
class StubLlmConfiguration {

    @Bean
    @Primary
    fun scriptedModels(auditing: ChatModelAuditing): ScriptedModels = ScriptedModels(auditing)
}

/** Holds one scripted model per task so a test can script each task independently. */
class ScriptedModels(auditing: ChatModelAuditing) : ChatModelRegistry {

    private val byTask = LlmTask.entries.associateWith { task ->
        ScriptedChatModel(listeners = listOf(auditing.listenerFor(task, PROFILE_NAME)))
    }

    operator fun get(task: LlmTask): ScriptedChatModel = byTask.getValue(task)

    fun resetAll() = byTask.values.forEach { it.reset() }

    override fun forTask(task: LlmTask): ChatModel = byTask.getValue(task)

    override fun profileNameFor(task: LlmTask): String = PROFILE_NAME

    private companion object {
        const val PROFILE_NAME = "scripted"
    }
}
