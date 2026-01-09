package com.asakii.plugin.utils

import com.asakii.claude.agent.sdk.types.AgentDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.asakii.logging.*
import java.util.concurrent.atomic.AtomicReference

private val logger = getLogger("ResourceLoader")

/**
 * 子代理配置文件的 JSON 结构
 */
@Serializable
data class AgentsConfig(
    val agents: Map<String, AgentJsonDefinition> = emptyMap()
)

/**
 * JSON 格式的代理定义
 */
@Serializable
data class AgentJsonDefinition(
    val description: String,
    val prompt: String,
    val tools: List<String>? = null,
    val model: String? = null
)

/**
 * 资源文件加载工具
 *
 * 用于从 resources 目录加载各种配置文件，包括：
 * - 子代理定义 (agents/agents.json)
 * - MCP prompts (prompts/[name].md)
 *
 * 特性：
 * - 支持缓存，多个会话共享加载的数据
 * - 支持手动刷新缓存
 */
object ResourceLoader {

    private const val AGENTS_JSON_PATH = "agents/agents.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 缓存的代理定义
     */
    private val cachedAgents = AtomicReference<Map<String, AgentDefinition>?>(null)

    /**
     * 从资源文件加载文本内容
     *
     * @param resourcePath 资源路径，如 "prompts/jetbrains-mcp-instructions.md"
     * @return 文件内容，如果文件不存在则返回 null
     */
    fun loadText(resourcePath: String): String? {
        return try {
            // 尝试多种类加载器
            val classLoaders = listOf(
                ResourceLoader::class.java.classLoader,
                Thread.currentThread().contextClassLoader,
                ClassLoader.getSystemClassLoader()
            )

            for (classLoader in classLoaders) {
                val stream = classLoader?.getResourceAsStream(resourcePath)
                if (stream != null) {
                    val content = stream.bufferedReader().use { it.readText() }
                    logger.info { "✅ Loaded resource '$resourcePath' via ${classLoader.javaClass.simpleName}" }
                    return content
                }
            }

            logger.warn { "❌ Resource not found: $resourcePath (tried ${classLoaders.size} classloaders)" }
            null
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load resource: $resourcePath" }
            null
        }
    }

    /**
     * 从资源文件加载文本内容，如果文件不存在则返回默认值
     *
     * @param resourcePath 资源路径
     * @param default 默认值
     * @return 文件内容或默认值
     */
    fun loadTextOrDefault(resourcePath: String, default: String): String {
        return loadText(resourcePath) ?: default
    }

    /**
     * 加载所有子代理定义（带缓存）
     *
     * @param forceReload 是否强制重新加载（忽略缓存）
     * @return 代理名称到定义的映射
     */
    fun loadAllAgentDefinitions(forceReload: Boolean = false): Map<String, AgentDefinition> {
        // 如果不强制重新加载且缓存存在，直接返回缓存
        if (!forceReload) {
            cachedAgents.get()?.let { cached ->
                logger.debug { "Using cached agent definitions (${cached.size} agents)" }
                return cached
            }
        }

        // 加载并解析 JSON
        val agents = loadAgentsFromJson()

        // 更新缓存
        cachedAgents.set(agents)

        if (agents.isNotEmpty()) {
            logger.info { "Loaded ${agents.size} agent definitions from $AGENTS_JSON_PATH" }
        }

        return agents
    }

    /**
     * 刷新缓存（下次调用 loadAllAgentDefinitions 时重新加载）
     */
    fun invalidateCache() {
        cachedAgents.set(null)
        logger.info { "Agent definitions cache invalidated" }
    }

    /**
     * 强制重新加载代理定义
     *
     * @return 代理名称到定义的映射
     */
    fun reloadAgentDefinitions(): Map<String, AgentDefinition> {
        return loadAllAgentDefinitions(forceReload = true)
    }

    /**
     * 从 JSON 文件加载代理定义
     */
    private fun loadAgentsFromJson(): Map<String, AgentDefinition> {
        logger.info { "🔍 Loading agent definitions from: $AGENTS_JSON_PATH" }
        return try {
            val content = loadText(AGENTS_JSON_PATH)
            if (content == null) {
                logger.warn { "❌ Agent definitions file not found: $AGENTS_JSON_PATH" }
                return emptyMap()
            }

            logger.info { "📄 Agent JSON content length: ${content.length} chars" }
            val config = json.decodeFromString<AgentsConfig>(content)
            logger.info { "📦 Parsed ${config.agents.size} agents from JSON" }

            // 转换为 AgentDefinition
            config.agents.mapValues { (name, jsonDef) ->
                AgentDefinition(
                    description = jsonDef.description,
                    prompt = jsonDef.prompt,
                    tools = jsonDef.tools,
                    model = jsonDef.model
                ).also {
                    logger.info { "✅ Loaded agent: $name (tools: ${jsonDef.tools?.size ?: 0})" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to load agent definitions from JSON" }
            emptyMap()
        }
    }
}
