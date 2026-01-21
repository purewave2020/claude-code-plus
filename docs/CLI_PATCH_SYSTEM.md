# CLI 补丁系统文档

## 概述

Claude Code Plus 使用 AST 补丁系统来增强官方 Claude CLI，添加控制端点和额外功能。由于 Claude CLI 代码是混淆的，变量名会在每个版本中变化，补丁系统需要通过代码特征动态发现关键变量和函数名。

## 架构

```
claude-agent-sdk/cli-patches/
├── patches/                    # 补丁文件
│   ├── 001-run-in-background.js
│   ├── 002-chrome-status.js
│   ├── 003-parent-uuid.js
│   ├── 004-mcp-reconnect.js    # MCP 重连控制
│   ├── 005-mcp-tools.js
│   ├── 006-mcp-disable-enable.js # MCP 禁用/启用控制
│   ├── 007-run-to-background.js
│   ├── 008-get-capabilities.js
│   └── index.js
├── patch-cli.js                # 补丁应用主程序
├── claude-cli-2.1.12.js        # 官方 CLI 源码
└── analyze-*.js                # 分析脚本
```

## 补丁执行流程

1. **解析 AST**: 使用 Babel 解析 CLI 源码为 AST
2. **按优先级应用补丁**: 补丁按 `priority` 字段排序执行
3. **共享上下文**: 补丁通过 `context.foundVariables` 共享发现的变量名
4. **生成代码**: 将修改后的 AST 生成为新的 CLI 代码

## 关键概念

### 变量发现机制

由于 CLI 代码混淆，变量名如 `configs`、`clients` 在每个版本中可能不同。补丁通过代码特征（如对象结构、函数签名）动态发现这些变量名。

**示例**: 在 `mcp_set_servers` 处理代码中查找 `{configs:X,clients:Y,tools:Z}` 对象表达式：

```javascript
// CLI 代码中的模式
{configs:J,clients:S,tools:f}

// 补丁发现逻辑
traverse(ast, {
  ObjectExpression(objPath) {
    const props = objPath.node.properties;
    for (const prop of props) {
      if (prop.key.name === 'configs' && t.isIdentifier(prop.value)) {
        configsVarName = prop.value.name;  // 发现 "J"
      }
      if (prop.key.name === 'clients' && t.isIdentifier(prop.value)) {
        clientsVarName = prop.value.name;  // 发现 "S"
      }
    }
  }
});
```

### 补丁上下文共享

补丁通过 `context.foundVariables` 对象在补丁之间共享发现的变量名：

```javascript
// 004-mcp-reconnect.js 中发现并存储
context.foundVariables.mcpConfigsVar = configsVarName;  // "J"
context.foundVariables.mcpClientsVar = clientsVarName;  // "S"
context.foundVariables.reconnectFn = reconnectFnName;   // "x2A"

// 006-mcp-disable-enable.js 中使用
const configsVarName = context.foundVariables?.mcpConfigsVar;
const clientsVarName = context.foundVariables?.mcpClientsVar;
const reconnectFnName = context.foundVariables?.reconnectFn;
```

## MCP 相关补丁

### 004-mcp-reconnect.js

**功能**: 添加 MCP 服务器重连控制端点

**控制命令**: `mcp_reconnect`

**发现的变量**:
- `reconnectFn`: 重连函数名 (如 `x2A`)
- `mcpConfigsVar`: configs 变量名 (如 `J`)
- `mcpClientsVar`: clients 变量名 (如 `S`)

**发现逻辑**:
1. 查找 `mcp_set_servers` 处理位置
2. 在其中查找 `{configs:X,clients:Y,...}` 对象表达式
3. 提取 X 作为 configs 变量名，Y 作为 clients 变量名

### 006-mcp-disable-enable.js

**功能**: 添加 MCP 服务器禁用/启用控制端点

**控制命令**: `mcp_disable`, `mcp_enable`

**依赖**: 使用 004 补丁发现的 `mcpConfigsVar` 和 `mcpClientsVar`

**发现的变量**:
- `checkDisabledFn`: 检查禁用状态函数 (如 `lPA`)
- `updateDisabledFn`: 更新禁用配置函数 (如 `CY0`)
- `disconnectFn`: 断开连接函数 (如 `gm`)

## CLI 2.1.12 变量映射

