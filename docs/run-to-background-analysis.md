# Run To Background 功能分析与设计

## 官方 CLI 分析

### 原生控制命令

通过分析 `claude-cli-2.1.3.js` 语法树，官方 CLI **原生支持的控制命令**：

```
initialize, interrupt, set_permission_mode, set_model, 
set_max_thinking_tokens, mcp_status, mcp_message, 
mcp_set_servers, rewind_files, ...
```

**官方不支持任何后台运行的控制命令**（`agent_run_to_background`、`bash_run_to_background` 都是我们补丁添加的）。

### 官方内部函数

| 函数 | 用途 | 签名 |
|------|------|------|
| `iV1` | 批量后台化所有任务 | `iV1(getState, setState)` |
| `Me5` | 后台化单个 Bash | `Me5(taskId, getState, setState)` |
| `R42` | 后台化单个 Agent | `R42(taskId, getState, setState)` |
| `wt` | 判断任务是否是 Bash | `wt(task) → boolean` |
| `Jr` | 判断任务是否是 Agent | `Jr(task) → boolean` |

### 控制请求处理上下文

在控制请求处理位置，以下变量可直接访问：

```javascript
X = getAppState   // 获取 appState 的函数
I = setAppState   // 更新 appState 的函数
```

### iV1 函数逻辑

```javascript
function iV1(A, Q) {
  let B = A()  // 获取 appState
  
  // 1. 筛选未后台化的 Bash 任务
  let G = Object.keys(B.tasks).filter(Y => {
    let J = B.tasks[Y]
    return wt(J) && !J.isBackgrounded && J.shellCommand
  })
  for (let Y of G) Me5(Y, A, Q)  // 后台化每个 Bash
  
  // 2. 筛选未后台化的 Agent 任务
  let Z = Object.keys(B.tasks).filter(Y => {
    let J = B.tasks[Y]
    return Jr(J) && !J.isBackgrounded
  })
  for (let Y of Z) R42(Y, A, Q)  // 后台化每个 Agent
}
```

---

## 新设计方案

### 控制命令

```javascript
{
  subtype: "run_to_background",
  task_id?: string  // 可选
}
```

### 行为

| 调用方式 | 行为 |
|---------|------|
| `{ subtype: "run_to_background" }` | 后台化所有任务（Bash + Agent） |
| `{ subtype: "run_to_background", task_id: "xxx" }` | 后台化指定任务（自动判断类型） |

### CLI 补丁实现逻辑

```javascript
if (subtype === "run_to_background") {
  const taskId = request.task_id;
  
  if (!taskId) {
    // 批量模式：后台化所有任务
    iV1(X, I);
    respond({ success: true, mode: "all" });
  } else {
    // 单任务模式：后台化指定任务
    const state = X();
    const task = state.tasks[taskId];
    
    if (!task) {
      respond({ success: false, error: "Task not found" });
    } else if (wt(task)) {
      // Bash 任务
      Me5(taskId, X, I);
      respond({ success: true, type: "bash", task_id: taskId });
    } else if (Jr(task)) {
      // Agent 任务
      R42(taskId, X, I);
      respond({ success: true, type: "agent", task_id: taskId });
    } else {
      respond({ success: false, error: "Unknown task type" });
    }
  }
}
```

### 响应格式

**批量模式响应**：
```javascript
{
  success: true,
  mode: "all"
}
```

**单任务模式响应**：
```javascript
{
  success: true,
  type: "bash" | "agent",
  task_id: string
}
```

**错误响应**：
```javascript
{
  success: false,
  error: string
}
```

---

## 与现有实现的对比

| 方面 | 现有实现 | 新设计 |
|------|---------|--------|
| Agent 后台化 | 通过 resolver Map | 直接调用 `R42` |
| Bash 后台化 | 通过全局 Map + H09 | 直接调用 `Me5` |
| 批量后台化 | 分别调用两个命令 | 直接调用 `iV1` |
| 类型判断 | 调用方需要知道类型 | CLI 内部自动判断 |
| 代码复杂度 | 较高（维护多个 Map） | 较低（直接调用官方函数） |

---

## SDK/前端 API

### 统一方法

```kotlin
// SDK
suspend fun runToBackground(taskId: String? = null): RunToBackgroundResult

// 前端
async runToBackground(taskId?: string): Promise<RunToBackgroundResult>
```

### 返回类型

```kotlin
data class RunToBackgroundResult(
    val success: Boolean,
    val mode: String? = null,      // "all" for batch mode
    val type: String? = null,      // "bash" | "agent" for single task
    val taskId: String? = null,
    val error: String? = null
)
```

---

## Terminal MCP 后台任务架构

### 现有机制

Terminal MCP 已有后台任务追踪机制：

