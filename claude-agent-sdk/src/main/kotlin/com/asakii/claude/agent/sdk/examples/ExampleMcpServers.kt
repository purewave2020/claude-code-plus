package com.asakii.claude.agent.sdk.examples

import com.asakii.claude.agent.sdk.mcp.*
import com.asakii.claude.agent.sdk.mcp.annotations.*
import com.asakii.claude.agent.sdk.builders.*
import kotlinx.serialization.json.*

/**
 * MCP Server 实现示例 - 展示简化后的最佳实现方式
 */

/**
 * 示例1: 最简单的计算器服务器 - 只需要类和注解
 */
class CalculatorServer : McpServerBase() {
    
    @McpTool("计算两个数的和")
    suspend fun add(a: Double, b: Double): Double = a + b
    
    @McpTool("计算两个数的差")
    suspend fun subtract(a: Double, b: Double): Double = a - b
    
    @McpTool("计算两个数的乘积")
    suspend fun multiply(a: Double, b: Double): Double = a * b
    
    @McpTool("计算两个数的商")
    suspend fun divide(dividend: Double, divisor: Double): Double {
        if (divisor == 0.0) {
            throw IllegalArgumentException("除数不能为零")
        }
        return dividend / divisor
    }
    
    @McpTool("计算数的幂")
    suspend fun power(base: Double, exponent: Double): Double {
        return Math.pow(base, exponent)
    }
    
    @McpTool("计算平方根")
    suspend fun sqrt(number: Double): Double {
        if (number < 0) {
            throw IllegalArgumentException("不能计算负数的平方根")
        }
        return Math.sqrt(number)
    }
}

/**
 * 示例2: 文本处理服务器
 */
@McpServerConfig(description = "文本处理工具服务器")
class TextProcessorServer : McpServerBase() {
    
    @McpTool("将文本转换为大写")
    suspend fun toUpperCase(text: String): String = text.uppercase()
    
    @McpTool("将文本转换为小写") 
    suspend fun toLowerCase(text: String): String = text.lowercase()
    
    @McpTool("反转文本")
    suspend fun reverse(text: String): String = text.reversed()
    
    @McpTool("计算文本长度和统计信息")
    suspend fun analyzeText(text: String): JsonObject {
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return buildJsonObject {
            put("length", text.length)
            put("wordCount", words.size)
            put("lineCount", text.split("\n").size)
            put("isEmpty", text.isEmpty())
            put("isBlank", text.isBlank())
        }
    }
    
    @McpTool("替换文本内容")
    suspend fun replaceText(text: String, target: String, replacement: String): String {
        return text.replace(target, replacement)
    }
}

/**
 * 示例3: 数学工具服务器（更复杂的示例）
 */
@McpServerConfig(version = "2.0.0", description = "高级数学计算工具")
class MathToolsServer : McpServerBase() {
    
    @McpTool("计算阶乘")
    suspend fun factorial(n: Int): Long {
        if (n < 0) throw IllegalArgumentException("阶乘输入不能为负数")
        if (n > 20) throw IllegalArgumentException("阶乘输入过大")
        
        var result = 1L
        for (i in 1..n) {
            result *= i
        }
        return result
    }
    
    @McpTool("计算斐波那契数")
    suspend fun fibonacci(n: Int): Long {
        if (n < 0) throw IllegalArgumentException("斐波那契输入不能为负数")
        if (n > 40) throw IllegalArgumentException("斐波那契输入过大")
        
        fun fib(num: Int): Long = when (num) {
            0 -> 0
            1 -> 1
            else -> fib(num - 1) + fib(num - 2)
        }
        
        return fib(n)
    }
    
    @McpTool("检查是否为质数")
    suspend fun isPrime(n: Int): JsonObject {
        if (n < 2) {
            return buildJsonObject {
                put("isPrime", false)
                put("number", n)
                put("reason", "Numbers less than 2 are not prime")
            }
        }

        for (i in 2..Math.sqrt(n.toDouble()).toInt()) {
            if (n % i == 0) {
                return buildJsonObject {
                    put("isPrime", false)
                    put("number", n)
                    put("divisor", i)
                    put("reason", "Divisible by $i")
                }
            }
        }

        return buildJsonObject {
            put("isPrime", true)
            put("number", n)
        }
    }

    
    @McpTool("计算最大公约数")
    suspend fun gcd(a: Int, b: Int): Int {
        var x = Math.abs(a)
        var y = Math.abs(b)
        
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        
        return x
    }
}