| 用途 | 变量名 | 发现特征 |
|------|--------|----------|
| MCP configs 对象 | `J` | `{configs:J,...}` 在 mcp_set_servers 处理中 |
| MCP clients 数组 | `S` | `{clients:S,...}` 在 mcp_set_servers 处理中 |
| 重连函数 | `x2A` | async 函数，返回 `{client, tools}` |
| 检查禁用函数 | `lPA` | 包含 `disabledMcpServers` 和 `includes` |
| 更新禁用函数 | `CY0` | 包含 `disabledMcpServers` 和 `filter` |
| 断开连接函数 | `gm` | async 函数，包含 `cleanup` 调用 |

## 常见问题

### "y is not defined" 错误

**原因**: 补丁硬编码使用了变量名 `y`，但在当前 CLI 版本中该变量名已变化。

**解决方案**: 使用变量发现机制动态获取正确的变量名，而不是硬编码。

### 补丁应用失败

**排查步骤**:
1. 检查 CLI 版本是否更新
2. 运行 `node patch-cli.js --dry-run <cli.js>` 查看补丁应用状态
3. 使用 `analyze-*.js` 脚本分析 CLI 结构变化
4. 更新补丁的特征匹配逻辑

## 开发指南

### 添加新补丁

1. 在 `patches/` 目录创建新文件
2. 导出包含 `id`, `description`, `priority`, `apply()` 的对象
3. 使用 `context.foundVariables` 访问或存储发现的变量名
4. 在 `patches/index.js` 中注册补丁

### 更新现有补丁

当 CLI 版本更新导致补丁失效时：

1. 使用分析脚本查找新的代码特征
2. 更新补丁中的特征匹配逻辑
3. 确保使用动态变量发现而非硬编码

### 测试补丁

```bash
cd claude-agent-sdk/cli-patches

# 干运行模式（仅验证，不生成文件）
node patch-cli.js --dry-run claude-cli-2.1.12.js

# 应用补丁
node patch-cli.js claude-cli-2.1.12.js patched-cli.js
```

## 变更历史

### 2026-01-20

- **修复**: 004-mcp-reconnect.js 和 006-mcp-disable-enable.js 补丁中的硬编码变量名问题
- **改进**: 添加动态变量发现机制，从 `mcp_set_servers` 处理代码中提取 `configs` 和 `clients` 变量名
- **影响**: 修复 `user_interaction` MCP 服务器连接失败问题 ("y is not defined" 错误)

### 2026-01-21

- **����**: Skill ���ߵ�  �������

---

## Skill/Task �ӻỰ��Ϣ�������

### ���ⱳ��

ͨ�� SDK �������Ϣ�У�Task �Ӵ�������Ϣ����ȷ�� ���� Skill ���ڲ���Ϣ  ʼ��Ϊ ��

### AST �������

ʹ��  �ű����� CLI Դ����֣�

**�ؼ���������**:
-  (line 181)
-  (line 2530)

**parent_tool_use_id ����ͳ��**:
- �� null ֵ: 3 �� (��Ϊ )
- null ֵ: 14 �� (Ӳ����)

**sourceToolUseID ����**: line 3090


### ��Ϣ������ĺ��� (Ls5)

λ��: line 2663 ����



### ����ԭ��

| | Task | Skill |
|---|------|-------|
| ���ӻỰ | Yes | Yes |
| ��Ϣ���·�� |  ->  | ��ͨ / |
| parent_tool_use_id |  (��ֵ) | Ӳ����  |
| �ڲ�׷�ٻ��� |  |  |

**Task �Ӵ���**: ͨ��  ���͵�  �¼�������Ϣ��Я�� ��

**Skill**: �ڲ�ͨ��  ��������Ϣ������ ������  �������ʱ��������Ӳ����Ϊ ��

### Skill �ڲ�׷�ٻ��� (Nd2 ����)

λ��: line 2530 ����



Skill ������  �����е���:


### ��Ʋ�һ��

�⿴������ CLI ��һ��**��Ʋ�һ��**��**��©**��
- Task �� Skill �����ӻỰ
- �����ڲ�����׷�ٻ��� ( vs )
- ��ֻ�� Task �����ʱ��ȷ������ 

### ��������

**Ŀ��**: ��  �����У������Ϣ�Ƿ��� ���������������Ϊ ��

**�޸ĵ�**: ��Ӳ�����  ��Ϊ��̬���:


**��ʵ��**: �����ļ�  (������)

\


### 2026-01-21

- **分析**: Skill 工具的 `parent_tool_use_id` 输出问题

---

## Skill/Task 子会话消息输出分析

### 问题背景

SDK 输出的消息中，Task 子代理的消息有正确的 `parent_tool_use_id`，但 Skill 的内部消息 `parent_tool_use_id` 始终为 `null`。

### AST 分析结果

