package com.asakii.plugin.ui.title

import com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner
import com.asakii.claude.agent.sdk.utils.SessionMetadata
import com.asakii.rpc.api.JetBrainsSessionApi
import com.asakii.rpc.api.JetBrainsSessionCommand
import com.asakii.rpc.api.JetBrainsSessionCommandType
import com.asakii.rpc.api.JetBrainsSessionSummary
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.AdjustmentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.*
import com.intellij.openapi.diagnostic.Logger
import javax.swing.*

/**
 * 历史会话列表项类型
 */
sealed class SessionListItem {
    data class GroupHeader(val title: String) : SessionListItem()
    data class SessionItem(
        val session: SessionMetadata,
        val isActive: Boolean,
        val timeStr: String,
        val preview: String
    ) : SessionListItem()

    data object LoadingIndicator : SessionListItem()
}

/**
 * 自定义会话列表项渲染器 - 双行显示
 */
class SessionListCellRenderer : ListCellRenderer<SessionListItem> {

    override fun getListCellRendererComponent(
        list: JList<out SessionListItem>,
        value: SessionListItem,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return when (value) {
            is SessionListItem.GroupHeader -> createGroupHeader(value, isSelected)
            is SessionListItem.SessionItem -> createSessionItem(value, isSelected)
            is SessionListItem.LoadingIndicator -> createLoadingIndicator()
        }
    }

    private fun createSessionItem(item: SessionListItem.SessionItem, isSelected: Boolean): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
            background = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()

            // 左侧图标
            val iconLabel = JLabel(
                if (item.isActive) AllIcons.Actions.Checked else AllIcons.FileTypes.Any_type
            )
            add(iconLabel, BorderLayout.WEST)

            // 右侧文字区域（双行）
            val textPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.emptyLeft(8)

                // 第一行：标题
                add(JLabel(item.preview).apply {
                    font = JBUI.Fonts.label()
                    foreground = if (isSelected) UIUtil.getListSelectionForeground(true)
                    else UIUtil.getLabelForeground()
                })

                // 第二行：时间 + 消息数
                add(JLabel("${item.timeStr} · ${item.session.messageCount} 条消息").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = if (isSelected) UIUtil.getListSelectionForeground(true)
                    else UIUtil.getLabelDisabledForeground()
                })
            }
            add(textPanel, BorderLayout.CENTER)
        }
    }

    private fun createGroupHeader(header: SessionListItem.GroupHeader, isSelected: Boolean): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 4, 8)
            isOpaque = false
            add(JLabel(header.title).apply {
                font = JBUI.Fonts.miniFont()
                foreground = UIUtil.getLabelDisabledForeground()
            }, BorderLayout.WEST)
        }
    }

    private fun createLoadingIndicator(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            isOpaque = false
            add(JLabel("加载中...", AllIcons.Process.Step_1, SwingConstants.CENTER).apply {
                foreground = UIUtil.getLabelDisabledForeground()
            }, BorderLayout.CENTER)
        }
    }
}

/**
 * 历史会话按钮 - 显示在 ToolWindow 标题栏右侧
 *
 * 点击后显示 IDEA 弹出菜单，列出项目的历史会话（从 ~/.claude/projects/ 扫描）
 * 用户选择后，反向调用前端加载该会话
 */
