package com.asakii.ai.agent.sdk.client

import kotlinx.serialization.json.JsonElement

data class PermissionRequest(
    val toolName: String,
    val inputJson: JsonElement,
    val toolUseId: String? = null
)

data class PermissionDecision(
    val approved: Boolean,
    val denyReason: String? = null
)

fun interface PermissionRequester {
    suspend fun requestPermission(request: PermissionRequest): PermissionDecision
}
