/**
 * skill-parent-tool-use-id 补丁
 *
 * 为 Skill 工具的内部消息添加 parent_tool_use_id 支持
 *
 * 原理：
 * - CLI 中的 Ls5 函数负责输出消息
 * - 对于普通的 assistant/user 消息，parent_tool_use_id 被硬编码为 null
 * - Skill 内部已经通过 Nd2 函数添加了 sourceToolUseID 属性
 * - 但在输出时 sourceToolUseID B被丢弃了
 *
 * 修改：
 * - 在 Ls5 函数中，将 assistant 和 user case 中的
 *   parent_tool_use_id:null 改为 parent_tool_use_id:Q.sourceToolUseID||null
 */

module.exports = {
  id: 'skill_parent_tool_use_id',
  description: 'Add parent_tool_use_id support for Skill internal messages',
  priority: 60,  // 在 parent_uuid 补丁之后
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;
    let ls5FuncName = null;

    // 查找 Ls5 函数（一个 generator 函数，包含 switch(A.type)，
    // 并在 assistant 和 user case 中 yield 带有 parent_tool_use_id:null 的对象）
    traverse(ast, {
      FunctionDeclaration(path) {
        if (patchApplied) return;
        if (!path.node.generator) return;

        // 检查函数体是否包含关键代码模式
        let hasSwitchAType = false;
        let hasAssistantCase = false;
        let hasUserCase = false;
        let hasProgressCase = false;
        let hasParentToolUseID = false;

        path.traverse({
          SwitchStatement(innerPath) {
            const disc = innerPath.node.discriminant;
            if (t.isMemberExpression(disc) &&
                t.isIdentifier(disc.object) &&
                t.isIdentifier(disc.property) &&
                disc.property.name === 'type') {
              hasSwitchAType = true;
            }
          },
          SwitchCase(innerPath) {
            const test = innerPath.node.test;
            if (t.isStringLiteral(test)) {
              if (test.value === 'assistant') hasAssistantCase = true;
              if (test.value === 'user') hasUserCase = true;
              if (test.value === 'progress') hasProgressCase = true;
            }
          },
          MemberExpression(innerPath) {
            if (t.isIdentifier(innerPath.node.property) &&
                innerPath.node.property.name === 'parentToolUseID') {
              hasParentToolUseID = true;
            }
          }
        });

        // 确认是否 Ls5 函数
        if (!hasSwitchAType || !hasAssistantCase || !hasUserCase || 
            !hasProgressCase || !hasParentToolUseID) {
          return;
        }

        ls5FuncName = path.node.id?.name || 'anonymous';
        details.push(`找到 Ls5 函数: ${ls5FuncName}`);

        // 查找并修改 assistant 和 user case 中的 parent_tool_use_id:null
        let modifiedCount = 0;

        path.traverse({
          SwitchCase(casePath) {
            const test = casePath.node.test;
            if (!t.isStringLiteral(test)) return;
            
            // 只处理 assistant 和 user case
            if (test.value !== 'assistant' && test.value !== 'user') return;

            // 在这个 case 内查找 parent_tool_use_id:null 并 修改
            casePath.traverse({
              ObjectProperty(propPath) {
                const key = propPath.node.key;
                const value = propPath.node.value;

                // 检查是否是 parent_tool_use_id: null
                if (t.isIdentifier(key) && key.name === 'parent_tool_use_id' &&
                    t.isNullLiteral(value)) {
                  
                  // 找到循环中使用的变量名（如为 for 循环中的 Q）
                  // 通过查找包含这个属性的对象所在的 for 语句
                  let loopVarName = 'Q';  // 默认
                  
                  // 向上查找包含 yield 的 for 语句
                  let parent = propPath.parentPath;
                  while (parent && !t.isForStatement(parent.node) && 
                         !t.isForOfStatement(parent.node)) {
                    parent = parent.parentPath;
                  }

                  if (parent && t.isForOfStatement(parent.node)) {
                    const left = parent.node.left;
                    if (t.isVariableDeclaration(left) && left.declarations.length > 0) {
                      const decl = left.declarations[0];
                      if (t.isIdentifier(decl.id)) {
                        loopVarName = decl.id.name;
                      }
                    }
                  }

                  // 将 parent_tool_use_id:null 改为 parent_tool_use_id:Q.sourceToolUseID||null
                  propPath.node.value = t.logicalExpression(
                    '||',
                    t.memberExpression(
                      t.identifier(loopVarName),
                      t.identifier('sourceToolUseID')
                    ),
                    t.nullLiteral()
                  );

                  modifiedCount++;
                  details.push(`修改 ${test.value} case 中的 parent_tool_use_id: ${loopVarName}.sourceToolUseID||null`);
                }
              }
            });
          }
        });

        if (modifiedCount > 0) {
          patchApplied = true;
          details.push(`共修改了 ${modifiedCount} 处 parent_tool_use_id`);
          path.stop();
        }
      }
    });

    if (patchApplied) {
      // 将发现的变量名存储到 context
      if (ls5FuncName) {
        context.foundVariables = context.foundVariables || {};
        context.foundVariables.ls5FuncName = ls5FuncName;
      }

      return {
        success: true,
        details
      };
    }

    return {
      success: false,
      reason: '未找到 Ls5 函数或其中的 parent_tool_use_id:null 属性'
    };
  }
};
