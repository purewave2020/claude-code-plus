package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider

object UnifiedAgentClientFactory {
    fun create(
        provider: AiAgentProvider,
        permissionRequester: PermissionRequester? = null
    ): UnifiedAgentClient = when (provider) {
        AiAgentProvider.CLAUDE -> ClaudeAgentClientImpl()
        AiAgentProvider.CODEX -> CodexAgentClientImpl(permissionRequester)
    }
}



































