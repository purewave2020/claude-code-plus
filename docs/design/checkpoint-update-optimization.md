# Checkpoint 更新机制优化方案

## 1. 问题分析

### 1.1 当前架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        ModernChatView.vue                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ const displayItemsRef = computed(() =>                      ││
│  │   sessionStore.currentDisplayItems)                         ││
│  │ const fileChanges = useFileChanges(displayItemsRef)         ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      useFileChanges.ts                           │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ watch(displayItems, (newItems) => {                         ││
│  │   for (const item of newItems) {        // 遍历所有 items   ││
│  │     if (item.displayType !== 'toolCall') continue           ││
│  │     if (toolCall.status === SUCCESS) {                      ││
│  │       addFileEdit(toolCall)                                 ││
│  │     }                                                       ││
│  │   }                                                         ││
│  │ }, { deep: true })  // ⚠️ 性能问题：深度监听                 ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 性能问题

| 问题 | 影响 | 严重程度 |
|------|------|---------|
| **deep watch 递归遍历** | 每次变化都递归检查整个对象树 | 🔴 高 |
| **高频触发** | 文本流式输出时每个字符都触发 | 🔴 高 |
| **全量遍历** | 每次触发都遍历所有 displayItems | 🟡 中 |
| **无效检查** | 99% 的触发与文件修改无关 | 🟡 中 |

### 1.3 触发频率估算

假设一次对话：
- 流式输出 1000 个字符 → 触发 ~1000 次
- 5 个工具调用 → 触发 ~15 次（start/input/result）
- 实际需要处理的文件修改 → 仅 2-3 次

**效率：< 0.3%**

---

## 2. 优化方案：事件驱动架构

### 2.1 设计目标

1. **精确触发**：仅在 JetBrains 文件工具完成时触发
2. **O(1) 复杂度**：直接处理单个 toolCall，无需遍历
3. **解耦**：useFileChanges 不再依赖 displayItems 的 deep watch
4. **向后兼容**：保留清理已删除记录的功能

### 2.2 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                          useSessionTab                           │
│  ┌──────────────────┐    ┌──────────────────┐                   │
│  │  useSessionTools │───▶│ useFileChanges   │                   │
│  │                  │    │ (注入 tools)     │                   │
│  │  updateToolResult│    │                  │                   │
│  │       │          │    │  onToolCompleted │                   │
│  │       ▼          │    │       │          │                   │
│  │  emit('completed')───▶│  addFileEdit()   │                   │
│  └──────────────────┘    └──────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 数据流

```
JetBrains MCP 工具执行完成
         │
         ▼
useSessionTools.updateToolResult(toolUseId, result)
         │
         ├──▶ 更新 toolCall.status = SUCCESS
         │
         └──▶ emit('toolCompleted', toolCall)  // 新增
                      │
                      ▼
         useFileChanges.onToolCompleted(toolCall)
                      │
                      ├──▶ 检查是否为 JetBrains 文件工具
                      │
                      └──▶ addFileEdit(toolCall)  // O(1)
```

---

## 3. 详细设计

### 3.1 useSessionTools 改造