```kotlin
// TerminalSessionManager.kt
data class TerminalBackgroundTask(
    val sessionId: String,           // 终端会话 ID
    val toolUseId: String,           // MCP 工具调用 ID
    val command: String,             // 执行的命令
    val startTime: Long,             // 开始时间戳
    var isBackground: Boolean = false,  // 是否已移到后台
    var backgroundTime: Long? = null    // 移到后台的时间戳
)

// 关键方法
fun recordTaskStart(sessionId, toolUseId, command)
fun recordTaskComplete(toolUseId)
fun markTaskAsBackground(toolUseId): Boolean
fun getBackgroundableTasks(thresholdMs): List<TerminalBackgroundTask>
```

### Terminal MCP 后台化 API

需要新增 HTTP API 端点处理 Terminal MCP 的后台化请求：

**端点**: `POST /api/`

**请求**:
```json
{
  "action": "terminal.runToBackground",
  "data": {
    "toolUseId": "optional-specific-tool-id"
  }
}
```

**行为**:
| 参数 | 行为 |
|------|------|
| 无 toolUseId | 将所有正在运行的 Terminal 任务移到后台 |
| 有 toolUseId | 将指定任务移到后台 |

**响应**:
```json
{
  "success": true,
  "backgrounded": ["tool-use-id-1", "tool-use-id-2"],
  "count": 2
}
```

---

## 前端调用策略

### 工具类型判断

```typescript
// ToolCallDisplay.vue 中的判断逻辑
const isTerminalMcpTool = computed(() => {
  return props.toolCall?.toolName?.startsWith('mcp__terminal__') ?? false
})

const isClaudeTool = computed(() => {
  const name = props.toolCall?.toolName || ''
  return ['Bash', 'Task'].includes(name) || name.startsWith('mcp__')
})
```

### 批量后台化 (Ctrl+B)

当用户按 Ctrl+B 时，需要同时调用两个 API：

```typescript
async function runAllToBackground() {
  // 1. 后台化 Claude 的 Bash/Task 任务
  await claudeSession.runToBackground()  // 无参数，批量模式
  
  // 2. 后台化 Terminal MCP 的任务
  await ideaBridge.query('terminal.runToBackground', {})
}
```

### 单任务后台化

根据工具类型调用对应 API：

```typescript
async function runTaskToBackground(toolCall: ToolCall) {
  if (toolCall.toolName?.startsWith('mcp__terminal__')) {
    // Terminal MCP 任务
    await ideaBridge.query('terminal.runToBackground', {
      toolUseId: toolCall.id
    })
  } else {
    // Claude Bash/Task 任务
    await claudeSession.runToBackground(toolCall.id)
  }
}
```

---

## 完整架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                           前端 (Vue)                                 │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ Ctrl+B 按下                                                      ││
│  │   ├── claudeSession.runToBackground()  → WebSocket RPC          ││
│  │   └── ideaBridge.query('terminal.runToBackground') → HTTP       ││
│  └─────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ 单任务按钮点击                                                   ││
│  │   └── 判断 toolName                                              ││
│  │       ├── mcp__terminal__* → HTTP terminal.runToBackground      ││
│  │       └── 其他 → WebSocket runToBackground(taskId)              ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         后端 (Kotlin)                               │
│  ┌────────────────────────────────┐  ┌────────────────────────────┐ │
│  │ WebSocket RPC                  │  │ HTTP API                   │ │
│  │ runToBackground(taskId?)       │  │ terminal.runToBackground   │ │
│  │         │                      │  │          │                 │ │
│  │         ▼                      │  │          ▼                 │ │
│  │ ControlProtocol.kt             │  │ TerminalSessionManager     │ │
│  │ → send control request         │  │ → markTaskAsBackground()   │ │
│  └────────────────────────────────┘  └────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       Claude CLI (补丁)                             │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ control request: { subtype: "run_to_background", task_id? }    ││
│  │                                                                 ││
│  │ if (!task_id) {                                                 ││
│  │   iV1(X, I)  // 批量后台化所有 Bash + Agent                     ││
│  │ } else {                                                        ││
│  │   const task = X().tasks[task_id]                               ││
│  │   if (wt(task)) Me5(task_id, X, I)  // Bash                     ││
│  │   else if (Jr(task)) R42(task_id, X, I)  // Agent               ││
│  │ }                                                               ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

---

## 实现步骤

1. **重写 CLI 补丁**：使用 `iV1`、`Me5`、`R42` 替代现有的 resolver Map 方式
2. **简化 SDK**：移除 `bashRunToBackground`，统一使用 `runToBackground(taskId?)`
3. **更新 Proto 定义**：简化响应结构
4. **后端添加 Terminal MCP API**：在 HttpApiServer 添加 `terminal.runToBackground` 端点
5. **前端适配**：
   - 批量模式：同时调用 Claude API + Terminal API
   - 单任务模式：根据 toolName 判断调用哪个 API
