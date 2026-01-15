package com.asakii.ai.agent.sdk

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.ThreadContextElement

/**
 * Coroutine-safe provider context for MCP system prompt building.
 */
object McpSystemPromptContext {
    private val providerLocal = ThreadLocal<AiAgentProvider?>()

    fun getProvider(): AiAgentProvider? = providerLocal.get()

    fun asContextElement(provider: AiAgentProvider?): ThreadContextElement<AiAgentProvider?> {
        return providerLocal.asContextElement(provider)
    }
}
