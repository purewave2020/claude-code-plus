# Bash 后台执行实现分析

本文档记录了 Bash 后台执行功能的 CLI 补丁分析和前后端实现方案。

---

## 1. CLI 补丁分析

### 1.1 官方 Bash 后台机制

CLI 中 Bash 后台执行通过以下流程实现：

```
用户按 Ctrl+B
    ↓
se5 函数中检测 shellCommand.status === "backgrounded"
    ↓
调用 H09 函数创建后台任务
    ↓
返回 {backgroundTaskId, backgroundedByUser: true}
    ↓
Bash 继续在后台运行
```

### 1.2 关键函数分析

#### H09 函数 - 创建后台任务

```javascript
function H09(A, Q) {
  // A = {command, description, shellCommand}
  // Q = setAppState
  let Y = HxA("local_bash");  // 生成任务 ID
  // ... 创建后台任务
  return {taskId: Y, cleanup: ...};
}
```

#### se5 函数 - Bash 执行生成器

```javascript
async function* se5({input, setAppState, abortController, ...}) {
  // 创建 shellCommand
  let z = await Z51(command, timeout, cwd, ...);
  
  // 检查后台状态
  if (z.status === "backgrounded") {
    return {backgroundTaskId: j, backgroundedByUser: true};
  }
  
  // 正常执行...
}
```

### 1.3 SDK 无法直接控制的原因

**问题**：CLI 原生代码中，`se5` 函数内部创建 `shellCommand`，无法从外部访问。

**解决方案**：通过 AST 补丁注入全局对象，在 `shellCommand` 创建后注册到全局 Map。

---

## 2. 补丁设计 (007-bash-background.js)

### 2.1 设计原则

- **最小侵入性**：所有逻辑代码抽取到全局对象
- **只注入方法调用**：原有代码只添加最少的方法调用

### 2.2 全局对象结构

```javascript
global.__claudePlusBash = {
  running: new Map(),  // toolUseId -> {shellCommand, setAppState, command, description, startTime}
  
  register(toolUseId, shellCommand, setAppState, command, description) {
    // 注册运行中的 Bash 命令
  },
  
  unregister(toolUseId) {
    // 注销已完成的 Bash 命令
  },
  
  background(toolUseId, H09Func) {
    // 将指定 Bash 命令移到后台
    // 1. 调用 H09 创建后台任务
    // 2. 设置 shellCommand.status = "backgrounded"
    // 3. 返回 {success, taskId, command}
  },
  
  getRunning(minDurationMs) {
    // 获取运行中的 Bash 命令列表
  }
};
```

### 2.3 注入点

| 步骤 | 注入位置 | 注入内容 |
|------|----------|----------|
| 1 | 程序开头 | 全局对象 `global.__claudePlusBash` |
| 2 | Bash.call 方法 | 修改 se5 调用，传入 `__toolUseId` |
| 3 | se5 函数内部 | 创建 shellCommand 后注册到全局 Map |
| 4 | 控制请求处理 | 添加 `bash_run_to_background` 命令 |

### 2.4 控制命令协议

**请求：**
```json
{
  "subtype": "bash_run_to_background",
  "task_id": "toolu_01ABC123..."  // tool_use_id
}
```

**响应：**
```json
{
  "subtype": "success",
  "response": {
    "task_id": "background-task-id",
    "command": "npm run build"
  }
}
```

---

## 3. 前后端实现方案

### 3.1 架构对比

| 层级 | Task 后台 | Bash 后台 |
|------|-----------|-----------|
| 前端调用 | `session.runInBackground()` | `session.bashRunToBackground(taskId)` |
| RSocket 路由 | `agent.runInBackground` | `agent.bashRunToBackground` |
| 后端处理 | `handleRunInBackground()` | `handleBashRunToBackground()` |
| SDK 方法 | `client.runInBackground()` | `client.bashRunToBackground(taskId)` |
| CLI 控制命令 | `agent_run_to_background` | `bash_run_to_background` |

### 3.2 后端实现 (ai-agent-server)

