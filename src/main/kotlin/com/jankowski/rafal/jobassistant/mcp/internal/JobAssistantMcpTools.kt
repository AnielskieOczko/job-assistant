package com.jankowski.rafal.jobassistant.mcp.internal

import com.jankowski.rafal.jobassistant.career.CareerService
import com.jankowski.rafal.jobassistant.linkedin.LinkedInDraftService
import com.jankowski.rafal.jobassistant.linkedin.LinkedInSection
import com.jankowski.rafal.jobassistant.market.DemandRanking
import com.jankowski.rafal.jobassistant.market.MarketInsights
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProfileTargetRole
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * PII-safe MCP tool definitions and dispatcher for external subscription clients
 * (Claude Desktop, Claude Code, Antigravity). Only exposes skills, seniority,
 * market demand counts, career plans, and LinkedIn drafts.
 */
@Component
internal class JobAssistantMcpTools(
    private val profiles: ProfileService,
    private val targetRoles: ProfileTargetRole,
    private val market: MarketInsights,
    private val career: CareerService,
    private val linkedIn: LinkedInDraftService,
    private val mapper: JsonMapper,
) {

    fun listTools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "get_target_role",
            description = "Get the active profile's target role title, seniority, and target skills (no PII).",
            inputSchema = objectSchema(
                properties = mapOf("profileId" to integerProp("Optional profile ID; defaults to active profile.")),
            ),
        ),
        McpToolDefinition(
            name = "get_market_demand",
            description = "Get in-scope market skill demand ranked by unmet gaps or total demand.",
            inputSchema = objectSchema(
                properties = mapOf(
                    "profileId" to integerProp("Optional profile ID."),
                    "ranking" to stringProp("UNMET (default) or ALL."),
                    "limit" to integerProp("Maximum number of skill rows to return (default 20)."),
                ),
            ),
        ),
        McpToolDefinition(
            name = "get_career_plan",
            description = "Get the current evidence-bounded career learning plan and skill progress.",
            inputSchema = objectSchema(
                properties = mapOf("profileId" to integerProp("Optional profile ID.")),
            ),
        ),
        McpToolDefinition(
            name = "list_linkedin_drafts",
            description = "List saved copy-ready LinkedIn section drafts and their staleness status.",
            inputSchema = objectSchema(
                properties = mapOf("profileId" to integerProp("Optional profile ID.")),
            ),
        ),
        McpToolDefinition(
            name = "draft_linkedin_section",
            description = "Draft a copy-ready LinkedIn section (HEADLINE, ABOUT, EXPERIENCE_ENTRY, PROJECT_ENTRY, SKILL_OR_CREDENTIAL_POST).",
            inputSchema = objectSchema(
                properties = mapOf(
                    "profileId" to integerProp("Optional profile ID."),
                    "section" to stringProp("Target LinkedIn section enum name."),
                    "subjectId" to integerProp("Optional experience or project ID for entry drafts."),
                    "notes" to stringProp("Optional user guidance notes."),
                ),
                required = listOf("section"),
            ),
        ),
    )

    fun call(name: String, arguments: JsonNode?): McpToolResult {
        val args = arguments ?: mapper.createObjectNode()
        val profileId = args.get("profileId")?.takeIf { !it.isNull }?.asLong()
            ?: profiles.findDefaultProfileId()

        val payload: Any = when (name) {
            "get_target_role" -> {
                val role = targetRoles.of(profileId)
                mapOf("profileId" to profileId, "targetRole" to role)
            }
            "get_market_demand" -> {
                val ranking = args.get("ranking")?.takeIf { !it.isNull }?.asString()
                    ?.let { DemandRanking.valueOf(it.uppercase()) } ?: DemandRanking.UNMET
                val limit = args.get("limit")?.takeIf { !it.isNull }?.asInt() ?: 20
                market.demand(profileId = profileId, ranking = ranking, limit = limit)
            }
            "get_career_plan" -> {
                val plan = career.current(profileId)
                mapOf("profileId" to profileId, "plan" to plan)
            }
            "list_linkedin_drafts" -> {
                val pid = profileId ?: throw IllegalArgumentException("No default profile found.")
                mapOf("profileId" to pid, "drafts" to linkedIn.list(pid))
            }
            "draft_linkedin_section" -> {
                val pid = profileId ?: throw IllegalArgumentException("No default profile found.")
                val sectionName = args.get("section")?.takeIf { !it.isNull }?.asString()
                    ?: throw IllegalArgumentException("section is required.")
                val section = LinkedInSection.valueOf(sectionName.uppercase())
                val subjectId = args.get("subjectId")?.takeIf { !it.isNull }?.asLong()
                val notes = args.get("notes")?.takeIf { !it.isNull }?.asString()
                linkedIn.draft(profileId = pid, section = section, subjectId = subjectId, notes = notes)
            }
            else -> throw IllegalArgumentException("Unknown tool: $name")
        }

        return McpToolResult(
            content = listOf(McpTextContent(text = mapper.writeValueAsString(payload))),
            isError = false,
        )
    }

    private fun objectSchema(
        properties: Map<String, Map<String, Any>>,
        required: List<String> = emptyList(),
    ): Map<String, Any> = buildMap {
        put("type", "object")
        put("properties", properties)
        if (required.isNotEmpty()) put("required", required)
    }

    private fun integerProp(description: String): Map<String, Any> =
        mapOf("type" to "integer", "description" to description)

    private fun stringProp(description: String): Map<String, Any> =
        mapOf("type" to "string", "description" to description)
}

internal data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>,
)

internal data class McpTextContent(
    val type: String = "text",
    val text: String,
)

internal data class McpToolResult(
    val content: List<McpTextContent>,
    val isError: Boolean,
)
