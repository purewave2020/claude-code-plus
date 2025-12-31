package com.asakii.claude.agent.sdk.examples

import com.asakii.claude.agent.sdk.*
import com.asakii.claude.agent.sdk.mcp.*
import com.asakii.claude.agent.sdk.mcp.annotations.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * 增强的 MCP 功能使用示例
 * 演示：
 * 1. 参数描述支持 - @ToolParam 注解提供参数说明
 * 2. 简化的字符串到类型转换 - AI 传递的字符串参数自动转换为正确类型
 * 3. 错误处理 - 转换失败时的明确错误信息
 */

@McpServerConfig(
    name = "file_processor",
    version = "2.1.0", 
    description = "文件处理工具集，支持参数验证和自动类型转换"
)
class FileProcessorServer : McpServerBase() {

    @McpTool("创建指定大小的文件")
    suspend fun createFile(
        @ToolParam("文件路径，例如 /tmp/test.txt") 
        filePath: String,
        
        @ToolParam("文件大小（字节），范围0到1GB，例如1024") 
        sizeBytes: Long,
        
        @ToolParam("是否覆盖已存在的文件，可选，默认false") 
        overwrite: Boolean = false
    ): String {
        // AI 会传递字符串 "1024" 给 sizeBytes，自动转换为 Long
        // AI 会传递字符串 "true" 给 overwrite，自动转换为 Boolean
        
        if (sizeBytes <= 0) {
            throw IllegalArgumentException("文件大小必须大于0")
        }
        
        return "创建文件: $filePath (${sizeBytes}字节, 覆盖: $overwrite)"
    }

    @McpTool("分析文件统计信息")
    suspend fun analyzeFile(
        @ToolParam("File path") 
        filePath: String,

        @ToolParam("Analysis depth (default 1, range 1-5)") 
        depth: Int = 1,

        @ToolParam("Include hidden files (default false)")
        includeHidden: Boolean = false,

        @ToolParam("Output format (default json)")
        format: String = "json"
    ): JsonObject {
        return buildJsonObject {
            put("path", filePath)
            put("depth", depth)
            put("includeHidden", includeHidden)
            put("format", format)
            put("fileCount", (10..100).random())
            put("totalSize", (1024..1048576).random())
        }
    }


    @McpTool("批量重命名文件")
    suspend fun batchRename(
        @ToolParam("Source directory") 
        sourceDir: String,

        @ToolParam("Filename pattern (e.g. file_*)") 
        pattern: String,

        @ToolParam("New name prefix (max 50 chars)") 
        prefix: String,

        @ToolParam("Start number (default 1, range 1-9999)")
        startNumber: Int = 1,

        @ToolParam("Dry run (default true)")
        dryRun: Boolean = true
    ): JsonArray {
        val fileCount = (3..8).random()
        return buildJsonArray {
            (0 until fileCount).forEach { index ->
                val num = startNumber + index
                add(buildJsonObject {
                    put("oldName", "old_file_$index.txt")
                    put("newName", "${prefix}_${num}.txt")
                    put("action", if (dryRun) "preview" else "rename")
                })
            }
        }
    }

}

/**
 * 使用示例函数
 */
suspend fun demonstrateEnhancedMcp() {
    println("=== 🚀 增强 MCP 功能演示 ===\n")
    
    val server = FileProcessorServer()
    
    // 1. 展示参数描述功能
    println("📋 工具定义（包含参数描述）:")
    val tools = server.listTools()
    tools.forEach { tool ->
        println("\n🔧 ${tool.name}: ${tool.description}")
        val schema = tool.inputSchema
        val properties = schema["properties"]?.jsonObject ?: buildJsonObject { }
        val required = schema["required"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            ?: emptySet()

        properties.forEach { (paramName, paramSchemaElement) ->
            val paramSchema = paramSchemaElement.jsonObject
            val description = paramSchema["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = paramSchema["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val requiredMark = if (required.contains(paramName)) "*" else ""
            println("  -$paramName$requiredMark ($type): $description")

            paramSchema["example"]?.let { println("    example: ${it.jsonPrimitive.contentOrNull ?: it.toString()}") }
            paramSchema["minimum"]?.let { println("    minimum: ${it.jsonPrimitive.contentOrNull ?: it.toString()}") }
            paramSchema["maximum"]?.let { println("    maximum: ${it.jsonPrimitive.contentOrNull ?: it.toString()}") }
            paramSchema["minLength"]?.let { println("    minLength: ${it.jsonPrimitive.contentOrNull ?: it.toString()}") }
            paramSchema["maxLength"]?.let { println("    maxLength: ${it.jsonPrimitive.contentOrNull ?: it.toString()}") }
        }

    }
    
    println("\n" + "=".repeat(50))
    
    // 2. 演示字符串到类型的自动转换
    println("\n🔄 字符串类型转换演示:")
    
    println("\n1️⃣ 创建文件 (字符串 -> Long, Boolean)")
    val createResult = server.callTool("createFile", buildJsonObject {
        put("filePath", "/tmp/example.txt")
        put("sizeBytes", "2048")
        put("overwrite", "true")
    })
    println("   结果: ${(createResult as ToolResult.Success).content.first()}")
    
    println("\n2️⃣ 文件分析 (字符串 -> Int, Boolean)")
    val analyzeResult = server.callTool("analyzeFile", buildJsonObject {
        put("filePath", "/home/user/documents")
        put("depth", "3")
        put("includeHidden", "false")
        put("format", "detailed")
    })
    println("   结果: ${(analyzeResult as ToolResult.Success).content.first()}")
    
    println("\n3️⃣ 批量重命名 (混合类型转换)")
    val renameResult = server.callTool("batchRename", buildJsonObject {
        put("sourceDir", "/tmp/photos")
        put("pattern", "IMG_*")
        put("prefix", "vacation")
        put("startNumber", "100")
        put("dryRun", "false")
    })
    println("   结果: ${(renameResult as ToolResult.Success).content.first()}")
    
    println("\n" + "=".repeat(50))
    
    // 3. 演示错误处理
    println("\n❌ 错误处理演示:")
    
    println("\n🚫 无效数值转换")
    val invalidResult = server.callTool("createFile", buildJsonObject {
        put("filePath", "/tmp/test.txt")
        put("sizeBytes", "not_a_number")
        put("overwrite", "true")
    })
    println("   错误: ${(invalidResult as ToolResult.Error).error}")
    
    println("\n🚫 业务逻辑错误")
    val businessErrorResult = server.callTool("createFile", buildJsonObject {
        put("filePath", "/tmp/test.txt")
        put("sizeBytes", "-100")
        put("overwrite", "true")
    })
    println("   错误: ${(businessErrorResult as ToolResult.Error).error}")
    
    println("\n✅ 增强 MCP 功能演示完成!")
}

/**
 * 主函数 - 运行演示
 */
suspend fun main() {
    demonstrateEnhancedMcp()
}
