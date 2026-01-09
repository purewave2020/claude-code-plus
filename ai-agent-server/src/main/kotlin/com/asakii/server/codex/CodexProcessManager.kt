package com.asakii.server.codex

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.asakii.logging.*
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists

/**
 * Codex 进程管理器
 *
 * 负责管理 codex-app-server 进程的生命周期，包括：
 * - 启动/停止进程
 * - 进程健康监控
 * - 崩溃恢复
 * - stdin/stdout 流管理
 *
 * @param binaryPath codex-app-server 可执行文件路径
 * @param workingDirectory 工作目录路径
 * @param scope 协程作用域（用于异步任务）
 * @param autoRestart 进程崩溃时是否自动重启（默认 false）
 * @param maxRestartAttempts 最大自动重启次数（默认 3 次）
 * @param healthCheckInterval 健康检查间隔（毫秒，默认 5000ms）
 */
class CodexProcessManager(
    private val binaryPath: String,
    private val workingDirectory: String,
    private val scope: CoroutineScope,
    private val autoRestart: Boolean = false,
    private val maxRestartAttempts: Int = 3,
    private val healthCheckInterval: Long = 5000L
) {
    private val logger = getLogger("CodexProcessManager")

    // 进程状态
    private var process: Process? = null
    private val isRunningFlag = AtomicBoolean(false)
    private var restartAttempts = 0

    // 状态流（用于外部监听进程状态变化）
    private val _status = MutableStateFlow<ProcessStatus>(ProcessStatus.Stopped)
    val status: StateFlow<ProcessStatus> = _status.asStateFlow()

    // 输出流管道
    private val _stdout = Channel<String>(Channel.UNLIMITED)
    private val _stderr = Channel<String>(Channel.UNLIMITED)
    val stdout: Channel<String> = _stdout
    val stderr: Channel<String> = _stderr

    // 流读取任务
    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null
    private var healthCheckJob: Job? = null

    /**
     * 启动 codex-app-server 进程
     *
     * @param args 额外的命令行参数
     * @param env 环境变量
     * @throws IllegalStateException 如果进程已在运行
     * @throws IOException 如果可执行文件不存在或启动失败
     */
    @Throws(IllegalStateException::class, IOException::class)
    suspend fun start(
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ) {
        if (isRunningFlag.get()) {
            throw IllegalStateException("Codex process is already running")
        }

        // 验证可执行文件
        val binaryFile = Paths.get(binaryPath)
        if (!binaryFile.exists()) {
            throw IOException("Codex binary not found at: $binaryPath")
        }

        // 验证工作目录
        val workDir = File(workingDirectory)
        if (!workDir.exists()) {
            workDir.mkdirs()
        }

        logger.info { "🚀 Starting Codex process: $binaryPath" }
        logger.debug { "   Working directory: $workingDirectory" }
        logger.debug { "   Arguments: $args" }

        try {
            // 构建进程
            val processBuilder = ProcessBuilder(listOf(binaryPath) + args)
                .directory(workDir)
                .redirectErrorStream(false) // 分离 stdout 和 stderr

            // 设置环境变量
            if (env.isNotEmpty()) {
                processBuilder.environment().putAll(env)
            }

            // 启动进程
            process = processBuilder.start()
            isRunningFlag.set(true)
            _status.value = ProcessStatus.Running
            restartAttempts = 0

            logger.info { "✅ Codex process started (PID: ${getProcessId()})" }

            // 启动流读取任务
            startStreamReaders()

            // 启动健康检查
            startHealthCheck()

        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to start Codex process" }
            _status.value = ProcessStatus.Failed(e.message ?: "Unknown error")
            throw e
        }
    }

    /**
     * 停止 codex-app-server 进程
     *
     * @param timeout 等待进程退出的超时时间（毫秒），默认 5000ms
     * @param force 如果超时后是否强制终止进程，默认 true
     */
    suspend fun stop(timeout: Long = 5000L, force: Boolean = true) {
        val currentProcess = process
        if (currentProcess == null || !isRunningFlag.get()) {
            logger.warn { "⚠️ Codex process is not running" }
            return
        }

        logger.info { "🛑 Stopping Codex process (PID: ${getProcessId()})" }

        // 取消健康检查
        healthCheckJob?.cancel()

        // 优雅关闭：先发送退出信号
        currentProcess.destroy()

        // 等待进程退出
        val exited = withContext(Dispatchers.IO) {
            currentProcess.waitFor(timeout, TimeUnit.MILLISECONDS)
        }

        if (!exited && force) {
            logger.warn { "⚠️ Process did not exit gracefully, forcing termination" }
            currentProcess.destroyForcibly()
            withContext(Dispatchers.IO) {
                currentProcess.waitFor(1000L, TimeUnit.MILLISECONDS)
            }
        }

        // 清理资源
        cleanup()

        logger.info { "✅ Codex process stopped" }
    }

    /**
     * 检查进程是否正在运行
     */
    fun isRunning(): Boolean {
        return isRunningFlag.get() && process?.isAlive == true
    }

    /**
     * 获取进程对象（可能为 null）
     */
    fun getProcess(): Process? {
        return if (isRunning()) process else null
    }

    /**
     * 获取进程 ID（如果可用）
     */
    fun getProcessId(): Long? {
        return try {
            process?.pid()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 向进程的 stdin 写入数据
     *
     * @param data 要写入的数据
     * @throws IOException 如果写入失败或进程未运行
     */
    @Throws(IOException::class)
    suspend fun writeToStdin(data: String) {
        val currentProcess = process
        if (currentProcess == null || !isRunning()) {
            throw IOException("Codex process is not running")
        }

        try {
            withContext(Dispatchers.IO) {
                currentProcess.outputStream.write(data.toByteArray())
                currentProcess.outputStream.flush()
            }
        } catch (e: IOException) {
            logger.error(e) { "❌ Failed to write to stdin" }
            throw e
        }
    }

    /**
     * 启动流读取任务
     */
    private fun startStreamReaders() {
        val currentProcess = process ?: return

        // 读取 stdout
        stdoutJob = scope.launch(Dispatchers.IO) {
            try {
                currentProcess.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (isActive) {
                            _stdout.trySend(line)
                            logger.debug { "[STDOUT] $line" }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logger.error(e) { "❌ Error reading stdout" }
                }
            }
        }

        // 读取 stderr
        stderrJob = scope.launch(Dispatchers.IO) {
            try {
                currentProcess.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (isActive) {
                            _stderr.trySend(line)
                            logger.debug { "[STDERR] $line" }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logger.error(e) { "❌ Error reading stderr" }
                }
            }
        }
    }

    /**
     * 启动健康检查任务
     */
    private fun startHealthCheck() {
        healthCheckJob = scope.launch {
            while (isActive && isRunningFlag.get()) {
                delay(healthCheckInterval)

                val currentProcess = process
                if (currentProcess != null && !currentProcess.isAlive) {
                    val exitCode = currentProcess.exitValue()
                    logger.error { "❌ Codex process crashed (exit code: $exitCode)" }

                    // 清理资源
                    cleanup(notifyCrash = false)

                    // 尝试自动重启
                    if (autoRestart && restartAttempts < maxRestartAttempts) {
                        restartAttempts++
                        logger.warn { "⚠️ Attempting auto-restart ($restartAttempts/$maxRestartAttempts)" }
                        _status.value = ProcessStatus.Restarting(restartAttempts)

                        delay(1000L) // 等待 1 秒后重启

                        try {
                            start() // 使用默认参数重启
                        } catch (e: Exception) {
                            logger.error(e) { "❌ Auto-restart failed" }
                            _status.value = ProcessStatus.Failed("Auto-restart failed: ${e.message}")
                        }
                    } else {
                        _status.value = ProcessStatus.Crashed(exitCode)
                        if (autoRestart) {
                            logger.error { "❌ Max restart attempts ($maxRestartAttempts) reached, giving up" }
                        }
                    }

                    break
                }
            }
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup(notifyCrash: Boolean = false) {
        isRunningFlag.set(false)

        // 取消流读取任务
        stdoutJob?.cancel()
        stderrJob?.cancel()

        // 关闭流
        try {
            process?.inputStream?.close()
            process?.errorStream?.close()
            process?.outputStream?.close()
        } catch (e: Exception) {
            logger.debug { "Error closing process streams: ${e.message}" }
        }

        process = null

        if (!notifyCrash) {
            _status.value = ProcessStatus.Stopped
        }
    }

    /**
     * 进程状态枚举
     */
    sealed class ProcessStatus {
        /** 已停止 */
        object Stopped : ProcessStatus()

        /** 正在运行 */
        object Running : ProcessStatus()

        /** 正在重启 */
        data class Restarting(val attempt: Int) : ProcessStatus()

        /** 崩溃 */
        data class Crashed(val exitCode: Int) : ProcessStatus()

        /** 启动失败 */
        data class Failed(val reason: String) : ProcessStatus()
    }
}
