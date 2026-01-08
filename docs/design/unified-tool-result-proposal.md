# 统一工具结果格式方案

## 1. 问题分析

### 当前状态

**Claude 后端数据流：**
```
Claude SDK Response
    ↓
ClaudeSession (RSocket)
    ↓
消息内容中的 tool_result ContentBlock
    ↓
useSessionMessages.processToolResults()
    ↓
tools.updateToolResult(tool_use_id, result)
```

**Codex 后端数据流：**
```
Codex JSON-RPC: item/completed
    ↓
CodexSession.handleNotification()
    ↓
tool_completed BackendEvent (result: unknown)
    ↓
useSessionTab.handleBackendEvent()
    ↓
❌ 未调用 tools.updateToolResult()
```

### 问题

1. **两种不同的传递机制**：Claude 通过消息内容，Codex 通过事件
2. **类型不明确**：`ToolCompletedEvent.result` 是 `unknown`
3. **处理分散**：前端需要在两个地方分别处理

---

## 2. 统一方案设计

### 2.1 目标架构

```
┌─────────────────────────────────────────────────────────┐
│                    后端 API 原始数据                      │
│  Claude: tool_result ContentBlock                       │
│  Codex: item/completed { item: {...} }                  │
└─────────────┬───────────────────────────┬───────────────┘
              ↓                           ↓
┌─────────────────────┐       ┌─────────────────────┐
│   ClaudeSession     │       │    CodexSession     │
│   发出统一事件        │       │    发出统一事件      │
└─────────────┬───────┘       └───────────┬─────────┘
              ↓                           ↓
┌─────────────────────────────────────────────────────────┐
│           统一的 tool_completed BackendEvent             │
│                                                         │
│  {                                                      │
│    type: 'tool_completed',                              │
│    itemId: string,           // 工具调用 ID              │
│    toolName: string,         // 工具名称                 │
│    success: boolean,                                    │
│    result: UnifiedToolResult // 统一的结果格式            │
│  }                                                      │
└─────────────────────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────────────────────┐
│              useSessionTab.handleBackendEvent()         │
│                                                         │
│  case 'tool_completed':                                 │
│      tools.updateToolResult(event.itemId, event.result) │
└─────────────────────────────────────────────────────────┘
```

### 2.2 统一类型定义

```typescript
// frontend/src/types/backend.ts

/**
 * 统一的工具执行结果
 * 
 * 所有后端（Claude/Codex/...）的工具结果都转换为此格式
 */
export interface UnifiedToolResult {
  /** 结果类型标识 */
  type: 'tool_result'
  
  /** 对应的工具调用 ID */
  tool_use_id: string
  
  /** 
   * 结果内容
   * - string: 简单文本结果
   * - object: 结构化结果（如文件操作详情）
   * - array: 多个结果块
   */
  content: string | Record<string, unknown> | unknown[]
  
  /** 是否执行出错 */
  is_error: boolean
  
  /** 错误信息（当 is_error=true 时） */
  error_message?: string
  
  /** 工具类型（可选，用于特殊处理） */
  tool_type?: 'bash' | 'read' | 'write' | 'edit' | 'mcp' | 'task' | string
  
  /** 子代理 ID（仅 Task 工具） */
  agent_id?: string
}

/**
 * Tool execution completed event (更新后)
 */
export interface ToolCompletedEvent extends BaseBackendEvent {
  type: 'tool_completed'
  /** 工具调用 ID */
  itemId: string
  /** 工具名称 */
  toolName?: string
  /** 是否成功 */
  success: boolean
  /** 统一格式的工具结果 */
  result: UnifiedToolResult
}
```

### 2.3 Session 层转换逻辑

#### CodexSession 转换

