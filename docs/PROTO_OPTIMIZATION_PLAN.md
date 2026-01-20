# Proto 数据模型优化方案

## 1. 问题分析

### 当前架构痛点

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│  Proto 定义     │      │  RpcModels.kt   │      │ ProtoConverter  │
│  (.proto)       │ ───> │  (data class)   │ ───> │  (.kt)          │
│  954 行         │      │  752 行         │      │  553 行         │
└─────────────────┘      └─────────────────┘      └─────────────────┘
         │                       │                        │
         └───────────────────────┴────────────────────────┘
                    每次添加字段需要改 3 处！
```

- **60+ 重复定义的类**
- **52 个映射函数**
- **添加 1 个字段 = 修改 3 处代码**（如 connectId 遗漏问题）

---

## 2. 目标架构

```
┌─────────────────┐      ┌─────────────────┐
│  Proto 定义     │ ───> │  Proto 生成类   │  ← 唯一数据源
│  (.proto)       │      │  + 扩展属性     │
└─────────────────┘      └─────────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
            ┌───────────────┐       ┌───────────────┐
            │ 后端业务逻辑   │       │ 前端 JSON     │
            │ (直接使用)    │       │ (轻量适配器)  │
            └───────────────┘       └───────────────┘
```

- **添加 1 个字段 = 只改 proto 文件**
- 删除 RpcModels.kt 中的重复定义
- 删除 ProtoConverter.kt 中的大部分映射

---

## 3. 实施方案

### Phase 1: 启用 Proto Kotlin DSL（低风险）

**目标**: 让 Proto 生成更友好的 Kotlin 代码

**步骤**:

1. 更新 `ai-agent-proto/build.gradle.kts`:

```kotlin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        id("kotlin") {
            artifact = "com.google.protobuf:protoc-gen-kotlin:1.4.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("kotlin")  // 生成 Kotlin DSL
            }
        }
    }
}
```

2. Proto 生成类将支持:
   - DSL 构建器: `connectOptions { model = "opus" }`
   - Null 安全访问: `options.modelOrNull`

---

### Phase 2: 添加扩展属性层（低风险）

**目标**: 为 Proto 类提供友好的 Kotlin API，避免 `hasXxx()` 检查

**创建文件**: `ai-agent-server/src/main/kotlin/com/asakii/server/proto/ProtoExtensions.kt`

```kotlin
package com.asakii.server.proto

import com.asakii.proto.rpc.*

// ==================== ConnectOptions 扩展 ====================

/** 获取 model，如果未设置则返回 null */
val ConnectOptions.modelOrNull: String?
    get() = if (hasModel()) model else null

/** 获取 connectId，如果未设置则返回 null */
val ConnectOptions.connectIdOrNull: String?
    get() = if (hasConnectId()) connectId else null

/** 获取 provider，如果未设置则返回默认值 */
val ConnectOptions.providerOrDefault: Provider
    get() = if (hasProvider()) provider else Provider.PROVIDER_CLAUDE

// ... 其他字段

// ==================== 批量转换扩展 ====================

/** 将所有 optional 字段转换为 nullable 的 Map */
fun ConnectOptions.toNullableMap(): Map<String, Any?> = mapOf(
    "provider" to providerOrNull,
    "model" to modelOrNull,
    "connectId" to connectIdOrNull,
    // ...
)

// ==================== Provider 枚举扩展 ====================

/** 转换为字符串标识 */
val Provider.stringValue: String
    get() = when (this) {
        Provider.PROVIDER_CLAUDE -> "claude"
        Provider.PROVIDER_CODEX -> "codex"
        else -> "unknown"
    }

/** 从字符串创建 Provider */
fun Provider.Companion.fromString(value: String): Provider = when (value) {
    "claude" -> Provider.PROVIDER_CLAUDE
    "codex" -> Provider.PROVIDER_CODEX
    else -> Provider.PROVIDER_UNSPECIFIED
}
```

---

### Phase 3: 重构 RPC 接口（中等风险）

**目标**: RPC 接口直接使用 Proto 类

**修改**: `ai-agent-rpc-api/src/main/kotlin/com/asakii/rpc/api/AiAgentRpcService.kt`

```kotlin
// 之前
interface AiAgentRpcService {
    suspend fun connect(options: RpcConnectOptions?): RpcConnectResult
}

// 之后 - 直接使用 Proto 类
interface AiAgentRpcService {
    suspend fun connect(options: ConnectOptions?): ConnectResult
}
```

**影响范围**:
- `AiAgentRpcServiceImpl.kt` - 修改方法签名
- `RSocketHandler.kt` - 简化序列化逻辑

---

### Phase 4: 删除冗余代码（中等风险）

**可删除的代码**:

| 文件 | 可删除内容 | 预计减少行数 |
|------|-----------|-------------|
| `RpcModels.kt` | 所有 `data class RpcXxx` | ~600 行 |
| `ProtoConverter.kt` | 所有 `toRpc()` 函数 | ~300 行 |
| `ProtoConverter.kt` | 大部分 `toProto()` 函数 | ~150 行 |

**保留的代码**:
- 前端 JSON 序列化适配器（如有需要）
- 枚举字符串转换

---

### Phase 5: 前端适配（可选）

**选项 A**: 前端继续使用 JSON，后端提供 Proto → JSON 转换

```kotlin
// 轻量级 JSON 适配器
object JsonAdapter {
    fun ConnectResult.toJson(): String = buildJsonObject {
        put("sessionId", sessionId)
        put("provider", provider.stringValue)
        // ...
    }.toString()
}
```

**选项 B**: 前端也使用 Protobuf（需要 protobuf.js）

```typescript
// 前端直接使用 Proto
import { ConnectOptions } from './proto/ai_agent_rpc_pb'

