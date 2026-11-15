package com.jankowski.rafal.jobassistant.career.internal

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

internal interface CareerPlanAuthor {

    @SystemMessage(fromResource = "/prompts/career-plan-system.md")
    @UserMessage(fromResource = "/prompts/career-plan-user.md")
    fun author(
        @V("targetRole") targetRole: String,
        @V("careerGoal") careerGoal: String,
        @V("evidence") evidence: String,
    ): AuthoredCareerPlan
}

internal data class AuthoredCareerPlan(
    val topics: List<AuthoredTopic> = emptyList(),
)

internal data class AuthoredTopic(
    val skill: String = "",
    val priority: Int = 1,
    val why: String = "",
    val practiceProject: String? = null,
    val effortEstimate: String? = null,
)

@Suppress("USELESS_ELVIS")
internal fun AuthoredCareerPlan.safeTopics(): List<AuthoredTopic> = topics ?: emptyList()

@Suppress("USELESS_ELVIS")
internal fun AuthoredTopic.safeSkill(): String = (skill ?: "").trim()

@Suppress("USELESS_ELVIS")
internal fun AuthoredTopic.safeWhy(): String = (why ?: "").trim()
