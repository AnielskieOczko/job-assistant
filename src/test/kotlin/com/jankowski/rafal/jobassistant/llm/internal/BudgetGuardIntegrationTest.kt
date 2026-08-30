package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.BudgetExceededException
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The cap is enforced where every call already passes, so no future caller can forget it.
 *
 * The guard is exercised through the real `AiServiceFactory` wiring rather than by calling the
 * inspector directly, because the property under test is not "the arithmetic is right" but "the
 * refusal happens above the listener pipeline" - and that only holds if `InspectingChatModel` is
 * actually in the path.
 */
@IntegrationTest
@TestPropertySource(properties = ["job-assistant.llm.budget.daily-usd=0.01"])
internal class BudgetGuardIntegrationTest(
    @Autowired private val factory: AiServiceFactory,
    @Autowired private val auditor: LlmCallAuditor,
    @Autowired private val models: ScriptedModels,
    @Autowired private val jdbc: JdbcClient,
) {

    /** Every property defaulted, as LangChain4j's reflective deserialisation requires. */
    data class Answer(val text: String = "")

    interface Asker {
        @UserMessage("Say {{word}}")
        fun ask(@V("word") word: String): Answer
    }

    @BeforeEach
    fun reset() {
        models.resetAll()
        jdbc.sql("delete from llm_call").update()
        jdbc.sql("delete from llm_spend_daily").update()
    }

    private fun spendToday(usd: String) = auditor.record(
        LlmCallAuditor.AuditEntry(
            task = "EXTRACTION", modelProfile = "openrouter", modelName = "m",
            requestJson = "[]", responseText = "{}", error = null,
            costUsd = BigDecimal(usd), inputTokens = 1, outputTokens = 1,
            latencyMs = 1, profileId = null,
        )
    )

    private fun callCount() =
        jdbc.sql("select count(*) from llm_call").query(Int::class.java).single()

    @Test
    fun `a call under the cap goes through`() {
        spendToday("0.004")
        models[LlmTask.EXTRACTION].enqueue("""{"text":"hello"}""")

        assertEquals("hello", factory.create(Asker::class.java, LlmTask.EXTRACTION).ask("hello").text)
    }

    /**
     * Nothing is sent and nothing is audited: the inspector runs above the listener pipeline, which
     * is why the exception has to name the period, the cap and the total itself - there will be no
     * row to look at afterwards.
     */
    @Test
    fun `a call over the cap is refused before it is sent, and leaves no audit row`() {
        spendToday("0.011")
        val before = callCount()
        models[LlmTask.EXTRACTION].enqueue("""{"text":"never reached"}""")

        val failure = runCatching {
            factory.create(Asker::class.java, LlmTask.EXTRACTION).ask("hello")
        }.exceptionOrNull()

        val refusal = generateSequence(failure) { it.cause }
            .filterIsInstance<BudgetExceededException>()
            .firstOrNull()

        assertNotNull(refusal, "expected a BudgetExceededException, got $failure")
        assertEquals("daily", refusal.period)
        assertTrue(refusal.message!!.contains("0.011"), "refusal must name the running total")
        assertEquals(before, callCount())
    }

    /** Exactly at the cap is spent, not "nearly spent" - the next call is the one over the line. */
    @Test
    fun `reaching the cap exactly is already too much`() {
        spendToday("0.01")
        models[LlmTask.EXTRACTION].enqueue("""{"text":"never reached"}""")

        assertTrue(
            runCatching { factory.create(Asker::class.java, LlmTask.EXTRACTION).ask("x") }.isFailure
        )
    }
}