```typescript
// frontend/src/composables/useSessionTools.ts

import { reactive, computed } from 'vue'
import mitt, { type Emitter } from 'mitt'  // 轻量级事件库

// 事件类型定义
type ToolEvents = {
  toolCompleted: ToolCall
  toolFailed: ToolCall
  toolStarted: ToolCall
}

export function useSessionTools() {
  // ========== 事件发射器 ==========
  const emitter: Emitter<ToolEvents> = mitt<ToolEvents>()
  
  // ... 现有代码 ...

  function updateToolResult(toolUseId: string, result: ToolResultBlock | ToolResult): boolean {
    const toolCall = pendingToolCalls.get(toolUseId)
    if (!toolCall) {
      return false
    }

    // 更新状态
    const isError = 'is_error' in result ? result.is_error : false
    toolCall.status = isError ? ToolCallStatus.FAILED : ToolCallStatus.SUCCESS
    toolCall.endTime = Date.now()
    toolCall.result = result as ToolResult

    // 🆕 发射事件
    if (isError) {
      emitter.emit('toolFailed', toolCall)
    } else {
      emitter.emit('toolCompleted', toolCall)
    }

    return true
  }

  return {
    // ... 现有导出 ...
    
    // 🆕 事件订阅方法
    onToolCompleted: (handler: (toolCall: ToolCall) => void) => {
      emitter.on('toolCompleted', handler)
      return () => emitter.off('toolCompleted', handler)  // 返回取消订阅函数
    },
    onToolFailed: (handler: (toolCall: ToolCall) => void) => {
      emitter.on('toolFailed', handler)
      return () => emitter.off('toolFailed', handler)
    },
    onToolStarted: (handler: (toolCall: ToolCall) => void) => {
      emitter.on('toolStarted', handler)
      return () => emitter.off('toolStarted', handler)
    },
  }
}
```

### 3.2 useFileChanges 改造

```typescript
// frontend/src/composables/useFileChanges.ts

import { ref, computed, watch, onUnmounted, type Ref, type ComputedRef } from 'vue'
import type { DisplayItem, ToolCall } from '@/types/display'
import type { SessionToolsInstance } from './useSessionTools'

/**
 * 文件改动追踪 Composable
 * 
 * @param displayItems - 用于清理已删除的记录（shallow watch）
 * @param tools - 工具管理实例，用于订阅工具完成事件
 */
export function useFileChanges(
  displayItems: Ref<DisplayItem[]> | ComputedRef<DisplayItem[]>,
  tools?: SessionToolsInstance
) {
  const fileEdits = ref<FileModification[]>([])
  const rollingBackFiles = ref<Set<string>>(new Set())
  
  // ... 现有的辅助函数 ...

  // ========== 方案 A：事件驱动（推荐）==========
  if (tools) {
    // 订阅工具完成事件
    const unsubscribe = tools.onToolCompleted((toolCall) => {
      // 只处理 JetBrains 文件编辑工具
      if (!isJetBrainsFileEditTool(toolCall.toolName)) return
      
      // 只在生成中添加（历史加载时不添加）
      if (!sessionStore.currentIsGenerating) return
      
      addFileEdit(toolCall)
    })
    
    // 组件卸载时取消订阅
    onUnmounted(unsubscribe)
  }
  
  // ========== 清理机制（保留 shallow watch）==========
  // 仅用于清理已删除的记录，不再用于检测新的工具完成
  watch(
    () => displayItems.value.length,  // 只监听长度变化
    () => {
      // 收集当前有效的 toolUseId
      const validIds = new Set<string>()
      for (const item of displayItems.value) {
        if (item.displayType === 'toolCall') {
          validIds.add(item.id)
        }
      }
      
      // 清理不存在的记录
      const before = fileEdits.value.length
      fileEdits.value = fileEdits.value.filter(e => validIds.has(e.toolUseId))
      
      if (fileEdits.value.length < before) {
        console.log(`[useFileChanges] Cleaned ${before - fileEdits.value.length} stale edits`)
      }
    }
  )

  return {
    // ... 现有导出 ...
  }
}
```

### 3.3 Tab 集成

```typescript
// frontend/src/composables/useSessionTab.ts

export function useSessionTab(initialOrder: number = 0) {
  const tools: SessionToolsInstance = useSessionTools()
  const stats: SessionStatsInstance = useSessionStats()
  const permissions: SessionPermissionsInstance = useSessionPermissions()
  const messagesHandler: SessionMessagesInstance = useSessionMessages(tools, stats)
  
  // 🆕 文件改动追踪（集成到 Tab）
  const fileChanges = useFileChanges(messagesHandler.displayItems, tools)

  return {
    // ... 现有导出 ...
    
    // 🆕 导出 fileChanges
    fileChanges,
  }
}
```

