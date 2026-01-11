/**
 * get_capabilities 补丁
 *
 * 添加功能状态查询控制端点，用于查询 CLI 的运行时能力。
 *
 * 请求格式:
 * {
 *   type: "control_request",
 *   request_id: "xxx",
 *   request: {
 *     subtype: "get_capabilities"
 *   }
 * }
 *
 * 响应格式:
 * {
 *   capabilities: {
 *     background_tasks_enabled: boolean  // !CLAUDE_CODE_DISABLE_BACKGROUND_TASKS
 *     // 未来可扩展更多状态...
 *   }
 * }
 *
 * 实现: 读取 process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS 环境变量
 */

module.exports = {
  id: 'get_capabilities',
  description: 'CLI capabilities query control endpoint',
  priority: 150,  // 在其他补丁之后
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;
    const parser = require('@babel/parser');

    // ========================================
    // 找到控制请求处理位置并添加 get_capabilities 分支
    // ========================================
    traverse(ast, {
      IfStatement(path) {
        if (patchApplied) return;

        const test = path.node.test;

        // 查找 *.request.subtype === "xxx" 模式
        if (!t.isBinaryExpression(test) || test.operator !== '===') return;

        const left = test.left;
        if (!t.isMemberExpression(left)) return;
        if (!t.isIdentifier(left.property) || left.property.name !== 'subtype') return;

        const obj = left.object;
        if (!t.isMemberExpression(obj)) return;
        if (!t.isIdentifier(obj.property) || obj.property.name !== 'request') return;

        const subtypeValue = test.right;
        if (!t.isStringLiteral(subtypeValue)) return;

        const subtype = subtypeValue.value;
        // 在已知的控制命令附近添加
        if (subtype !== 'mcp_set_servers' && subtype !== 'mcp_status' && subtype !== 'mcp_reconnect') return;

        // 找到了控制请求处理位置
        const requestVar = obj.object;

        // 找出响应函数名
        let responderName = 's';
        const consequent = path.node.consequent;
        if (t.isBlockStatement(consequent)) {
          for (const stmt of consequent.body) {
            if (t.isExpressionStatement(stmt) && t.isCallExpression(stmt.expression)) {
              const callee = stmt.expression.callee;
              if (t.isIdentifier(callee)) {
                responderName = callee.name;
                break;
              }
            }
            // 检查 IIFE 内部
            if (t.isExpressionStatement(stmt)) {
              const expr = stmt.expression;
              if (t.isCallExpression(expr) && t.isArrowFunctionExpression(expr.callee)) {
                const body = expr.callee.body;
                if (t.isBlockStatement(body)) {
                  for (const innerStmt of body.body) {
                    if (t.isExpressionStatement(innerStmt) && t.isCallExpression(innerStmt.expression)) {
                      const innerCallee = innerStmt.expression.callee;
                      if (t.isIdentifier(innerCallee)) {
                        responderName = innerCallee.name;
                        break;
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 构建 get_capabilities 处理逻辑
        // 读取 process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS
        // 使用与 CLI 内部 i1() 函数相同的逻辑解析布尔值
        // i1() 检查: "1", "true", "yes", "on" (不区分大小写)
        // 返回 { background_tasks_enabled: !disabled }
        
        const handlerCode = `
          var __disableBackgroundTasks = (process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS || '').toLowerCase();
          var __isDisabled = __disableBackgroundTasks === '1' || __disableBackgroundTasks === 'true' || __disableBackgroundTasks === 'yes' || __disableBackgroundTasks === 'on';
          var __backgroundEnabled = !__isDisabled;
          ${responderName}({
            ...${requestVar.name},
            response: {
              subtype: "capabilities",
              request_id: ${requestVar.name}.request_id,
              response: {
                capabilities: {
                  background_tasks_enabled: __backgroundEnabled
                }
              }
            }
          });
        `;

        // 解析处理代码
        const handlerAst = parser.parse(handlerCode, {
          sourceType: 'script'
        });

        // 创建新的 if 语句
        const newIfStatement = t.ifStatement(
          t.binaryExpression(
            '===',
            t.memberExpression(
              t.memberExpression(t.cloneNode(requestVar), t.identifier('request')),
              t.identifier('subtype')
            ),
            t.stringLiteral('get_capabilities')
          ),
          t.blockStatement(handlerAst.program.body)
        );

        // 在当前 if 语句后插入新的 if 语句
        const parentPath = path.parentPath;
        if (parentPath && parentPath.isBlockStatement()) {
          const siblings = parentPath.node.body;
          const index = siblings.indexOf(path.node);
          if (index !== -1) {
            siblings.splice(index + 1, 0, newIfStatement);
            patchApplied = true;
            details.push(`添加 get_capabilities 控制命令处理 (responder: ${responderName})`);
          }
        } else if (path.node.alternate === null) {
          // 如果没有 else 分支，添加为 else if
          path.node.alternate = newIfStatement;
          patchApplied = true;
          details.push(`添加 get_capabilities 作为 else if 分支 (responder: ${responderName})`);
        }
      }
    });

    if (!patchApplied) {
      return { success: false, error: '未找到控制请求处理位置' };
    }

    return { success: true, details };
  }
};