const options = new ConnectOptions()
options.setModel('opus')
options.setConnectId(tabId)
```

---

## 4. 实施顺序

```
Phase 1 ─────► Phase 2 ─────► Phase 3 ─────► Phase 4
  │              │              │              │
  │              │              │              │
  ▼              ▼              ▼              ▼
启用 Kotlin    添加扩展      重构接口       删除冗余
Proto DSL      属性层         签名          代码

风险: 低        风险: 低      风险: 中       风险: 中
时间: 1h        时间: 2h      时间: 4h       时间: 2h
```

---

## 5. 快速修复方案（立即可用）

如果不想大规模重构，可以先用**代码生成**减少手动映射：

### 自动生成映射代码的脚本

创建 `scripts/generate-proto-mappings.kt`:

```kotlin
// 从 proto 文件解析字段，自动生成 toRpc() 函数
fun generateToRpcFunction(protoMessage: String, fields: List<Field>): String {
    return """
    fun $protoMessage.toRpc(): Rpc$protoMessage = Rpc$protoMessage(
        ${fields.joinToString(",\n        ") { field ->
            "${field.name} = if (has${field.name.capitalize()}()) ${field.name}${field.conversion} else null"
        }}
    )
    """.trimIndent()
}
```

这样每次更新 proto 后，运行脚本自动生成映射代码。

---

## 6. 收益预估

| 指标 | 当前 | 优化后 | 改善 |
|------|------|--------|------|
| 添加字段修改处 | 3 处 | 1 处 | -67% |
| 数据模型代码行数 | 2500+ | ~800 | -68% |
| 映射函数数量 | 52 | ~10 | -80% |
| 遗漏字段风险 | 高 | 低 | 显著降低 |

---

## 7. 实施记录（2025-01）

### ✅ Phase 1: 已完成

Kotlin Proto DSL 已在 `ai-agent-proto/build.gradle.kts` 中配置：
- 支持 DSL 构建器: `connectOptions { model = "opus" }`
- 生成友好的 Kotlin 代码

### ✅ Phase 2: 已完成

创建了 `ai-agent-proto/src/main/kotlin/com/asakii/rpc/proto/ProtoExtensions.kt`：
- 所有 `ConnectOptions` 字段的 `xxxOrNull` 扩展属性
- 枚举类型的 `stringValue` 扩展和 `fromString()` 工厂函数
- 覆盖 `ConnectResult`, `Provider`, `PermissionMode`, `SandboxMode`, `SessionStatus` 等

### ✅ Phase 3: 部分完成

简化了 `ProtoConverter.toRpc()` 方法，使用 ProtoExtensions 替代手动 `hasXxx()` 检查：
```kotlin
// 之前
connectId = if (hasConnectId()) connectId else null

// 之后
connectId = connectIdOrNull
```

**注意**: 没有修改 `AiAgentRpcService` 接口签名，因为这需要更大范围的重构。

### ⚠️ Phase 4: 评估结论 - 不建议删除 RpcModels

经过详细分析，**RpcModels 不是冗余的**，不能简单删除：

1. **用途不同**:
   - `RpcModels`: 用于 JSON 序列化（`@Serializable` 注解），前端通信
   - `Proto 类`: 用于 RSocket 二进制传输，不支持 kotlinx.serialization

2. **被广泛使用**:
   - `HistoryJsonlLoader.kt` - JSONL 历史文件解析
   - `CodexHistoryMapper.kt` - Codex 历史转换
   - `HttpApiServer.kt` - HTTP API 响应
   - `AiAgentRpcServiceImpl.kt` - 业务逻辑层

3. **删除的前提条件**（不具备）:
   - Proto 类支持 kotlinx.serialization（需要自定义序列化器）
   - 或前端改用 Protobuf（需要 protobuf.js）

---

## 8. 实际收益

| 指标 | 改进前 | 改进后 | 说明 |
|------|--------|--------|------|
| 添加 Proto 字段 | 改 3 处 | 改 2 处 | Proto + ProtoExtensions |
| `ProtoConverter.toRpc()` 可读性 | 低 | 高 | 使用 `xxxOrNull` 扩展 |
| 新字段遗漏风险 | 高 | 中 | ProtoExtensions 集中管理 |

---

## 9. 未来优化方向

如果需要进一步减少重复，可考虑：

1. **代码生成**: 从 `.proto` 文件自动生成 `RpcModels` 和映射代码
2. **Proto JSON 序列化**: 使用 `com.google.protobuf:protobuf-java-util` 的 `JsonFormat`
3. **前端 Protobuf**: 使用 `protobuf.js` 让前端直接处理二进制格式

---

## 附录：已修改的文件清单

### 新增文件

- `ai-agent-proto/src/main/kotlin/com/asakii/rpc/proto/ProtoExtensions.kt`

### 修改的文件

- `ai-agent-proto/build.gradle.kts` - Kotlin proto 插件（已有）
- `ai-agent-rpc-api/build.gradle.kts` - 添加 `api(project(":ai-agent-proto"))` 依赖
- `ai-agent-server/.../ProtoConverter.kt` - 简化 `toRpc()` 使用扩展属性

### 保留的文件

- `ai-agent-rpc-api/.../RpcModels.kt` - **保留**，用于 JSON 序列化
- `ai-agent-server/.../ProtoConverter.kt` - **保留**，用于 Proto ↔ RpcModels 转换