### 3.4 ModernChatView 调整

```typescript
// frontend/src/components/chat/ModernChatView.vue

// 不再在这里创建 fileChanges，而是从 sessionStore 获取
const fileChanges = computed(() => sessionStore.currentTab?.fileChanges)
provide('fileChanges', fileChanges)
```

---

## 4. 迁移计划

### Phase 1: 添加事件系统（向后兼容）

1. 安装 mitt：`npm install mitt`
2. 在 useSessionTools 中添加事件发射器
3. updateToolResult 中添加事件发射
4. 导出事件订阅方法

### Phase 2: 改造 useFileChanges

1. 添加 tools 参数支持
2. 实现事件驱动的文件追踪
3. 保留 shallow watch 用于清理
4. 移除 deep watch

### Phase 3: 集成到 Tab

1. 在 useSessionTab 中创建 fileChanges
2. 导出 fileChanges 实例
3. ModernChatView 从 Tab 获取

### Phase 4: 清理

1. 移除旧的 deep watch 代码
2. 更新类型定义
3. 添加单元测试

---

## 5. 性能对比

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 触发频率 | ~1000 次/对话 | ~3 次/对话 | 99.7% ↓ |
| 单次处理复杂度 | O(n) 遍历 | O(1) 直接处理 | 显著 ↓ |
| deep watch 开销 | 递归遍历对象树 | 无 | 100% ↓ |
| 内存压力 | 高（频繁创建临时对象） | 低 | 显著 ↓ |

---

## 6. 备选方案

### 6.1 方案 B：Provide/Inject 事件总线

如果不想修改 useSessionTools 的接口，可以使用全局事件总线：

```typescript
// eventBus.ts
export const toolEventBus = mitt<ToolEvents>()

// useSessionTools.ts
import { toolEventBus } from './eventBus'
toolEventBus.emit('toolCompleted', toolCall)

// useFileChanges.ts
import { toolEventBus } from './eventBus'
toolEventBus.on('toolCompleted', handler)
```

**优点**：改动最小
**缺点**：全局状态，多 Tab 时需要额外区分

### 6.2 方案 C：响应式 Signal

使用 Vue 3.4+ 的 `watch` 配合 `{ flush: 'sync' }` 和特定的状态变量：

```typescript
// useSessionTools.ts
const lastCompletedToolCall = shallowRef<ToolCall | null>(null)

function updateToolResult(...) {
  // ...
  lastCompletedToolCall.value = toolCall  // 触发订阅者
}

// useFileChanges.ts
watch(
  () => tools.lastCompletedToolCall.value,
  (toolCall) => {
    if (toolCall && isJetBrainsFileEditTool(toolCall.toolName)) {
      addFileEdit(toolCall)
    }
  },
  { flush: 'sync' }
)
```

**优点**：纯 Vue 响应式，无额外依赖
**缺点**：需要手动管理状态重置

---

## 7. 推荐方案

**推荐采用方案 A（mitt 事件驱动）**，理由：

1. **最佳性能**：事件驱动，精确触发
2. **清晰语义**：`onToolCompleted` 比 watch 更直观
3. **易于扩展**：未来可添加更多事件类型
4. **成熟方案**：mitt 是广泛使用的轻量级事件库（<200B gzip）
5. **类型安全**：完整的 TypeScript 支持

---

## 8. 文件修改清单

| 文件 | 修改内容 |
|------|---------|
| `package.json` | 添加 mitt 依赖 |
| `useSessionTools.ts` | 添加事件发射器和订阅方法 |
| `useFileChanges.ts` | 改为事件驱动，移除 deep watch |
| `useSessionTab.ts` | 集成 fileChanges |
| `ModernChatView.vue` | 从 Tab 获取 fileChanges |
| `sessionStore.ts` | 导出 currentTab.fileChanges |
