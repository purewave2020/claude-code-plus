package com.asakii.plugin.listeners

import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*
import com.intellij.openapi.wm.ToolWindow
import com.asakii.plugin.services.ClaudeCodePlusBackgroundService
import com.asakii.plugin.types.SessionState

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

/**
 * Claude 工具窗口监听器（动态插件兼容版本）
 *
 * 监听工具窗口的显示/隐藏事件，维护会话状态的连续性。
 * 当工具窗口隐藏时，后台服务继续执行；
 * 当工具窗口重新显示时，自动恢复最新状态。
 *
 * 实现 Disposable 接口并注册到项目，支持动态加载/卸载。
 */
class ClaudeToolWindowListener(private val project: Project) : ToolWindowManagerListener, Disposable {
    
    companion object {
        private val logger = Logger.getInstance(ClaudeToolWindowListener::class.java)
        const val TOOL_WINDOW_ID = "Claude Code Plus"
        
        // 新会话消息总线主题
        val NEW_SESSION_TOPIC = com.intellij.util.messages.Topic.create(
            "Claude.NewSession",
            Runnable::class.java
        )
    }
    
    // 后台服务引用
    private val backgroundService = ClaudeCodePlusBackgroundService
    
    // 协程作用域
    private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 工具窗口状态
    private var isToolWindowVisible = false
    private var lastVisibleTime = System.currentTimeMillis()
    
    // 当前活跃的会话ID（可能有多个标签页）
    private val activeSessionIds = mutableSetOf<String>()
    
    init {
        // 注册到项目的 Disposable 层级，确保插件卸载时正确清理
        Disposer.register(project, this)
        logger.info { "🎯 ClaudeToolWindowListener 已初始化，项目: ${project.basePath}" }
    }
    
