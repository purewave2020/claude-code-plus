package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getInt
import com.asakii.plugin.mcp.getLong
import com.asakii.plugin.mcp.getString
import com.asakii.settings.AgentSettingsService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalRead 宸ュ叿 - 璇诲彇/鎼滅储缁堢杈撳嚭
 *
 * 璇诲彇缁堢浼氳瘽鐨勮緭鍑哄唴瀹癸紝鏀寔姝ｅ垯琛ㄨ揪寮忔悳绱€?
 * 鏀寔绛夊緟鍛戒护鎵ц瀹屾垚鍚庡啀璇诲彇銆?
 */
class TerminalReadTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 璇诲彇缁堢杈撳嚭
     *
     * @param arguments 鍙傛暟锛?
     *   - session_id: String? - 浼氳瘽 ID锛屼笉浼犲垯浣跨敤榛樿缁堢
     *   - max_lines: Int? - 鏈€澶ц鏁帮紙榛樿 1000锛?
     *   - search: String? - 鎼滅储妯″紡锛堟鍒欒〃杈惧紡锛?
     *   - context_lines: Int? - 鎼滅储缁撴灉涓婁笅鏂囪鏁帮紙榛樿 2锛?
     *   - wait: Boolean? - 鏄惁绛夊緟鍛戒护鎵ц瀹屾垚锛堥粯璁?false锛?
     *   - timeout: Int? - 绛夊緟瓒呮椂鏃堕棿锛堟绉掞紝榛樿 30000锛?
     */
    fun execute(arguments: JsonObject): JsonObject {
        // 濡傛灉鏈寚瀹?session_id锛屼娇鐢ㄩ粯璁ょ粓绔?
        val requestedSessionId = arguments.getString("session_id")
        val sessionId = requestedSessionId
            ?: sessionManager.getDefaultTerminalId()
            ?: return buildJsonObject {
                put("success", false)
                put("error", "No session_id provided and no default terminal exists")
            }

        // 楠岃瘉浼氳瘽鎵€鏈夋潈
        sessionManager.validateSessionOwnership(sessionId)?.let { return it }

        val maxLines = arguments.getInt("max_lines") ?: 1000
        val search = arguments.getString("search")
        val contextLines = arguments.getInt("context_lines") ?: 2
        // 榛樿涓嶇瓑寰咃紝鍙€氳繃 wait=true 绛夊緟鍛戒护瀹屾垚
        val waitForIdle = arguments.getBoolean("wait") ?: false
        // 浣跨敤閰嶇疆鐨勯粯璁よ秴鏃舵椂闂达紙绉掆啋姣锛?
        val settings = AgentSettingsService.getInstance()
        val defaultTimeoutMs = settings.terminalReadTimeoutMs
        val timeout = arguments.getLong("timeout") ?: defaultTimeoutMs

        logger.info { "Reading output from session: $sessionId (maxLines: $maxLines, search: $search, waitForIdle: $waitForIdle)" }

        val result = sessionManager.readOutput(sessionId, maxLines, search, contextLines, waitForIdle, timeout)

        return if (result.success) {
            buildJsonObject {
                put("success", true)
                put("session_id", result.sessionId)
                result.isRunning?.let { put("is_running", it) }
                put("status", when (result.isRunning) {
                    true -> "running"
                    false -> "idle"
                    null -> "unknown"
                })

                // 绛夊緟鐘舵€佷俊鎭?
                if (result.waitTimedOut) {
                    put("wait_timed_out", true)
                }
                result.waitMessage?.let { put("wait_message", it) }

                if (result.searchMatches != null) {
                    put("match_count", result.searchMatches.size)
                    put("matches", buildJsonArray {
                        result.searchMatches.forEach { match ->
                            add(buildJsonObject {
                                put("line_number", match.lineNumber)
                                put("line", match.line)
                                put("context", match.context)
                            })
                        }
                    })
                } else {
                    put("output", result.output ?: "")
                    put("line_count", result.lineCount)
                }
            }
        } else {
            buildJsonObject {
                put("success", false)
                put("session_id", sessionId)
                put("error", result.error ?: "Unknown error")
            }
        }
    }
}