```typescript
// frontend/src/services/backend/CodexSession.ts

case 'item/completed': {
  const item = params.item as CodexItem
  
  // 转换为统一格式
  const unifiedResult: UnifiedToolResult = this.convertToUnifiedResult(item)
  
  this.emitEvent({
    type: 'tool_completed',
    sessionId,
    timestamp,
    itemId: item.id,
    toolName: this.getToolName(item),
    success: item.status === 'Completed' || item.status === 'Applied',
    result: unifiedResult,
  })
  break
}

/**
 * 将 Codex item 转换为统一的 ToolResult 格式
 */
private convertToUnifiedResult(item: CodexItem): UnifiedToolResult {
  const isError = item.status === 'Cancelled' || item.status === 'Declined'
  
  // 根据 item 类型提取内容
  let content: string | Record<string, unknown> = {}
  let toolType: string = item.type
  
  switch (item.type) {
    case 'commandExecution':
      content = {
        command: (item as any).command,
        output: (item as any).output,
        exitCode: (item as any).exitCode,
      }
      toolType = 'bash'
      break
      
    case 'fileChange':
      content = {
        filePath: (item as any).filePath,
        changeType: (item as any).changeType,
        diff: (item as any).diff,
      }
      toolType = 'edit'
      break
      
    case 'mcpToolCall':
      content = {
        server: (item as any).server,
        tool: (item as any).tool,
        result: (item as any).result,
      }
      toolType = 'mcp'
      break
      
    default:
      content = item as any
  }
  
  return {
    type: 'tool_result',
    tool_use_id: item.id,
    content,
    is_error: isError,
    tool_type: toolType,
  }
}
```

#### ClaudeSession 转换（可选方案）

**方案 A：保持现有机制**
- Claude 继续通过消息内容中的 `tool_result` 传递
- `useSessionMessages.processToolResults()` 继续处理
- 不发出 `tool_completed` 事件

**方案 B：统一发出事件**
- Claude 也在收到 `tool_result` 时发出 `tool_completed` 事件
- 前端统一在 `handleBackendEvent` 中处理
- 移除 `processToolResults` 的直接调用

**建议：采用方案 A**，因为：
1. Claude 的现有机制工作正常
2. 修改风险较小
3. 两种机制最终都调用 `tools.updateToolResult()`，结果一致

### 2.4 前端消费层统一处理

```typescript
// frontend/src/composables/useSessionTab.ts

case 'tool_completed': {
  log.info(`[Tab ${tabId}] 工具完成: ${event.toolName || event.itemId}, success=${event.success}`)
  
  // 统一更新工具状态
  if (event.itemId && event.result) {
    tools.updateToolResult(event.itemId, event.result)
    
    // 触发 UI 更新
    messagesHandler.triggerDisplayItemsUpdate()
  }
  break
}
```

---

## 3. 实施步骤

### Phase 1: 类型定义
1. 在 `backend.ts` 中添加 `UnifiedToolResult` 类型
2. 更新 `ToolCompletedEvent` 使用新类型

### Phase 2: CodexSession 转换
1. 实现 `convertToUnifiedResult()` 方法
2. 修改 `item/completed` 事件处理，输出统一格式
3. 添加工具类型映射

### Phase 3: 前端统一处理
1. 更新 `useSessionTab.ts` 的 `tool_completed` 处理
2. 确保调用 `triggerDisplayItemsUpdate()` 触发 UI 更新
3. 移除临时修复代码

### Phase 4: 测试验证
1. Claude 后端：工具卡片显示正常
2. Codex 后端：工具卡片显示 result
3. 各种工具类型：bash、edit、mcp 等

---

## 4. 类型兼容性

### 与现有类型的关系

```
ToolResultContent (message.ts)      ← Claude 消息内容格式
        ↓
ToolResultBlock = ToolResultContent ← 类型别名
        ↓
ToolResult (display.ts)             ← 显示层使用的格式
        ↓
UnifiedToolResult (backend.ts)      ← 新增：统一的后端事件格式
```

`UnifiedToolResult` 与 `ToolResultContent` 兼容，可以直接传递给 `tools.updateToolResult()`。

---

## 5. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 类型转换遗漏字段 | 工具卡片显示不完整 | 添加详细的类型映射测试 |
| Codex item 格式变化 | 转换失败 | 添加 fallback 处理 |
| UI 更新未触发 | 结果不显示 | 确保调用 triggerDisplayItemsUpdate |
| Claude 现有机制受影响 | 功能回退 | 保持 Claude 现有机制不变 |

---

## 6. 后续优化

1. **工具结果渲染组件统一**：根据 `tool_type` 选择不同的渲染组件
2. **错误处理标准化**：统一的错误展示格式
3. **结果缓存**：避免重复转换
