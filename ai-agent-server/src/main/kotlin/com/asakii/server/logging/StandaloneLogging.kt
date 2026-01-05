package com.asakii.server.logging

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 日志配置工具。
 *
 * 日志文件：
 * - server.log：所有日志的完整记录（汇总）
 * - sdk.log：Claude Agent SDK 相关日志（输入、CLI 原始输出）
 * - ws.log：RSocket/WebSocket RPC 交互日志（请求、响应）
 *
 * 日志目录：
 * - 开发模式 (runIde)：<project>/.log/
 * - 生产模式 (安装为插件)：<IDEA_LOG_DIR>/claude-code-plus/
 */
object StandaloneLogging {

  // 专用 logger 名称
  const val SDK_LOGGER = "com.asakii.sdk"
  const val WS_LOGGER = "com.asakii.ws"

  private var logDir: Path? = null
  private var configured = false

  /**
   * 获取当前配置的日志目录
   */
  fun getLogDir(): Path? = logDir

  /**
   * 配置日志系统（使用项目根目录下的 .log 子目录）
   * 适用于：StandaloneServer 独立运行、开发模式 (runIde)
   *
   * @param projectRoot 项目根目录
   */
  fun configure(projectRoot: File) {
    configureWithDir(projectRoot.toPath().resolve(".log"))
  }

  /**
   * 检测是否在 IDEA 插件环境中运行
   * 插件环境中排除了 Logback，使用 IDEA 内置的 SLF4J 实现
   */
  fun isIdeaPluginEnvironment(): Boolean {
    return try {
      // 尝试加载 Logback 类，如果失败则说明在 IDEA 插件环境中
      Class.forName("ch.qos.logback.classic.LoggerContext")
      false
    } catch (e: ClassNotFoundException) {
      true
    }
  }

  /**
   * 配置日志系统（直接指定日志目录）
   * 适用于：StandaloneServer 独立运行模式
   *
   * 注意：在 IDEA 插件环境中，Logback 被排除，SLF4J 会自动使用 IDEA 的日志实现，
   * 日志会写入 idea.log，无需调用此方法。
   *
   * @param logDirectory 日志目录路径
   */
  fun configureWithDir(logDirectory: Path) {
    if (configured) {
      println("⚠️ [StandaloneLogging] Already configured, skipping. Current logDir: ${logDir?.toAbsolutePath()}")
      return
    }

    // 检测是否在 IDEA 插件环境中
    if (isIdeaPluginEnvironment()) {
      println("📝 [StandaloneLogging] Running in IDEA plugin environment, using IDEA's built-in SLF4J implementation")
      println("📝 [StandaloneLogging] Logs will be written to idea.log")
      // Bridge java.util.logging -> SLF4J if the bridge is available in the IDE classpath
      bridgeJulToSlf4jIfAvailable()

      configured = true
      return
    }

    logDir = logDirectory
    Files.createDirectories(logDir!!)
    println("📝 [StandaloneLogging] logDir created: ${logDir!!.toAbsolutePath()}")

    val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext
        ?: run {
            println("⚠️ [StandaloneLogging] LoggerFactory is not Logback LoggerContext, skipping configuration")
            return
        }

    // Bridge java.util.logging -> SLF4J/logback so SDK CLI output also goes to server.log
    bridgeJulToSlf4jIfAvailable()

    // 0. 配置 server.log（所有日志的汇总）- 使用异步 Appender
    val serverLogFile = logDir!!.resolve("server.log").toAbsolutePath().toString()
    val serverFileAppender = createRollingFileAppender(
      loggerContext,
      "SERVER_FILE",
      serverLogFile,
      "${logDir!!.toAbsolutePath()}/server.%d{yyyy-MM-dd}.%i.log"
    )
    val serverAppender = createAsyncAppender(loggerContext, "SERVER_ASYNC", serverFileAppender)
    println("📝 [StandaloneLogging] Server log (all): $serverLogFile (async)")

    // 1. 配置 sdk.log（SDK 日志）- 使用异步 Appender
    val sdkLogFile = logDir!!.resolve("sdk.log").toAbsolutePath().toString()
    val sdkFileAppender = createRollingFileAppender(
      loggerContext,
      "SDK_FILE",
      sdkLogFile,
      "${logDir!!.toAbsolutePath()}/sdk.%d{yyyy-MM-dd}.%i.log"
    )
    val sdkAppender = createAsyncAppender(loggerContext, "SDK_ASYNC", sdkFileAppender)
    println("📝 [StandaloneLogging] SDK log: $sdkLogFile (async)")

    // 2. 配置 ws.log（RSocket/WebSocket 日志）- 使用异步 Appender
    val wsLogFile = logDir!!.resolve("ws.log").toAbsolutePath().toString()
    val wsFileAppender = createRollingFileAppender(
      loggerContext,
      "WS_FILE",
      wsLogFile,
      "${logDir!!.toAbsolutePath()}/ws.%d{yyyy-MM-dd}.%i.log"
    )
    val wsAppender = createAsyncAppender(loggerContext, "WS_ASYNC", wsFileAppender)
    println("📝 [StandaloneLogging] WebSocket log: $wsLogFile (async)")

    // 3. 配置 SDK Logger（写入 sdk.log + server.log）
    val sdkLogger = loggerContext.getLogger(SDK_LOGGER)
    sdkLogger.addAppender(sdkAppender)
    sdkLogger.addAppender(serverAppender)  // 同时写入 server.log
    sdkLogger.level = Level.DEBUG
    sdkLogger.isAdditive = false  // 不传播到 root logger

    // 4. 配置 WebSocket Logger（写入 ws.log + server.log）
    val wsLogger = loggerContext.getLogger(WS_LOGGER)
    wsLogger.addAppender(wsAppender)
    wsLogger.addAppender(serverAppender)  // 同时写入 server.log
    wsLogger.level = Level.DEBUG
    wsLogger.isAdditive = false  // 不传播到 root logger

    // 5. Root logger 写入 server.log（其他所有日志）
    val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
    rootLogger.addAppender(serverAppender)
    rootLogger.level = Level.INFO

    configured = true
    println("📝 Logging configured.")
    println("   - Server logs (all): $serverLogFile")
    println("   - SDK logs: $sdkLogFile")
    println("   - WebSocket logs: $wsLogFile")
  }

