/**
 * 验证增强后的 CLI 代码
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

const enhancedPath = path.join(__dirname, '../src/main/resources/bundled/claude-cli-2.1.12-enhanced.mjs');
console.log('Reading enhanced CLI:', enhancedPath);
const code = fs.readFileSync(enhancedPath, 'utf-8');

console.log('Parsing AST...');
let ast;
try {
  ast = parse(code, { sourceType: 'module', plugins: ['jsx'], errorRecovery: true });
  console.log('AST ParSe SUCCESS\n');
} catch (e) {
  console.error('AST PARSE FAILED:', e.message);
  process.exit(1);
}

console.log('=== Verifying skill_parent_tool_use_id patch ===\n');

let foundLs5 = false;
let sourceToolUseIDCount = 0;
let parentToolUseIdNullCount = 0;
let parentToolUseIdNonNullCount = 0;

traverse(ast, {
  FunctionDeclaration(path) {
    if (!path.node.generator) return;
    if (path.node.id?.name !== 'Ls5') return;
    
    foundLs5 = true;
    console.log('Found Ls5 function at line', path.node.loc?.start.line);
    
    // Check parent_tool_use_id usages
    path.traverse({
      ObjectProperty(innerPath) {
        const key = innerPath.node.key;
        if (key.type === 'Identifier' && key.name === 'parent_tool_use_id') {
          const value = generate(innerPath.node.value).code;
          if (value.includes('sourceToolUseID')) {
            sourceToolUseIDCount++;
            console.log('  [OK] parent_tool_use_id:', value);
          } else if (value === 'null') {
            parentToolUseIdNullCount++;
            console.log('  [SKIP] parent_tool_use_id: null (progress/tool_progress case)');
          } else {
            parentToolUseIdNonNullCount++;
            console.log('  [OK - non-null] parent_tool_use_id:', value);
          }
        }
      }
    });
    
    path.stop();
  }
});

console.log('\n=== Verification Results ===');
console.log('Found Ls5 function:', foundLs5 ? 'YES' : 'NO');
console.log('sourceToolUseID usages:', sourceToolUseIDCount);
console.log('parentToolUseID usages (non-null):', parentToolUseIdNonNullCount);
console.log('null usages (progress/tool_progress cases):', parentToolUseIdNullCount);

if (foundLs5 && sourceToolUseIDCount >= 2) {
  console.log('\nVERIFICATION PASSED: skill_parent_tool_use_id patch is correctly applied');
} else {
  console.log('\nVERIFICATION FAILED');
  process.exit(1);
}