    /**
     * 工具窗口状态变化时触发
     */
    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
        toolWindow?.let { handleToolWindowStateChange(it) }
    }
    
    /**
     * 工具窗口显示事件
     */
    override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == TOOL_WINDOW_ID) {
            handleToolWindowShown(toolWindow)
        }
    }
    
    /**
     * 处理工具窗口显示
     */
    private fun handleToolWindowShown(toolWindow: ToolWindow) {
        logger.info { "👁️ 工具窗口已显示: ${toolWindow.id}" }
        isToolWindowVisible = true
        
        // 计算隐藏时长
        val hiddenDuration = System.currentTimeMillis() - lastVisibleTime
        logger.info { "⏱️ 工具窗口隐藏时长: ${hiddenDuration / 1000}秒" }
        
        // 恢复会话状态
        listenerScope.launch {
            restoreSessionStates()
        }
        
        // 发送状态恢复事件
        notifyUIToRefresh()
    }
    
    /**
     * 处理工具窗口隐藏
     */
    private fun handleToolWindowHidden(toolWindow: ToolWindow) {
        logger.info { "🙈 工具窗口已隐藏: ${toolWindow.id}" }
        isToolWindowVisible = false
        lastVisibleTime = System.currentTimeMillis()
        
        // 保存当前状态快照
        listenerScope.launch {
            saveCurrentSessionStates()
        }
        
        // 记录当前活跃的会话
        recordActiveSessionIds()
        
        logger.info { "💾 会话状态已保存，后台服务继续运行" }
    }
    
    /**
     * 处理工具窗口状态变化
     */
    private fun handleToolWindowStateChange(toolWindow: ToolWindow) {
        val wasVisible = isToolWindowVisible
        val nowVisible = toolWindow.isVisible
        
        if (wasVisible != nowVisible) {
            if (nowVisible) {
                handleToolWindowShown(toolWindow)
            } else {
                handleToolWindowHidden(toolWindow)
            }
        }
    }
    
    /**
     * 保存当前会话状态
     */
    private suspend fun saveCurrentSessionStates() {
        withContext(Dispatchers.IO) {
            try {
                val projectPath = project.basePath ?: return@withContext

                // 获取项目所有会话状态（添加超时保护）
                val projectStates: Map<String, SessionState> = withTimeoutOrNull(3000) {
                    backgroundService.observeProjectUpdates(projectPath).firstOrNull()
                } ?: emptyMap()

                logger.info { "💾 保存 ${projectStates.size} 个会话状态" }

                // 记录活跃会话ID
                activeSessionIds.clear()
                activeSessionIds.addAll(projectStates.keys)

                // 后台服务已经在内存中维护状态，这里只需记录会话ID

            } catch (e: Exception) {
                logger.error("保存会话状态失败", e)
            }
        }
    }
    
    /**
     * 恢复会话状态
     */
    private suspend fun restoreSessionStates() {
        withContext(Dispatchers.IO) {
            try {
                val projectPath = project.basePath ?: return@withContext
                
                logger.info { "🔄 开始恢复会话状态，活跃会话: ${activeSessionIds.size}" }
                
                // 从后台服务恢复每个会话的状态
                activeSessionIds.forEach { sessionId ->
                    val state = backgroundService.getSessionState(sessionId)
                    if (state != null) {
                        logger.info { "✅ 会话 $sessionId 在内存中: ${state.messages.size} 条消息, 生成中=${state.isGenerating}" }
                        // 内存中有状态，UI 会自动通过 Flow 同步，无需额外操作
                    } else {
                        // 会话不在内存中，跳过磁盘恢复（根据需求，只在启动时从文件加载）
                        logger.info { "⚠️ 会话 $sessionId 不在内存中" }
                    }
                }
                
                // 获取最新的服务统计
                val stats = backgroundService.getServiceStats()
                logger.info { "📊 后台服务统计: $stats" }
                
            } catch (e: Exception) {
                logger.error("恢复会话状态失败", e)
            }
        }
    }
    
    /**
     * 记录活跃的会话ID
     */
    private fun recordActiveSessionIds() {
        // 这里应该从UI组件获取当前所有标签页的会话ID
        // 暂时从后台服务获取
        val projectPath = project.basePath ?: return

        listenerScope.launch {
            try {
                // 添加超时保护
                val projectStates: Map<String, SessionState> = withTimeoutOrNull(3000) {
                    backgroundService.observeProjectUpdates(projectPath).firstOrNull()
                } ?: emptyMap()

                activeSessionIds.clear()
                activeSessionIds.addAll(projectStates.keys)

                logger.info { "📝 记录活跃会话ID: $activeSessionIds" }
            } catch (e: Exception) {
                logger.error("记录会话ID失败", e)
            }
        }
    }
    
    /**
     * 通知UI刷新
     */
    private fun notifyUIToRefresh() {
        // 发送自定义事件通知UI组件刷新
        project.messageBus.syncPublisher(ToolWindowStateChangedTopic.TOPIC)
            .onToolWindowStateChanged(isVisible = true)
    }
    
    /**
     * 注册会话到监听器
     */
    fun registerSession(sessionId: String) {
        activeSessionIds.add(sessionId)
        logger.debug { "➕ 注册会话: $sessionId, 当前活跃数: ${activeSessionIds.size}" }
    }
    
    /**
     * 从监听器注销会话
     */
    fun unregisterSession(sessionId: String) {
        activeSessionIds.remove(sessionId)
        logger.debug { "➖ 注销会话: $sessionId, 剩余活跃数: ${activeSessionIds.size}" }
    }
    
    /**
     * 获取工具窗口是否可见
     */
    fun isToolWindowCurrentlyVisible(): Boolean = isToolWindowVisible
    
    /**
     * 清理资源（Disposable 接口实现）
     */
    override fun dispose() {
        logger.info { "🧹 清理 ClaudeToolWindowListener" }
        listenerScope.cancel("Listener disposed")
        activeSessionIds.clear()
    }
}