package com.asakii.server.mcp.schema

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects
import com.networknt.schema.Error
import com.asakii.logging.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val logger = getLogger("SchemaValidator")

/**
 * 参数校验错误
 */
data class ValidationError(
    val parameter: String,
    val message: String,
    val hint: String? = null
)

/**
 * 校验结果
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<ValidationError>) : ValidationResult() {
        /**
         * 格式化错误信息，适合返回给用户
         */
        fun formatMessage(): String {
            val sb = StringBuilder()
            sb.appendLine("Parameter validation failed (${errors.size} error${if (errors.size > 1) "s" else ""}):")
            sb.appendLine()

            errors.forEachIndexed { index, error ->
                sb.appendLine("${index + 1}. [${error.parameter}] ${error.message}")
                error.hint?.let { hint ->
                    hint.lines().forEach { line ->
                        sb.appendLine("   $line")
                    }
                }
                if (index < errors.size - 1) sb.appendLine()
            }

            return sb.toString().trimEnd()
        }
    }
}

/**
 * JSON Schema 参数校验器
 *
 * 使用 networknt/json-schema-validator 库进行校验。
 * 基于 tools.json 中定义的 JSON Schema 校验工具参数。
 *
 * 支持以下校验：
 * - required: 必填参数
 * - type: 参数类型 (string, integer, number, boolean, array, object)
 * - enum: 枚举值
 * - minimum/maximum: 数值范围
 * - minLength/maxLength: 字符串长度
 * - items.enum: 数组元素枚举值
 *
 * 使用示例：
 * ```kotlin
 * val result = SchemaValidator.validate("FindUsages", arguments)
 * if (result is ValidationResult.Invalid) {
 *     return ToolResult.error(result.formatMessage())
 * }
 * ```
 */
object SchemaValidator {

    private val json = Json { ignoreUnknownKeys = true }
    private val schemaRegistry = SchemaRegistry.withDialect(Dialects.getDraft7())

    /**
     * 校验参数
     *
     * @param toolName 工具名称，用于获取对应的 Schema
     * @param arguments 要校验的参数
     * @param customValidators 自定义校验器，用于 Schema 无法表达的复杂逻辑
     * @return 校验结果
     */
    fun validate(
        toolName: String,
        arguments: JsonObject,
        customValidators: List<(JsonObject) -> ValidationError?> = emptyList()
    ): ValidationResult {
        val schema = ToolSchemaLoader.getSchema(toolName)
        if (schema.isEmpty()) {
            logger.warn { "No schema found for tool: $toolName, skipping validation" }
            return ValidationResult.Valid
        }

        return validateWithSchema(schema, arguments, customValidators)
    }

