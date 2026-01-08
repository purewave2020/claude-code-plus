package com.asakii.ai.agent.sdk

import com.asakii.ai.agent.sdk.adapter.ClaudeStreamAdapter
import com.asakii.ai.agent.sdk.adapter.UiStreamAdapter
import com.asakii.ai.agent.sdk.model.NormalizedStreamEvent
import com.asakii.ai.agent.sdk.model.UiStreamEvent
import com.asakii.claude.agent.sdk.types.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 统一的流式适配器入口，将底层 SDK 的流转换成 UI 可直接渲染的事件。
 * 注意：Codex 使用 CodexAppServerStreamAdapter，不经过此 Bridge。
 */
class AiAgentStreamBridge {
    private val claudeAdapter = ClaudeStreamAdapter()
    private val uiAdapter = UiStreamAdapter()

    fun fromClaude(messages: Flow<Message>): Flow<UiStreamEvent> =
        toUiEvents(normalizeClaude(messages))

    fun normalizeClaude(messages: Flow<Message>): Flow<NormalizedStreamEvent> = flow {
        messages.collect { message ->
            claudeAdapter.convert(message).forEach { emit(it) }
        }
    }

    fun toUiEvents(events: Flow<NormalizedStreamEvent>): Flow<UiStreamEvent> = flow {
        events.collect { normalized ->
            uiAdapter.convert(normalized).forEach { emit(it) }
        }
    }
}



