/**
 * 示例4: 系统工具服务器（使用手动注册方式）
 */
class SystemToolsServer : McpServerBase() {
    
    @McpTool("获取当前时间戳")
    suspend fun getCurrentTimestamp(): JsonObject {
        val timestamp = System.currentTimeMillis()
        return buildJsonObject {
            put("timestamp", timestamp)
            put("iso", java.time.Instant.ofEpochMilli(timestamp).toString())
            put("readable", java.time.LocalDateTime.now().toString())
        }
    }

    
    @McpTool("生成随机数")
    suspend fun randomNumber(min: Int = 0, max: Int = 100): JsonObject {
        if (min >= max) {
            throw IllegalArgumentException("min must be less than max")
        }

        val random = kotlin.random.Random.nextInt(min, max + 1)
        return buildJsonObject {
            put("value", random)
            put("min", min)
            put("max", max)
            put("range", max - min + 1)
        }
    }

    
    // 手动注册工具的示例
    override suspend fun onInitialize() {
        // 注册一个不使用注解的工具
        registerTool(
            name = "system_info",
            description = "获取系统信息",
            parameterSchema = mapOf()
        ) { _ ->
            ToolResult.success(buildJsonObject {
                put("os", System.getProperty("os.name"))
                put("javaVersion", System.getProperty("java.version"))
                put("userHome", System.getProperty("user.home"))
                put("workingDir", System.getProperty("user.dir"))
                put("availableProcessors", Runtime.getRuntime().availableProcessors())
                put("maxMemory", Runtime.getRuntime().maxMemory())
                put("totalMemory", Runtime.getRuntime().totalMemory())
                put("freeMemory", Runtime.getRuntime().freeMemory())
            })
        }

    }
}

/**
 * 示例5: 最简单的单一功能服务器
 */
class PingServer : McpServerBase() {
    @McpTool("简单的ping测试")
    suspend fun ping(): String = "pong"
}

/**
 * 示例6: 数据处理服务器（展示不同的返回类型）
 */
class DataProcessorServer : McpServerBase() {
    
    @McpTool("对数组进行排序")
    suspend fun sortArray(numbers: List<Double>, ascending: Boolean = true): JsonObject {
        val sorted = if (ascending) numbers.sorted() else numbers.sortedDescending()
        return buildJsonObject {
            putJsonArray("original") {
                numbers.forEach { add(it) }
            }
            putJsonArray("sorted") {
                sorted.forEach { add(it) }
            }
            put("ascending", ascending)
            put("count", numbers.size)
        }
    }

    
    @McpTool("计算数组统计信息")
    suspend fun arrayStats(numbers: List<Double>): JsonObject {
        if (numbers.isEmpty()) {
            return buildJsonObject {
                put("error", "Array cannot be empty")
            }
        }

        val sum = numbers.sum()
        val average = sum / numbers.size
        val min = numbers.minOrNull() ?: 0.0
        val max = numbers.maxOrNull() ?: 0.0
        val sorted = numbers.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }

        return buildJsonObject {
            put("count", numbers.size)
            put("sum", sum)
            put("average", average)
            put("min", min)
            put("max", max)
            put("median", median)
            put("range", max - min)
        }
    }

}

/**
 * 快捷使用方式示例
 */
fun createSimpleCalculator(): McpServer = simpleTool(
    name = "simple_add",
    description = "simple add tool"
) { args ->
    val a = args["a"]?.jsonPrimitive?.doubleOrNull
    val b = args["b"]?.jsonPrimitive?.doubleOrNull
    if (a == null || b == null) {
        return@simpleTool ToolResult.error("Missing or invalid parameters")
    }
    ToolResult.success(JsonPrimitive(a + b))
}

