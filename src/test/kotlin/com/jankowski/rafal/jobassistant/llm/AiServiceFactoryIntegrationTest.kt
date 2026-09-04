package com.jankowski.rafal.jobassistant.llm

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import dev.langchain4j.guardrail.OutputGuardrailException
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real AiServices wiring - schema-shaped output, the repair guardrail and the audit
 * trail - against a scripted model. Everything is real except the HTTP call.
 */
@IntegrationTest
class AiServiceFactoryIntegrationTest(
    @Autowired private val factory: AiServiceFactory,
    @Autowired private val models: ScriptedModels,
    @Autowired private val callLog: LlmCallLog,
    @Autowired private val jdbc: JdbcClient,
) {

    /**
     * Every property carries a default so Kotlin emits a no-arg constructor. LangChain4j
     * deserialises service return types reflectively without the Jackson Kotlin module, so a data
     * class with required constructor parameters fails at runtime rather than compile time.
     */
    data class SkillSummary(
        val skill: String = "",
        val confidence: Int = 0,
        val aliases: List<String> = emptyList(),
    )

    interface SummaryService {
        @SystemMessage("You summarise skills.")
        @UserMessage("Summarise {{term}}")
        fun summarise(@V("term") term: String): SkillSummary
    }

    private fun service() = factory.create(SummaryService::class.java, LlmTask.EXTRACTION)

    @BeforeEach
    fun reset() {
        models.resetAll()
        jdbc.sql("delete from llm_call").update()
    }

    @Test
    fun `parses a JSON response into a Kotlin data class`() {
        models[LlmTask.EXTRACTION].enqueue(
            """{"skill":"Kotlin","confidence":9,"aliases":["kt"]}"""
        )

        val result = service().summarise("Kotlin")

        assertEquals("Kotlin", result.skill)
        assertEquals(9, result.confidence)
        assertEquals(listOf("kt"), result.aliases)
    }

    @Test
    fun `repairs a markdown-fenced response without a second model call`() {
        models[LlmTask.EXTRACTION].enqueue(
            "```json\n{\"skill\":\"Kotlin\",\"confidence\":7,\"aliases\":[]}\n```"
        )

        val result = service().summarise("Kotlin")

        assertEquals("Kotlin", result.skill)
        assertEquals(
            1,
            models[LlmTask.EXTRACTION].requests.size,
            "fence stripping must not cost an extra round trip",
        )
    }

    /**
     * This pair of tests used to assert that prose was reprompted and the second answer accepted,
     * and they passed for a reason that had nothing to do with the code: a scripted model returns
     * the next queued reply whatever it is asked, so they queued a correct answer to a question the
     * model never received. In production that second request carried the correction instruction
     * and **nothing else** - no system prompt, no original request - because a guardrail reprompt
     * is assembled from a chat memory these services do not have. The model duly answered the only
     * question it could see, and its reply reached a user as a polished project description.
     *
     * The lesson is in the assertion, not only in the behaviour: *never assert that a repair
     * happened without asserting what the repair asked.*
     */
    @Test
    fun `prose instead of JSON fails the call rather than being asked again`() {
        models[LlmTask.EXTRACTION].enqueue(
            "I'm afraid I can't do that.",
            """{"skill":"Kotlin","confidence":5,"aliases":[]}""",
        )

        val refused = assertFailsWith<OutputGuardrailException> { service().summarise("Kotlin") }

        assertTrue(refused.message!!.contains("JSON"), "the caller has to be able to say what happened")
        assertEquals(
            1,
            models[LlmTask.EXTRACTION].requests.size,
            "a second question nobody can answer is worse than none",
        )
    }

    @Test
    fun `a silent answer is asked again, carrying the original request`() {
        models[LlmTask.EXTRACTION].enqueue("", """{"skill":"Kotlin","confidence":5,"aliases":[]}""")

        val result = service().summarise("Kotlin coroutines")

        assertEquals("Kotlin", result.skill)
        assertEquals(2, models[LlmTask.EXTRACTION].requests.size, "expected exactly one retry")

        val retry = models[LlmTask.EXTRACTION].requests.last().messages().joinToString { it.toString() }
        assertTrue(retry.contains("Summarise Kotlin coroutines"), "the retry lost the question")
        assertTrue(retry.contains("You summarise skills."), "the retry lost the system prompt")
    }

    @Test
    fun `a model silent twice fails rather than answering something else`() {
        models[LlmTask.EXTRACTION].enqueue("", "")

        assertFailsWith<OutputGuardrailException> { service().summarise("Kotlin") }

        assertEquals(2, models[LlmTask.EXTRACTION].requests.size, "one retry, not a loop")
    }

    @Test
    fun `the user message is rendered from the template variable`() {
        models[LlmTask.EXTRACTION].enqueue("""{"skill":"Kotlin","confidence":1,"aliases":[]}""")

        service().summarise("Kotlin coroutines")

        val sent = models[LlmTask.EXTRACTION].requests.single().messages().joinToString { it.toString() }
        assertTrue(sent.contains("Summarise Kotlin coroutines"))
        assertTrue(sent.contains("You summarise skills."))
    }

    @Test
    fun `every call is audited with task, tokens and latency`() {
        models[LlmTask.EXTRACTION].enqueue("""{"skill":"Kotlin","confidence":1,"aliases":[]}""")

        service().summarise("Kotlin")

        val call = callLog.recent().single()
        assertEquals("EXTRACTION", call.task)
        assertEquals("scripted", call.modelProfile)
        assertEquals("scripted-model", call.modelName)
        assertEquals(11, call.inputTokens)
        assertEquals(22, call.outputTokens)
        assertNotNull(call.latencyMs)
        assertNull(call.error)
    }

    @Test
    fun `the audit detail keeps the prompt and the raw response for debugging`() {
        models[LlmTask.EXTRACTION].enqueue("""{"skill":"Kotlin","confidence":3,"aliases":[]}""")

        service().summarise("Kotlin")

        val detail = assertNotNull(callLog.detail(callLog.recent().single().id))
        assertTrue(detail.requestJson.contains("Summarise Kotlin"))
        assertTrue(assertNotNull(detail.responseText).contains("\"skill\":\"Kotlin\""))
    }

    /** Two rows for one logical call is the honest record: it cost two. */
    @Test
    fun `a retried call records both round trips`() {
        models[LlmTask.EXTRACTION].enqueue("", """{"skill":"Kotlin","confidence":1,"aliases":[]}""")

        service().summarise("Kotlin")

        assertEquals(2, callLog.recent().size)
    }

    @Test
    fun `a model failure is recorded rather than lost`() {
        models[LlmTask.EXTRACTION].enqueueFailure(RuntimeException("upstream 502"))

        runCatching { service().summarise("Kotlin") }

        val call = callLog.recent().single()
        assertTrue(assertNotNull(call.error).contains("upstream 502"))
        assertNull(call.outputTokens)
    }

    @Test
    fun `each task gets its own model so audit rows name the right one`() {
        models[LlmTask.NARRATIVE].enqueue("""{"skill":"Prose","confidence":1,"aliases":[]}""")

        factory.create(SummaryService::class.java, LlmTask.NARRATIVE).summarise("anything")

        assertEquals("NARRATIVE", callLog.recent().single().task)
    }
}