    /**
     * 使用指定的 Schema 校验参数
     *
     * @param schema JSON Schema 定义
     * @param arguments 要校验的参数
     * @param customValidators 自定义校验器
     * @return 校验结果
     */
    fun validateWithSchema(
        schema: JsonObject,
        arguments: JsonObject,
        customValidators: List<(JsonObject) -> ValidationError?> = emptyList()
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // 1. 使用 JSON Schema 库校验
        try {
            val schemaJson = json.encodeToString(JsonElement.serializer(), schema)
            val jsonSchema = schemaRegistry.getSchema(schemaJson, InputFormat.JSON)
            val dataJson = json.encodeToString(JsonElement.serializer(), arguments)

            val validationErrors = jsonSchema.validate(dataJson, InputFormat.JSON)

            validationErrors.forEach { error ->
                errors.add(convertToValidationError(error, schema))
            }
        } catch (e: Exception) {
            logger.error(e) { "JSON Schema validation failed" }
            // 如果库校验失败，回退到基础校验
            errors.addAll(fallbackValidation(schema, arguments))
        }

        // 2. 运行自定义校验器
        for (validator in customValidators) {
            validator(arguments)?.let { errors.add(it) }
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    /**
     * 将 JSON Schema 库的 Error 转换为我们的 ValidationError
     */
    private fun convertToValidationError(
        error: Error,
        schema: JsonObject
    ): ValidationError {
        // 从 instanceLocation 提取参数名
        val path = error.instanceLocation?.toString() ?: ""
        val paramName = when {
            path == "$" || path.isEmpty() -> extractParamFromMessage(error.message ?: "")
            path.startsWith("$.") -> path.removePrefix("$.")
            path.startsWith("/") -> path.removePrefix("/").replace("/", ".")
            else -> path
        }

        // 生成人类可读的错误信息
        val message = formatErrorMessage(error, paramName)

        // 生成提示信息
        val hint = generateHint(paramName, error.keyword ?: "", schema)

        return ValidationError(
            parameter = paramName.ifEmpty { "(unknown)" },
            message = message,
            hint = hint
        )
    }

    /**
     * 从错误消息中提取参数名
     */
    private fun extractParamFromMessage(message: String): String {
        val regex = Regex("""'([^']+)'""")
        return regex.find(message)?.groupValues?.get(1) ?: "(root)"
    }

    /**
     * 格式化错误信息为人类可读的形式
     */
    private fun formatErrorMessage(error: Error, paramName: String): String {
        val originalMessage = error.message ?: "Validation failed"
        val type = error.keyword ?: ""

        return when {
            type.contains("required") -> "Missing required parameter"
            type.contains("type") -> {
                val expectedType = Regex("""expected type: (\w+)""").find(originalMessage)?.groupValues?.get(1)
                    ?: Regex("""type: (\w+)""").find(originalMessage)?.groupValues?.get(1)
                if (expectedType != null) {
                    "Invalid type: expected $expectedType"
                } else {
                    "Invalid type"
                }
            }
            type.contains("enum") -> "Invalid value"
            type.contains("minimum") -> {
                val minimum = Regex("""minimum (-?\d+\.?\d*)""").find(originalMessage)?.groupValues?.get(1)
                "Value is below minimum${minimum?.let { " ($it)" } ?: ""}"
            }
            type.contains("maximum") -> {
                val maximum = Regex("""maximum (-?\d+\.?\d*)""").find(originalMessage)?.groupValues?.get(1)
                "Value exceeds maximum${maximum?.let { " ($it)" } ?: ""}"
            }
            type.contains("minLength") -> "String is too short"
            type.contains("maxLength") -> "String is too long"
            type.contains("pattern") -> "Value does not match the required pattern"
            else -> originalMessage
        }
    }

    /**
     * 生成提示信息
     */
    private fun generateHint(paramName: String, errorType: String?, schema: JsonObject): String? {
        val properties = schema["properties"]?.jsonObject ?: return null
        val paramSchema = properties[paramName]?.jsonObject ?: return null

        return when {
            errorType?.contains("enum") == true -> {
                val enumValues = paramSchema["enum"] as? JsonArray
                enumValues?.let { values ->
                    "Valid values: ${values.joinToString(", ") { it.asReadableString() }}"
                }
            }
            errorType?.contains("required") == true -> {
                val description = paramSchema["description"]?.jsonPrimitive?.contentOrNull
                description?.let { "Description: $it" }
            }
            errorType?.contains("type") == true -> {
                val expectedType = paramSchema["type"]?.jsonPrimitive?.contentOrNull
                getTypeHint(expectedType)
            }
            errorType?.contains("minimum") == true || errorType?.contains("maximum") == true -> {
                val min = paramSchema["minimum"]
                val max = paramSchema["maximum"]
                when {
                    min != null && max != null -> "Valid range: ${min.asReadableString()} to ${max.asReadableString()}"
                    min != null -> "Minimum value: ${min.asReadableString()}"
                    max != null -> "Maximum value: ${max.asReadableString()}"
                    else -> null
                }
            }
            else -> {
                val description = paramSchema["description"]?.jsonPrimitive?.contentOrNull
                description?.let { "Description: $it" }
            }
        }
    }

    private fun getTypeHint(type: String?): String? {
        return when (type) {
            "string" -> "Expected a text string"
            "integer" -> "Expected a whole number (e.g., 1, 42, 100)"
            "number" -> "Expected a number (e.g., 1, 3.14, -5)"
            "boolean" -> "Expected true or false"
            "array" -> "Expected an array/list (e.g., [\"a\", \"b\"])"
            "object" -> "Expected an object (e.g., {\"key\": \"value\"})"
            else -> null
        }
    }

    /**
     * 回退校验：当 JSON Schema 库校验失败时使用
     */
    private fun fallbackValidation(
        schema: JsonObject,
        arguments: JsonObject
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val properties = schema["properties"]?.jsonObject ?: return errors
        val required = schema["required"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        // 校验必填参数
        for (paramName in required) {
            val value = arguments[paramName]
            if (value.isNullOrBlank()) {
                val paramSchema = properties[paramName]?.jsonObject
                val description = paramSchema?.get("description")?.jsonPrimitive?.contentOrNull
                errors.add(ValidationError(
                    parameter = paramName,
                    message = "Missing required parameter",
                    hint = description?.let { "Description: $it" }
                ))
            }
        }

        // 校验枚举值
        arguments.forEach { (paramName, value) ->
            val paramSchema = properties[paramName]?.jsonObject ?: return@forEach
            val enumValues = paramSchema["enum"] as? JsonArray
            if (enumValues != null && enumValues.none { it == value }) {
                errors.add(ValidationError(
                    parameter = paramName,
                    message = "Invalid value: '${value.asReadableString()}'",
                    hint = "Valid values: ${enumValues.joinToString(", ") { it.asReadableString() }}"
                ))
            }
        }

        return errors
    }

    private fun JsonElement?.isNullOrBlank(): Boolean {
        if (this == null || this is JsonNull) return true
        val primitive = this as? JsonPrimitive ?: return false
        return primitive.isString && primitive.content.isBlank()
    }

    private fun JsonElement.asReadableString(): String {
        return when (this) {
            is JsonNull -> "null"
            is JsonPrimitive -> this.content
            else -> this.toString()
        }
    }

    private val JsonPrimitive.contentOrNull: String?
        get() = if (isString) content else null

    /**
     * 便捷方法：创建自定义校验器，用于"至少需要其中一个参数"的场景
     */
    fun requireAtLeastOne(vararg params: String, message: String? = null): (JsonObject) -> ValidationError? {
        return { arguments ->
            val hasAny = params.any { param ->
                val value = arguments[param]
                !value.isNullOrBlank()
            }
            if (!hasAny) {
                ValidationError(
                    parameter = params.joinToString("/"),
                    message = message ?: "At least one of these parameters is required: ${params.joinToString(", ")}",
                    hint = "Provide at least one: ${params.joinToString(" or ")}"
                )
            } else null
        }
    }

    /**
     * 便捷方法：创建自定义校验器，用于"参数 A 依赖参数 B"的场景
     */
    fun requireIfPresent(trigger: String, triggerValues: List<JsonElement>, required: String): (JsonObject) -> ValidationError? {
        return { arguments ->
            val triggerValue = arguments[trigger]
            if (triggerValue != null && triggerValues.any { it == triggerValue }) {
                val requiredValue = arguments[required]
                if (requiredValue.isNullOrBlank()) {
                    ValidationError(
                        parameter = required,
                        message = "Required when $trigger is ${triggerValues.joinToString(" or ") { "'${it.asReadableString()}'" }}",
                        hint = "Please provide '$required' parameter"
                    )
                } else null
            } else null
        }
    }
}
