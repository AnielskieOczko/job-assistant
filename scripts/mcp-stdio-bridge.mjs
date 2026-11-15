#!/usr/bin/env node
/**
 * Zero-dependency stdio-to-HTTP JSON-RPC 2.0 bridge for job-assistant MCP (/api/mcp).
 * Allows stdio-only MCP clients (Claude Desktop, Antigravity, etc.) to call the
 * local Spring Boot server using interactive Claude / Gemini subscriptions.
 */
import readline from 'node:readline'

const endpoint = process.env.JOB_ASSISTANT_MCP_URL ?? 'http://127.0.0.1:8080/api/mcp'

const rl = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity,
})

rl.on('line', async (line) => {
  const trimmed = line.trim()
  if (!trimmed) return

  let request
  try {
    request = JSON.parse(trimmed)
  } catch (err) {
    process.stdout.write(
      `${JSON.stringify({ jsonrpc: '2.0', id: null, error: { code: -32700, message: err.message } })}\n`,
    )
    return
  }

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
    const text = await res.text()
    if (request.id !== undefined && request.id !== null && text.trim()) {
      process.stdout.write(`${text.trim()}\n`)
    }
  } catch (err) {
    if (request.id !== undefined && request.id !== null) {
      process.stdout.write(
        `${JSON.stringify({
          jsonrpc: '2.0',
          id: request.id,
          error: { code: -32000, message: `MCP bridge error: ${err.message}` },
        })}\n`,
      )
    }
  }
})
