package com.jankowski.rafal.jobassistant.llm

/**
 * Vetoes an outgoing prompt before it reaches a provider.
 *
 * The point of the indirection is that this module stays ignorant: it knows only that something
 * may object to a prompt, never what a profile is or why a particular string matters. Whoever
 * cares registers a bean and throws.
 *
 * Every implementation is consulted on every model call, regardless of which provider serves the
 * task. A "local" model profile is a base URL like any other and could be repointed at a remote
 * host by a one-line config edit, so there is deliberately no way to opt a provider out.
 */
interface OutboundPromptInspector {

    /**
     * @param renderedPrompt every message of the request - system and user - concatenated.
     * @throws RuntimeException to refuse the call. Nothing is sent and nothing is audited.
     */
    fun inspect(renderedPrompt: String)
}
