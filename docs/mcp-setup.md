# Subscription LLM & MCP Setup

`job-assistant` supports two complementary ways to use your interactive **Claude** and **Gemini** subscriptions with stronger privacy:

1. **In-app `claude-cli` transport (`claude -p`)** — routes `CAREER_PLAN` and `LINKEDIN` (or any `LlmTask`) through your local Claude CLI without an API key.
2. **PII-safe MCP Server (`POST /api/mcp`)** — exposes 5 tools (`get_target_role`, `get_market_demand`, `get_career_plan`, `list_linkedin_drafts`, `draft_linkedin_section`) with no direct identifiers (`full_name`, `email`, `phone`, links).

---

## 1. Enable `claude-cli` for in-app tasks

Start the app with task overrides (or set them in `application-dev.yaml` / `application.yaml`):

```bash
JOB_ASSISTANT_LLM_TASKS_CAREER_PLAN=claude-cli \
JOB_ASSISTANT_LLM_TASKS_LINKEDIN=claude-cli \
./mvnw spring-boot:run
```

---

## 2. Connect Claude Desktop / Antigravity / Claude Code via MCP

For clients that support **Streamable HTTP / HTTP MCP** directly:
- URL: `http://127.0.0.1:8080/api/mcp`

For **stdio-only** MCP clients, use [`scripts/mcp-stdio-bridge.mjs`](../scripts/mcp-stdio-bridge.mjs):

```json
{
  "mcpServers": {
    "job-assistant": {
      "command": "node",
      "args": ["/Volumes/my-data/Developer/Projects/job-assistant/scripts/mcp-stdio-bridge.mjs"],
      "env": {
        "JOB_ASSISTANT_MCP_URL": "http://127.0.0.1:8080/api/mcp"
      }
    }
  }
}
```
