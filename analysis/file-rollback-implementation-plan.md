# 文件回滚功能实施方案（v2 - 使用 IDEA Local History）

## 📋 功能概述

为 Claude Code Plus 实现文件回滚功能，允许用户：
1. 在工具卡片上点击回滚按钮，撤销单次文件操作
2. 在输入框上方查看本会话所有文件变更，支持选择性回滚

---

## 🎯 设计目标

- **安全性**: 利用 IDEA Local History 的成熟机制
- **可追溯**: 每次操作前自动创建标签
- **用户友好**: 清晰展示变更历史，支持批量和单独回滚
- **简单可靠**: 复用 IDEA 内置功能，最小化自定义代码

---

## 🔑 核心方案：使用 IDEA Local History API

### API 概述

```kotlin
import com.intellij.history.LocalHistory
import com.intellij.history.Label

// 1. 在操作前创建标签
val label: Label = LocalHistory.getInstance().putSystemLabel(
    project,
    "Before Edit: ${filePath}"
)

// 2. 回滚到标签状态
label.revert(project, virtualFile)

// 3. 获取标签时的文件内容（可选）
val content: ByteContent? = label.getByteContent(filePath)
```

### 优势

| 方面 | 自定义缓存方案 | IDEA Local History 方案 |
|------|---------------|------------------------|
| 内存占用 | 需要缓存文件内容 | IDEA 自动管理 |
| 持久化 | 会话结束丢失 | 跨会话保留 |
| 可靠性 | 需自己实现 | IDEA 成熟机制 |
| 代码量 | 复杂 | 简单 |
| 撤销支持 | 需要额外处理 | 原生支持 |

---

## 📊 数据模型设计

### 1. FileChangeLabel 接口（后端）

```kotlin
// 用于存储操作与标签的关联
data class FileChangeLabel(
    val toolUseId: String,           // 关联的工具调用 ID
    val filePath: String,            // 文件路径
    val operationType: String,       // "edit" | "write"
    val label: Label,                // IDEA Local History 标签
    val timestamp: Long,             // 操作时间戳
    val description: String          // 操作描述
)

// 会话级别的标签缓存
object FileChangeLabelCache {
    // toolUseId -> FileChangeLabel
    private val labels = ConcurrentHashMap<String, FileChangeLabel>()

    fun record(toolUseId: String, label: FileChangeLabel) {
        labels[toolUseId] = label
    }

    fun get(toolUseId: String): FileChangeLabel? = labels[toolUseId]

    fun getByFile(filePath: String): List<FileChangeLabel> {
        return labels.values.filter { it.filePath == filePath }
    }

    fun clearSession() {
        labels.clear()
    }
}
```

### 2. FileChange 接口（前端）

```typescript
// frontend/src/types/fileChange.ts

export interface FileChange {
  id: string                    // toolUseId
  filePath: string              // 文件路径
  operationType: 'edit' | 'write'  // 操作类型
  timestamp: number             // 操作时间戳
  description: string           // 操作描述

  // Edit 特有字段（用于显示）
  oldString?: string
  newString?: string

  // 状态
  canRollback: boolean          // 是否可以回滚
  isRolledBack: boolean         // 是否已回滚
}
```

---

## 🔄 工作流程

### Edit/Write 操作流程

```
1. AI 调用 EditFile/WriteFile 工具
2. 工具执行前：
   - LocalHistory.putSystemLabel(project, "Before Edit: xxx.kt")
   - 保存 Label 到 FileChangeLabelCache
3. 工具执行编辑/写入操作
4. 返回结果，包含 toolUseId
5. 前端更新 fileChanges 状态
```

### 回滚流程

```
1. 用户点击回滚按钮
2. 前端发送回滚请求，携带 toolUseId
3. 后端从 FileChangeLabelCache 获取 Label
4. 调用 label.revert(project, virtualFile)
5. 返回回滚结果
6. 前端更新 isRolledBack 状态
```

---

## 🖼️ UI 组件设计

### 1. FileChangesBar 组件

**位置**: 输入框上方，仅在有文件变更时显示