  /**
   * 创建滚动文件 appender
   */
  private fun createRollingFileAppender(
    context: LoggerContext,
    name: String,
    logFile: String,
    rollingPattern: String
  ): RollingFileAppender<ILoggingEvent> {
    val appender = RollingFileAppender<ILoggingEvent>()
    appender.context = context
    appender.name = name
    appender.file = logFile

    // 配置编码器
    val encoder = PatternLayoutEncoder()
    encoder.context = context
    encoder.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    encoder.charset = Charsets.UTF_8
    encoder.start()
    appender.encoder = encoder

    // 配置滚动策略
    val rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>()
    rollingPolicy.context = context
    rollingPolicy.setParent(appender)
    rollingPolicy.fileNamePattern = rollingPattern
    rollingPolicy.setMaxFileSize(FileSize.valueOf("10MB"))
    rollingPolicy.maxHistory = 7
    rollingPolicy.setTotalSizeCap(FileSize.valueOf("100MB"))
    rollingPolicy.start()

    appender.rollingPolicy = rollingPolicy
    appender.start()

    return appender
  }

  /**
   * 创建异步 appender
   *
   * AsyncAppender 将日志写入（包括 toString() 调用）延迟到异步线程执行。
   * 这允许使用 LazyLogMessage 包装消息，在日志线程中进行格式化，而不是阻塞工作线程。
   */
  private fun createAsyncAppender(
    context: LoggerContext,
    name: String,
    delegate: Appender<ILoggingEvent>
  ): AsyncAppender {
    val asyncAppender = AsyncAppender()
    asyncAppender.context = context
    asyncAppender.name = name

    // 配置队列大小（默认 256，增大以支持高吞吐量）
    asyncAppender.queueSize = 1024

    // 配置丢弃策略：队列满时丢弃 TRACE/DEBUG/INFO 级别的日志（保留 WARN/ERROR）
    asyncAppender.discardingThreshold = 0  // 0 表示不丢弃任何日志

    // 不包含调用者信息（提高性能）
    asyncAppender.isIncludeCallerData = false

    // 设置被包装的 appender
    asyncAppender.addAppender(delegate)

    asyncAppender.start()
    return asyncAppender
  }

  private fun bridgeJulToSlf4jIfAvailable() {
    try {
      val bridgeClass = Class.forName("org.slf4j.bridge.SLF4JBridgeHandler")
      val isInstalled = bridgeClass.getMethod("isInstalled")
      val removeHandlers = bridgeClass.getMethod("removeHandlersForRootLogger")
      val install = bridgeClass.getMethod("install")
      val installed = (isInstalled.invoke(null) as? Boolean) ?: false
      if (!installed) {
        removeHandlers.invoke(null)
        install.invoke(null)
        println("[StandaloneLogging] JUL bridged to SLF4J")
      }
    } catch (e: ClassNotFoundException) {
      println("[StandaloneLogging] SLF4JBridgeHandler not found, skip JUL bridge")
    } catch (e: Throwable) {
      println("[StandaloneLogging] Failed to bridge JUL: ${e.message}")
    }
  }
}