运行 `ast-analyzer.mjs` 脚本分析 CLI 源码后发现：

- **Task 定义**: `P6 = "Task"` (line 181)
- **Skill 定义**: `xV = "Skill"` (line 2530)

**parent_tool_use_id 属性统订**:
- 非 null 值: 3 处 (均为 `A.parentToolUseID`)
- null 值: 14 处 (硬编码)

**sourceToolUseID 访问**: line 3090

### 消息输出核心函数 (Ls5)

位置: line 2663 附近

- 普通 `assistant`/`user` 消息：硬编码 `parent_tool_use_id: null`
- `progress` 类型的 `agent_progress`：使用 `A.parentToolUseID`

### 核本原因  

| | Task | Skill |
|---|------|-------|
| 是子会话 | Yes | Yes |
| 消息输出路径 | `progress` -> `agent_progress` | 普通 `assistant`/`user` |
| parent_tool_use_id | `A.parentToolUseID` (有值) | 硫叆码 `null` |
| 内部追踪机制 | `parentToolUseID` | `sourceToolUseID` |

**Task 子代理**: 通过 `progress` 类型的 `agent_progress` 事件发送消息，携带 `parentToolUseID`。

**Skill**: 内部通过 `Nd2` 函数给消息添加了 `sourceToolUseID`，但在 `Ls5` 函数输出时被丢弃，硬编码为 `null`。

### Skill 内部追踪机制

`Nd2` 函数 (位于 line 2530 附近)：
- 为 `user` 类型消息添加 `sourceToolUseID` 属性
- Skill 工具在 `call()` 方法中调用此函数

### 设计不一致

这是 CLI 的一个 **设计不一致** 或 **遗漏**：
- Task 和 Skill 都是子会话
- 两者内部都有追踪机制

- 但只有 Task 在输出时正确传递了 `parent_tool_use_id`

### 补丁方案

**目标**: 在 `Ls5` 函数中，检查信息是否有 `sourceToolUseID`，如果有则作为 `parent_tool_use_id`。

**修改点**: 将硬编码的 `parent_tool_use_id:null` 改为动态检查 `Q.sourceToolUseID || null`

**待实现**: 补丁文件 `008-skill-parent-tool-use-id.js`


---



### 语法验证工具

增强后的 CLI 必须通过语法验证：

```bash
node syntax-validator.mjs [enhanced-cli-path]
```

验证项目：
1. **Babel 严格模式**: 语法错误检测
2. **危险模式**: `undefined.x`, `null.x` 等
3. **补丁完整性**: 控制命令存在性
4. **AST 验证**: 注入代码结构

**背景**: 曾因补丁硬编码变量名导致 "y is not defined" 运行时错误。

## 升级检查流程

### 重要提醒

**每次升级官方 Claude CLI 版本时，必须执行以下检查流程**，确认官方是否已修复我们补丁解决的问题。

### 升级前检查清单

1. **获取新版本 CLI 源码**
2. **使用 AST 分析工具检查关键代码**
3. **对每个补丁检查官方是否已修复**

### 决策流程

- 官方已修复某补丁 -> 移除对应补丁，更新 patches/index.js
- 官方未修复 -> 保留补丁
- 重新生成增强 CLI 并验证功能

### 注意事项

- 不要盲目升级，即使官方版本号更新
- 保持 AST 工具更新
- 升级后必须测试所有增强功能
- 官方每次更新可能改变混淆后的变量名




---

## 升级检查流程

### 重要提醒

**每次升级官方 Claude CLI 版本时，必须执行以下检查流程**，确认官方是否已修复我们补丁解决的问题。

### 升级前检查清单

1. 获取新版本 CLI 源码
2. 使用 AST 分析工具检查关键代码
3. 对每个补丁检查官方是否已修复

### 决策流程

- 官方已修复某补丁 -> 移除对应补丁，更新 patches/index.js
- 官方未修复 -> 保留补丁
- 重新生成增强 CLI 并验证功能

### 补丁检查表

| 补丁 | 检查方法 | 官方已修复标志 |
|------|----------|---------------|
| 009-skill-parent-tool-use-id | 检查 Ls5 函数 | parent_tool_use_id 使用动态值 |
| 007-run-to-background | 检查控制端点 | 官方支持后台化 |
| 004-mcp-reconnect | 检查控制端点 | 官方支持 MCP 重连 |

### 注意事项

- 不要盲目升级，即使官方版本号更新
- 保持 AST 工具更新
- 升级后必须测试所有增强功能
- 官方每次更新可能改变混淆后的变量名