```vue
<!-- frontend/src/components/chat/FileChangesBar.vue -->
<template>
  <div v-if="hasChanges" class="file-changes-bar">
    <div class="bar-header">
      <span class="title">
        <svg class="icon" viewBox="0 0 16 16" width="14" height="14">
          <path fill="currentColor" d="M2 1.5a.5.5 0 0 1 .5-.5h11a.5.5 0 0 1 0 1h-11a.5.5 0 0 1-.5-.5z"/>
          <path fill="currentColor" d="M2 4.5a.5.5 0 0 1 .5-.5h11a.5.5 0 0 1 0 1h-11a.5.5 0 0 1-.5-.5z"/>
          <path fill="currentColor" d="M2 7.5a.5.5 0 0 1 .5-.5h11a.5.5 0 0 1 0 1h-11a.5.5 0 0 1-.5-.5z"/>
        </svg>
        {{ changedFilesCount }} 个文件已修改
      </span>
      <button class="toggle-btn" @click="expanded = !expanded">
        {{ expanded ? '收起' : '展开' }}
      </button>
    </div>

    <div v-if="expanded" class="changes-list">
      <div
        v-for="file in changedFiles"
        :key="file.path"
        class="file-item"
      >
        <span class="file-icon">📄</span>
        <span class="file-path" @click="openFile(file.path)">
          {{ shortenPath(file.path) }}
        </span>
        <span class="change-count">({{ file.changeCount }})</span>
        <button
          class="rollback-btn"
          :disabled="file.isRolledBack"
          @click="rollbackFile(file.path)"
        >
          ↶
        </button>
      </div>
    </div>
  </div>
</template>
```

### 2. 工具卡片回滚按钮

```vue
<!-- CompactToolCard.vue 中添加 -->
<button
  v-if="showRollbackButton"
  class="rollback-btn"
  :class="{ 'rolled-back': isRolledBack }"
  :disabled="isRolledBack"
  @click.stop="handleRollback"
  :title="isRolledBack ? '已回滚' : '回滚此操作'"
>
  <span v-if="isRolledBack">✓</span>
  <span v-else>↶</span>
</button>
```

---

## 🔌 后端实现

### 1. 修改 EditFileTool.kt

```kotlin
class EditFileTool(private val project: Project) {

    fun execute(arguments: Map<String, Any>): Any {
        val filePath = arguments["filePath"] as? String ?: return error("...")
        val toolUseId = arguments["_toolUseId"] as? String  // 从上下文获取

        // ... 验证逻辑 ...

        // 在修改前创建 Local History 标签
        val label = LocalHistory.getInstance().putSystemLabel(
            project,
            "Before Edit: ${File(filePath).name}"
        )

        // 记录标签
        if (toolUseId != null) {
            FileChangeLabelCache.record(toolUseId, FileChangeLabel(
                toolUseId = toolUseId,
                filePath = filePath,
                operationType = "edit",
                label = label,
                timestamp = System.currentTimeMillis(),
                description = "Edit ${File(filePath).name}"
            ))
        }

        // 执行编辑操作
        return performEdit(filePath, oldString, newString, replaceAll)
    }
}
```

### 2. 修改 WriteFileTool.kt

```kotlin
class WriteFileTool(private val project: Project) {

    fun execute(arguments: Map<String, Any>): Any {
        val filePath = arguments["filePath"] as? String ?: return error("...")
        val toolUseId = arguments["_toolUseId"] as? String

        // 检查文件是否存在（决定是创建还是覆盖）
        val file = File(filePath)
        val isNewFile = !file.exists()

        // 如果是覆盖已有文件，创建 Local History 标签
        if (!isNewFile) {
            val label = LocalHistory.getInstance().putSystemLabel(
                project,
                "Before Write: ${file.name}"
            )

            if (toolUseId != null) {
                FileChangeLabelCache.record(toolUseId, FileChangeLabel(
                    toolUseId = toolUseId,
                    filePath = filePath,
                    operationType = "write",
                    label = label,
                    timestamp = System.currentTimeMillis(),
                    description = "Write ${file.name}"
                ))
            }
        }

        // 执行写入操作
        return performWrite(filePath, content, overwrite, createDirs)
    }
}
```

### 3. 新建 RollbackFileTool.kt

```kotlin
class RollbackFileTool(private val project: Project) {

    fun execute(arguments: Map<String, Any>): Any {
        val toolUseId = arguments["toolUseId"] as? String
            ?: return ToolResult.error("Missing required parameter: toolUseId")

        // 从缓存获取标签
        val changeLabel = FileChangeLabelCache.get(toolUseId)
            ?: return ToolResult.error("No rollback label found for this operation")

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(changeLabel.filePath)
            ?: return ToolResult.error("File not found: ${changeLabel.filePath}")

        return try {
            // 使用 Label.revert 回滚
            ApplicationManager.getApplication().invokeAndWait {
                WriteCommandAction.runWriteCommandAction(project, "Rollback", null, {
                    changeLabel.label.revert(project, virtualFile)
                })
            }

            // 刷新 VFS
            virtualFile.refresh(false, false)

            buildString {
                appendLine("## Rollback Successful")
                appendLine()
                appendLine("**File:** `${changeLabel.filePath}`")
                appendLine("**Operation:** ${changeLabel.description}")
                appendLine("**Reverted to:** State before the operation")
            }
        } catch (e: Exception) {
            logger.error(e) { "Rollback failed: ${changeLabel.filePath}" }
            ToolResult.error("Rollback failed: ${e.message}")
        }
    }
}
```

