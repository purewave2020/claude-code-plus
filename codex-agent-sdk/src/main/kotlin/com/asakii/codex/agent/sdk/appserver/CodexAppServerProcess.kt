package com.asakii.codex.agent.sdk.appserver

import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Codex App-Server 进程管理器
 *
 * 负责启动和管理 `codex app-server` 进程的生命周期
 */
class CodexAppServerProcess private constructor(
    private val process: Process,
    private val rpcClient: CodexJsonRpcClient
) : Closeable {

    val client: CodexJsonRpcClient get() = rpcClient

    val isAlive: Boolean get() = process.isAlive

    /**
     * 等待进程退出
     */
    fun waitFor(timeout: Long = 30, unit: TimeUnit = TimeUnit.SECONDS): Int {
        return if (process.waitFor(timeout, unit)) {
            process.exitValue()
        } else {
            -1
        }
    }

    /**
     * 强制终止进程
     */
    fun destroy() {
        process.destroy()
    }

    /**
     * 强制终止进程 (forcibly)
     */
    fun destroyForcibly(): Process {
        return process.destroyForcibly()
    }

    override fun close() {
        rpcClient.close()
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        private val logger = Logger.getLogger(CodexAppServerProcess::class.java.name)

        /**
         * 启动 Codex App-Server 进程
         *
         * @param codexPath Codex 可执行文件路径，null 则自动查找
         * @param workingDirectory 工作目录
         * @param env 环境变量
         * @param configOverrides CLI 配置覆盖（--config key=value）
         * @param scope 协程作用域
         */
        fun spawn(
            codexPath: Path? = null,
            workingDirectory: Path? = null,
            env: Map<String, String> = emptyMap(),
            configOverrides: Map<String, String> = emptyMap(),
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): CodexAppServerProcess {
            val executablePath = codexPath?.toString() ?: findCodexExecutable()
            logger.info(
                "Starting codex app-server: path=$executablePath cwd=${workingDirectory?.toAbsolutePath() ?: "default"}"
            )

            val command = mutableListOf(executablePath)
            if (configOverrides.isNotEmpty()) {
                logger.info("codex app-server configOverrides (${configOverrides.size} entries):")
                configOverrides.entries.sortedBy { it.key }.forEach { (key, value) ->
                    logger.info("  --config $key=$value")
                }
            }
            configOverrides.forEach { (key, value) ->
                command.add("--config")
                command.add("$key=$value")
            }
            command.add("app-server")

            val finalCommand = if (isWindowsCmd(executablePath)) {
                listOf("cmd", "/c") + command
            } else {
                command
            }

            val processBuilder = ProcessBuilder(finalCommand)
                .redirectErrorStream(false)

            workingDirectory?.let {
                processBuilder.directory(it.toFile())
            }

            // 设置环境变量
            val processEnv = processBuilder.environment()
            env.forEach { (key, value) ->
                processEnv[key] = value
            }

            val process = try {
                processBuilder.start()
            } catch (e: IOException) {
                throw CodexAppServerException("Failed to start codex app-server: ${e.message}", e)
            }

            startStderrLogger(process, scope)
            process.onExit().thenAccept { exited ->
                logger.warning("codex app-server exited: code=${exited.exitValue()}")
            }

            val stdin = process.outputStream
                ?: throw CodexAppServerException("Failed to get stdin of codex app-server")
            val stdout = process.inputStream
                ?: throw CodexAppServerException("Failed to get stdout of codex app-server")

            val rpcClient = CodexJsonRpcClient(stdin, stdout, scope)
            rpcClient.start()

            return CodexAppServerProcess(process, rpcClient)
        }

        /**
         * 查找 Codex 可执行文件
         */
        private fun findCodexExecutable(): String {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val binaryCandidates = if (isWindows) {
                listOf("codex.exe", "codex.cmd", "codex.bat", "codex")
            } else {
                listOf("codex")
            }
            fun isRunnable(file: File): Boolean {
                return if (isWindows) file.exists() else file.exists() && file.canExecute()
            }

            // 1. 检查环境变量 CODEX_BIN
            System.getenv("CODEX_BIN")?.let { path ->
                val direct = File(path)
                if (isRunnable(direct)) return direct.absolutePath
                if (isWindows && !direct.name.contains('.')) {
                    val parent = direct.parentFile
                    listOf("exe", "cmd", "bat").forEach { ext ->
                        val expanded = if (parent != null) {
                            File(parent, "${direct.name}.$ext")
                        } else {
                            File("${direct.path}.$ext")
                        }
                        if (isRunnable(expanded)) return expanded.absolutePath
                    }
                }
            }

            // 2. 检查 PATH 中的 codex
            val pathEnv = System.getenv("PATH") ?: ""
            val pathSeparator = if (isWindows) ";" else ":"
            for (dir in pathEnv.split(pathSeparator)) {
                binaryCandidates.forEach { candidate ->
                    val file = File(dir, candidate)
                    if (isRunnable(file)) return file.absolutePath
                }
            }

            // 3. 检查常见位置
            val commonPaths = if (isWindows) {
                listOf(
                    System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\codex\\codex.exe" },
                    System.getenv("APPDATA")?.let { "$it\\codex\\codex.exe" },
                    System.getenv("APPDATA")?.let { "$it\\npm\\codex.cmd" },
                    System.getenv("APPDATA")?.let { "$it\\npm\\codex.exe" },
                    "C:\\Program Files\\codex\\codex.exe"
                )
            } else {
                listOf(
                    System.getenv("HOME")?.let { "$it/.local/bin/codex" },
                    "/usr/local/bin/codex",
                    "/usr/bin/codex"
                )
            }

            for (path in commonPaths.filterNotNull()) {
                val file = File(path)
                if (isRunnable(file)) return file.absolutePath
            }

            throw CodexAppServerException(
                "Codex executable not found. Please install Codex or set CODEX_BIN environment variable."
            )
        }

        private fun startStderrLogger(process: Process, scope: CoroutineScope) {
            val stderr = process.errorStream ?: return
            scope.launch {
                try {
                    stderr.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) {
                                logger.warning("[codex stderr] $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.warning("[codex stderr] reader failed: ${e.message}")
                }
            }
        }

        private fun isWindowsCmd(executablePath: String): Boolean {
            val os = System.getProperty("os.name").lowercase()
            if (!os.contains("windows")) return false
            val lower = executablePath.lowercase()
            return lower.endsWith(".cmd") || lower.endsWith(".bat")
        }
    }
}

/**
 * App-Server 异常
 */
class CodexAppServerException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