#### RSocketHandler.kt
```kotlin
// 添加路由
"agent.bashRunToBackground" -> handleBashRunToBackground(dataBytes, rpcService)

// 处理函数
private suspend fun handleBashRunToBackground(
    dataBytes: ByteArray,
    rpcService: AiAgentRpcService
): Payload {
    val request = ProtoBuf.decodeFromByteArray<Proto.BashRunToBackgroundRequest>(dataBytes)
    val result = rpcService.bashRunToBackground(request.taskId)
    return buildPayload { data(ProtoBuf.encodeToByteArray(result.toProto())) }
}
```

#### AiAgentRpcServiceImpl.kt
```kotlin
override suspend fun bashRunToBackground(taskId: String): RpcBashBackgroundResult {
    sdkLog.info { "🔄 [SDK] 将 Bash 命令移到后台: $taskId" }
    val activeClient = client ?: error("AI Agent 尚未连接")
    val result = activeClient.bashRunToBackground(taskId)
    return RpcBashBackgroundResult(
        success = result.success,
        taskId = result.taskId,
        command = result.command
    )
}
```

### 3.3 前端实现 (frontend)

#### types/rpc.ts
```typescript
export interface RpcBashBackgroundResult {
  success: boolean
  taskId?: string
  command?: string
}
```

#### services/backend/BackendSession.ts
```typescript
async bashRunToBackground(taskId: string): Promise<RpcBashBackgroundResult> {
  return this.rsocket.requestResponse('agent.bashRunToBackground', { taskId })
}
```

#### stores/sessionStore.ts
```typescript
async function bashRunToBackground(taskId: string): Promise<RpcBashBackgroundResult> {
  if (!currentTab.value) {
    throw new Error('当前没有活跃的会话')
  }
  return currentTab.value.bashRunToBackground(taskId)
}
```

### 3.4 UI 集成

在 `BashToolDisplay.vue` 组件中添加后台按钮：

```vue
<template>
  <button 
    v-if="isRunning && canBackground"
    @click="handleBackground"
    title="移到后台 (Ctrl+B)"
  >
    <IconBackground />
  </button>
</template>

<script setup>
const handleBackground = async () => {
  if (props.toolUseId) {
    await sessionStore.bashRunToBackground(props.toolUseId)
  }
}
</script>
```

---

## 4. 关键发现

### 4.1 tool_use_id 可用性

在 Bash 工具的 `call` 方法中：
```javascript
async call(A, Q, B, G, Z) {
  // B 就是 tool_use_id
}
```

通过修改 `se5` 调用传入 `__toolUseId`，可以在 `se5` 内部获取到 `tool_use_id`。

### 4.2 后台触发机制

CLI 检测 `shellCommand.status === "backgrounded"` 来触发后台切换，这比 Promise.race 更简单直接。

### 4.3 H09 函数复用

补丁直接复用 CLI 的 `H09` 函数创建后台任务，保证与官方行为一致。

---

## 5. 文件清单

### 已完成

| 文件 | 状态 | 说明 |
|------|------|------|
| `cli-patches/patches/007-bash-background.js` | ✅ | CLI 补丁 |
| `claude-agent-sdk/.../ControlProtocol.kt` | ✅ | SDK 控制协议 |
| `claude-agent-sdk/.../ClaudeCodeSdkClient.kt` | ✅ | SDK 公共 API |

### 待实现

| 文件 | 状态 | 说明 |
|------|------|------|
| `ai-agent-server/.../RSocketHandler.kt` | ⏳ | 添加路由 |
| `ai-agent-server/.../AiAgentRpcService.kt` | ⏳ | 接口定义 |
| `ai-agent-server/.../AiAgentRpcServiceImpl.kt` | ⏳ | 实现 |
| `frontend/src/types/rpc.ts` | ⏳ | 类型定义 |
| `frontend/src/services/backend/BackendSession.ts` | ⏳ | RSocket 调用 |
| `frontend/src/stores/sessionStore.ts` | ⏳ | Store 方法 |
| `frontend/src/components/tools/BashToolDisplay.vue` | ⏳ | UI 按钮 |

---

## 6. 测试验证

### 6.1 补丁验证
```bash
cd claude-agent-sdk/cli-patches
node patch-cli.js --dry-run claude-cli-2.1.3.js
```

### 6.2 功能测试
1. 启动一个长时间运行的 Bash 命令（如 `sleep 60`）
2. 在前端点击后台按钮或按 Ctrl+B
3. 验证命令继续在后台运行
4. 验证返回的 `backgroundTaskId` 正确