### 4. HTTP API 端点

```kotlin
// HttpApiServer.kt 添加

"file.rollback" -> {
    val toolUseId = request.data?.jsonObject?.get("toolUseId")?.jsonPrimitive?.content
        ?: return@post call.respondText(
            Json.encodeToString(FrontendResponse(success = false, error = "Missing toolUseId")),
            ContentType.Application.Json
        )

    val result = rollbackFileTool.execute(mapOf("toolUseId" to toolUseId))

    val response = if (result is String) {
        FrontendResponse(success = true, data = JsonPrimitive(result))
    } else {
        FrontendResponse(success = false, error = (result as? ToolResult.Error)?.message)
    }

    call.respondText(Json.encodeToString(response), ContentType.Application.Json)
}

"file.getChanges" -> {
    // 返回当前会话的所有文件变更
    val changes = FileChangeLabelCache.getAll().map { label ->
        buildJsonObject {
            put("id", label.toolUseId)
            put("filePath", label.filePath)
            put("operationType", label.operationType)
            put("timestamp", label.timestamp)
            put("description", label.description)
        }
    }

    call.respondText(
        Json.encodeToString(FrontendResponse(success = true, data = JsonArray(changes))),
        ContentType.Application.Json
    )
}
```

---

## 📋 实施步骤

### Phase 1: 后端标签记录（1 小时）

1. **创建 FileChangeLabelCache**
   - 文件: `jetbrains-plugin/.../mcp/tools/FileChangeLabelCache.kt`
   - 定义 FileChangeLabel 数据类
   - 实现标签缓存管理

2. **修改 EditFileTool**
   - 在 performEdit 前调用 `LocalHistory.putSystemLabel()`
   - 记录标签到缓存

3. **修改 WriteFileTool**
   - 对于覆盖操作，在 performWrite 前创建标签
   - 记录标签到缓存

### Phase 2: 后端回滚 API（1 小时）

4. **创建 RollbackFileTool**
   - 实现 `label.revert()` 调用
   - 处理错误情况

5. **添加 HTTP 端点**
   - `file.rollback`: 执行回滚
   - `file.getChanges`: 获取变更列表

### Phase 3: 前端 UI（2-3 小时）

6. **创建 FileChangesBar 组件**
   - 显示修改文件列表
   - 支持展开/折叠
   - 回滚按钮

7. **修改工具卡片**
   - 添加回滚按钮
   - 显示回滚状态

8. **集成到 ModernChatView**
   - 在输入框上方显示 FileChangesBar

### Phase 4: 测试（1 小时）

9. **功能测试**
   - Edit 回滚
   - Write 回滚
   - 批量回滚

---

## 📁 涉及文件清单

### 新建文件
- `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/tools/FileChangeLabelCache.kt`
- `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/tools/RollbackFileTool.kt`
- `frontend/src/components/chat/FileChangesBar.vue`
- `frontend/src/types/fileChange.ts`

### 修改文件
- `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/tools/EditFileTool.kt`
- `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/tools/WriteFileTool.kt`
- `jetbrains-plugin/src/main/kotlin/com/asakii/server/HttpApiServer.kt` (或 ai-agent-server)
- `frontend/src/components/chat/ModernChatView.vue`
- `frontend/src/components/tools/CompactToolCard.vue`
- `frontend/src/services/ideaBridge.ts`

---

## 🕐 预估工时

| 阶段 | 任务 | 预估时间 |
|------|------|----------|
| Phase 1 | 后端标签记录 | 1 小时 |
| Phase 2 | 后端回滚 API | 1 小时 |
| Phase 3 | 前端 UI | 2-3 小时 |
| Phase 4 | 测试 | 1 小时 |
| **总计** | | **5-6 小时** |

---

## ⚠️ 注意事项

### Local History 限制
- 仅对 VFS 中的文件有效
- 需要 IDEA 启用 Local History（默认启用）
- 标签数量有上限（IDEA 自动清理旧标签）

### 边缘情况
- 文件被删除后无法回滚（Label.revert 会失败）
- 多次编辑同一文件：每次操作都有独立标签，可以分别回滚
- 新建文件：没有"操作前"状态，回滚 = 删除文件

---

## 🚀 下一步

确认此方案后，按 Phase 1 开始实施：
1. 创建 `FileChangeLabelCache.kt`
2. 修改 `EditFileTool.kt` 添加标签记录
3. 修改 `WriteFileTool.kt` 添加标签记录
