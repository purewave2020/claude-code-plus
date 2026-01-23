/**
 * 查找 Ls5 函数（消息输出函数）的精确位置
 */

import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';
import _generate from '@babel/generator';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const traverse = _traverse.default || _traverse;
const generate = _generate.default || _generate;
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const cliPath = path.join(__dirname, 'claude-cli-2.1.17.js');
console.log('Reading CLI source:', cliPath);
const code = fs.readFileSync(cliPath, 'utf-8');

console.log('Parsing AST...');
const ast = parse(code, { sourceType: 'module', plugins: ['jsx'], errorRecovery: true });

console.log('Searching for generator function with parent_tool_use_id...\n');

let found = 0;

// 查找所有生成器函数（包括 FunctionDeclaration 和 FunctionExpression）
traverse(ast, {
  'FunctionDeclaration|FunctionExpression' (path) {
    if (!path.node.generator) return;
    
    try {
      const codeSnippet = generate(path.node).code;
      
      // 检查是否包含关键横式
      if (codeSnippet.includes('parent_tool_use_id:null') &&
          codeSnippet.includes('case"assistant"')) {
        
        found++;
        const funcName = path.node.id?.name || 'anonymous';
        const line = path.node.loc?.start.line;
        
        console.log('=== Generator function #' + found + ' ===');
        console.log('Function name:', funcName);
        console.log('Location: line', line);
        
        // 检查是否是 Ls5 （包含 agent_progress 和多个 parent_tool_use_id）
        const hasAgentProgress = codeSnippet.includes('agent_progress');
        const hasParentToolUseID = codeSnippet.includes('parentToolUseID');
        const hasSwitchAType = codeSnippet.includes('switch(A.type)');
        
        console.log('Has agent_progress:', hasAgentProgress);
        console.log('Has parentToolUseID:', hasParentToolUseID);
        console.log('Has switch(A.type):', hasSwitchAType);
        
        if (hasAgentProgress && hasParentToolUseID && hasSwitchAType) {
          console.log('\n*** THIS IS Ls5 FUNCTION! ***');
          console.log('\nCode (first 2500 chars):');
          console.log(codeSnippet.slice(0, 2500));
          console.log('\n...\n');
          
          // 查找 parent_tool_use_id 的所有使用
          console.log('=== parent_tool_use_id usages in this function ===');
          let count = 0;
          path.traverse({
            ObjectProperty(innerPath) {
              const key = innerPath.node.key;
              if (key.type === 'Identifier' && key.name === 'parent_tool_use_id') {
                count++;
                const value = generate(innerPath.node.value).code;
                const yieldObj = generate(innerPath.parentPath.node).code.slice(0, 200);
                console.log('\n[' + count + '] parent_tool_use_id:', value);
                console.log('Object:', yieldObj);
              }
            }
          });
        }
        console.log('\n');
      }
    } catch (e) {
      // ignore generate errors
    }
  }
});

if (found === 0) {
  console.log('No matching generator functions found.');
} else {
  console.log('Total found:', found);
}