class HistorySessionAction(
    private val sessionApi: JetBrainsSessionApi,
    private val project: Project
) : AnAction("历史会话", "查看历史会话", AllIcons.Actions.Search) {

    companion object {
        private const val POPUP_WIDTH = 350
        private const val POPUP_HEIGHT = 400
    }

    private val logger = Logger.getInstance(HistorySessionAction::class.java.name)
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm")

    // 分页状态
    private var currentOffset = 0
    private var hasMore = true
    private val pageSize = 10  // 总显示数量（激活 + 历史）
    private var cachedSessions: MutableList<SessionMetadata> = mutableListOf()
    private var lastEvent: AnActionEvent? = null
    private var currentPopup: JBPopup? = null
    private var isLoading = false

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("🔍 [HistorySessionAction] 点击历史会话按钮")
        lastEvent = e
        refreshSessionList(e)
    }

    /**
     * 刷新会话列表（可被内部方法调用，避免直接调用 actionPerformed）
     */
    private fun refreshSessionList(e: AnActionEvent) {
        // 重置分页状态
        currentOffset = 0
        hasMore = true
        cachedSessions.clear()
        isLoading = true

        // 先显示弹窗（带加载状态），再异步加载数据
        showLoadingPopup(e)
        loadSessions(e, reset = true)
    }

    /**
     * 显示加载中状态的弹窗（先显示激活会话，然后显示加载中）
     */
    private fun showLoadingPopup(e: AnActionEvent) {
        // 获取当前活动会话（即使在加载中也可以显示）
        val currentState = sessionApi.getState()
        val activeSessions = currentState?.sessions ?: emptyList()

        val items = mutableListOf<SessionListItem>()

        // 先显示激活会话
        if (activeSessions.isNotEmpty()) {
            val now = System.currentTimeMillis()
            items.add(SessionListItem.GroupHeader("激活中"))
            activeSessions.forEach { session ->
                val displayTitle = session.title.take(35).replace("\n", " ").trim().ifEmpty { "新会话" }
                val metadata = SessionMetadata(
                    sessionId = session.sessionId ?: session.id,
                    timestamp = now,
                    messageCount = 0,
                    firstUserMessage = session.title,
                    projectPath = project.basePath ?: "",
                    customTitle = null
                )
                items.add(
                    SessionListItem.SessionItem(
                        session = metadata,
                        isActive = true,
                        timeStr = if (session.isGenerating) "生成中" else if (session.isConnecting) "连接中" else "已连接",
                        preview = displayTitle
                    )
                )
            }
        }

        // 历史会话加载中
        items.add(SessionListItem.GroupHeader("历史加载中..."))

        val sessionCount = items.filterIsInstance<SessionListItem.SessionItem>().size
        showPopupWithItems(e, items, sessionCount)
    }

    /**
     * 加载历史会话
     */
    private fun loadSessions(e: AnActionEvent, reset: Boolean = false) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val projectPath = project.basePath ?: return@executeOnPooledThread

            // 获取当前激活会话（只用真实的 sessionId 去重，忽略 null）
            val currentState = sessionApi.getState()
            val activeSessions = currentState?.sessions ?: emptyList()
            val activeRealSessionIds = activeSessions.mapNotNull { it.sessionId }.toSet()
            val activeCount = activeSessions.size

            // 历史会话需要加载的数量 = pageSize - 激活会话数量
            val historyToLoad = maxOf(pageSize - activeCount, 1)

            logger.info("🔍 [HistorySessionAction] 扫描项目历史会话: $projectPath, offset=$currentOffset, historyToLoad=$historyToLoad (activeCount=$activeCount)")

            val sessions = ClaudeSessionScanner.scanHistorySessions(projectPath, historyToLoad, currentOffset)
            logger.info("🔍 [HistorySessionAction] 找到 ${sessions.size} 个历史会话")

            // 更新分页状态
            hasMore = sessions.size >= historyToLoad
            if (reset) {
                cachedSessions.clear()
            }
            cachedSessions.addAll(sessions)
            currentOffset += sessions.size
            isLoading = false

            // 回到 UI 线程显示弹出菜单
            ApplicationManager.getApplication().invokeLater {
                // 关闭加载中的弹窗
                currentPopup?.cancel()
                showSessionPopup(e, cachedSessions.toList())
            }
        }
    }

    private fun showSessionPopup(e: AnActionEvent, historySessions: List<SessionMetadata>) {
        // 获取当前活动会话
        val currentState = sessionApi.getState()
        val activeSessions = currentState?.sessions ?: emptyList()
        // 只用真实的 sessionId 去重（后端会话 ID）
        val activeRealSessionIds = activeSessions.mapNotNull { it.sessionId }.toSet()

        // 历史会话排除激活的
        val filteredHistory = historySessions.filter { !activeRealSessionIds.contains(it.sessionId) }

        // 如果激活会话和历史会话都为空
        if (activeSessions.isEmpty() && filteredHistory.isEmpty()) {
            logger.info("[HistorySessionAction] 没有历史会话")
            val emptyItems = listOf(SessionListItem.GroupHeader("暂无历史会话"))
            showPopupWithItems(e, emptyItems, 0)
            return
        }

        // 构建列表项
        val items = buildListItems(activeSessions, filteredHistory)
        val sessionCount = items.filterIsInstance<SessionListItem.SessionItem>().size

        showPopupWithItems(e, items, sessionCount)
    }

    /**
     * 使用 PopupChooserBuilder 显示弹窗
     * 固定大小 350x400，滚动到底部自动加载更多
     */
    private fun showPopupWithItems(e: AnActionEvent, items: List<SessionListItem>, sessionCount: Int) {
        // 创建自定义列表模型
        val listModel = DefaultListModel<SessionListItem>()
        items.forEach { listModel.addElement(it) }

        val list = JList(listModel).apply {
            cellRenderer = SessionListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION

            // 左键单击选择会话
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(evt: MouseEvent) {
                    val index = locationToIndex(evt.point)
                    if (index < 0) return

                    val selected = model.getElementAt(index)

                    if (SwingUtilities.isRightMouseButton(evt)) {
                        // 右键点击显示上下文菜单
                        if (selected is SessionListItem.SessionItem && !selected.isActive) {
                            showSessionContextMenu(evt, selected)
                        }
                    } else if (SwingUtilities.isLeftMouseButton(evt) && evt.clickCount == 1) {
                        // 左键单击
                        when (selected) {
                            is SessionListItem.SessionItem -> {
                                logger.info("🔍 [HistorySessionAction] 选择会话: ${selected.session.sessionId}")
                                currentPopup?.cancel()
                                sessionApi.sendCommand(
                                    JetBrainsSessionCommand(
                                        type = JetBrainsSessionCommandType.SWITCH,
                                        sessionId = selected.session.sessionId
                                    )
                                )
                            }
                            else -> {}
                        }
                    }
                }
            })
        }

        // 创建滚动面板并添加滚动监听
        val scrollPane = JBScrollPane(list).apply {
            preferredSize = Dimension(POPUP_WIDTH, POPUP_HEIGHT)
            minimumSize = Dimension(POPUP_WIDTH, POPUP_HEIGHT)
            border = null

            // 滚动到底部自动加载更多
            verticalScrollBar.addAdjustmentListener { evt: AdjustmentEvent ->
                if (!evt.valueIsAdjusting && hasMore && !isLoading) {
                    val scrollBar = evt.adjustable
                    val extent = scrollBar.visibleAmount
                    val maximum = scrollBar.maximum
                    val value = scrollBar.value

                    // 当滚动到距离底部 50px 以内时触发加载
                    if (value + extent >= maximum - 50) {
                        logger.info("🔍 [HistorySessionAction] 滚动触发加载更多")
                        loadMoreSessionsInPlace(listModel, list)
                    }
                }
            }
        }

        // 创建容器面板确保固定大小
        val containerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(POPUP_WIDTH, POPUP_HEIGHT)
            add(scrollPane, BorderLayout.CENTER)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(containerPanel, list)
            .setTitle("历史会话 ($sessionCount)")
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup()

        currentPopup = popup

        // 显示弹窗 - 固定在按钮下方
        val component = e.inputEvent?.component
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInFocusCenter()
        }
    }

    /**
     * 在当前弹窗内加载更多会话（不关闭弹窗）
     */
    private fun loadMoreSessionsInPlace(listModel: DefaultListModel<SessionListItem>, list: JList<SessionListItem>) {
        if (isLoading || !hasMore) return
        isLoading = true

        // 添加加载指示器
        ApplicationManager.getApplication().invokeLater {
            listModel.addElement(SessionListItem.LoadingIndicator)
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val projectPath = project.basePath ?: return@executeOnPooledThread

            // 获取当前激活会话
            val currentState = sessionApi.getState()
            val activeSessions = currentState?.sessions ?: emptyList()
            val activeRealSessionIds = activeSessions.mapNotNull { it.sessionId }.toSet()
            val activeCount = activeSessions.size

            val historyToLoad = maxOf(pageSize - activeCount, 1)

            logger.info("🔍 [HistorySessionAction] 滚动加载更多: offset=$currentOffset, historyToLoad=$historyToLoad")

            val sessions = ClaudeSessionScanner.scanHistorySessions(projectPath, historyToLoad, currentOffset)
            logger.info("🔍 [HistorySessionAction] 加载到 ${sessions.size} 个历史会话")

            // 更新分页状态
            hasMore = sessions.size >= historyToLoad
            cachedSessions.addAll(sessions)
            currentOffset += sessions.size
            isLoading = false

            // 回到 UI 线程更新列表
            ApplicationManager.getApplication().invokeLater {
                // 移除加载指示器
                for (i in listModel.size() - 1 downTo 0) {
                    if (listModel.getElementAt(i) is SessionListItem.LoadingIndicator) {
                        listModel.removeElementAt(i)
                    }
                }

                // 添加新加载的会话
                val now = System.currentTimeMillis()
                val filteredSessions = sessions.filter { !activeRealSessionIds.contains(it.sessionId) }
                filteredSessions.forEach { session ->
                    val displayTitle = (session.customTitle ?: session.firstUserMessage)
                        .take(35).replace("\n", " ").trim()
                        .ifEmpty { "新会话" }
                    listModel.addElement(
                        SessionListItem.SessionItem(
                            session = session,
                            isActive = false,
                            timeStr = formatRelativeTime(session.timestamp, now),
                            preview = displayTitle
                        )
                    )
                }

                // 刷新列表
                list.revalidate()
                list.repaint()
            }
        }
    }

    /**
     * 显示会话上下文菜单（右键菜单）
     */
    private fun showSessionContextMenu(evt: MouseEvent, item: SessionListItem.SessionItem) {
        val menuItems = listOf(
            "删除会话" to { deleteHistorySession(item.session) }
        )

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<Pair<String, () -> Unit>>("", menuItems) {
                override fun getTextFor(value: Pair<String, () -> Unit>): String = value.first

                override fun onChosen(selectedValue: Pair<String, () -> Unit>, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        selectedValue.second()
                    }
                    return FINAL_CHOICE
                }
            }
        )
        popup.show(com.intellij.ui.awt.RelativePoint(evt))
    }

    /**
     * 删除历史会话
     * 1. 直接删除历史文件
     * 2. 通知前端删除会话（如果已加载）
     */
    private fun deleteHistorySession(session: SessionMetadata) {
        logger.info("🗑️ [HistorySessionAction] 删除历史会话: ${session.sessionId}")

        // 关闭当前弹窗
        currentPopup?.cancel()

        ApplicationManager.getApplication().executeOnPooledThread {
            // 1. 直接删除历史文件
            val deleted = ClaudeSessionScanner.deleteSession(session.projectPath, session.sessionId)

            if (deleted) {
                logger.info("✅ [HistorySessionAction] 历史文件删除成功: ${session.sessionId}")

                // 2. 从缓存中移除
                cachedSessions.removeIf { it.sessionId == session.sessionId }

                // 3. 通知前端删除会话（如果已加载为 Tab）
                sessionApi.sendCommand(
                    JetBrainsSessionCommand(
                        type = JetBrainsSessionCommandType.DELETE,
                        sessionId = session.sessionId
                    )
                )
            } else {
                logger.warn("❌ [HistorySessionAction] 历史文件删除失败: ${session.sessionId}")
            }

            // 4. 刷新弹窗（重新加载历史会话列表）
            ApplicationManager.getApplication().invokeLater {
                lastEvent?.let { refreshSessionList(it) }
            }
        }
    }

    /**
     * 构建列表项（带分组）
     * @param activeSessions 激活中的会话（从 sessionApi 获取）
     * @param historySessions 历史会话（从文件扫描获取，已排除激活会话）
     */
    private fun buildListItems(
        activeSessions: List<JetBrainsSessionSummary>,
        historySessions: List<SessionMetadata>
    ): List<SessionListItem> {
        val items = mutableListOf<SessionListItem>()
        val now = System.currentTimeMillis()

        // 激活中分组
        if (activeSessions.isNotEmpty()) {
            items.add(SessionListItem.GroupHeader("激活中"))
            activeSessions.forEach { session ->
                val displayTitle = session.title.take(35).replace("\n", " ").trim().ifEmpty { "新会话" }
                // 创建一个虚拟的 SessionMetadata 用于兼容现有的 SessionListItem
                val metadata = SessionMetadata(
                    sessionId = session.sessionId ?: session.id,
                    timestamp = now,
                    messageCount = 0,
                    firstUserMessage = session.title,
                    projectPath = project.basePath ?: "",
                    customTitle = null
                )
                items.add(
                    SessionListItem.SessionItem(
                        session = metadata,
                        isActive = true,
                        timeStr = if (session.isGenerating) "生成中" else if (session.isConnecting) "连接中" else "已连接",
                        preview = displayTitle
                    )
                )
            }
        }

        // 历史分组
        if (historySessions.isNotEmpty()) {
            items.add(SessionListItem.GroupHeader("历史"))
            historySessions.forEach { session ->
                // 优先使用 customTitle，否则使用 firstUserMessage
                val displayTitle = (session.customTitle ?: session.firstUserMessage)
                    .take(35).replace("\n", " ").trim()
                    .ifEmpty { "新会话" }
                items.add(
                    SessionListItem.SessionItem(
                        session = session,
                        isActive = false,
                        timeStr = formatRelativeTime(session.timestamp, now),
                        preview = displayTitle
                    )
                )
            }
        }

        return items
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }

    /**
     * 格式化相对时间（参考 Web 端）
     */
    private fun formatRelativeTime(timestamp: Long, now: Long): String {
        val diff = now - timestamp
        val minutes = diff / 60000
        val hours = diff / 3600000
        val days = diff / 86400000

        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 7 -> "${days}天前"
            else -> dateTimeFormat.format(Date(timestamp))
        }
    }
}
