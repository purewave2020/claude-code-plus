package com.asakii.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 统一日志工具
 *
 * 基于 SLF4J 的日志门面，自动适配不同环境：
 * - IDEA 插件环境：使用 IDEA 内置的 SLF4J 实现，输出到 idea.log
 * - Standalone 环境：使用 Logback，输出到控制台/文件
 *
 * 用法：
 * ```kotlin
 * // 方式 1：使用委托属性
 * class MyClass {
 *     companion object {
 *         private val logger by logger()
 *     }
 * }
 *
 * // 方式 2：直接获取
 * private val logger = getLogger("MyClass")
 * private val logger = getLogger<MyClass>()
 *
 * // 使用
 * logger.info("message")
 * logger.debug("message")
 * logger.warn("message", exception)
 * logger.error("message", exception)
 * ```
 */

/**
 * 获取 Logger（使用类名）
 */
inline fun <reified T> getLogger(): Logger = LoggerFactory.getLogger(T::class.java)

/**
 * 获取 Logger（使用名称）
 */
fun getLogger(name: String): Logger = LoggerFactory.getLogger(name)

/**
 * 获取 Logger（使用 Class）
 */
fun getLogger(clazz: Class<*>): Logger = LoggerFactory.getLogger(clazz)

/**
 * 委托属性方式获取 Logger
 *
 * 用法：
 * ```kotlin
 * companion object {
 *     private val logger by logger()
 * }
 * ```
 */
inline fun <reified T> T.logger(): Lazy<Logger> = lazy { LoggerFactory.getLogger(T::class.java) }

/**
 * 顶层函数的 Logger 委托
 */
fun logger(name: String): Lazy<Logger> = lazy { LoggerFactory.getLogger(name) }

// ==================== 控制台输出配置 ====================

@PublishedApi
internal val consoleOutputEnabled: Boolean by lazy {
    System.getProperty("logging.console.enabled", "true").toBoolean()
}

@PublishedApi
internal val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

@PublishedApi
internal fun formatTime(): String = LocalDateTime.now().format(timeFormatter)

// ==================== Lambda 扩展函数（默认输出到控制台）====================

/**
 * INFO 级别日志（Lambda 版本，延迟计算消息，同时输出到控制台）
 */
inline fun Logger.info(message: () -> String) {
    if (isInfoEnabled) {
        val msg = message()
        info(msg)
        if (consoleOutputEnabled) {
            println("${formatTime()} INFO  [${name.substringAfterLast('.')}] $msg")
        }
    }
}

/**
 * DEBUG 级别日志（Lambda 版本，延迟计算消息，同时输出到控制台）
 */
inline fun Logger.debug(message: () -> String) {
    if (isDebugEnabled) {
        val msg = message()
        debug(msg)
        if (consoleOutputEnabled) {
            println("${formatTime()} DEBUG [${name.substringAfterLast('.')}] $msg")
        }
    }
}

/**
 * WARN 级别日志（Lambda 版本，延迟计算消息，同时输出到控制台）
 */
inline fun Logger.warn(message: () -> String) {
    if (isWarnEnabled) {
        val msg = message()
        warn(msg)
        if (consoleOutputEnabled) {
            println("${formatTime()} WARN  [${name.substringAfterLast('.')}] $msg")
        }
    }
}

/**
 * WARN 级别日志（Lambda 版本，带异常，同时输出到控制台）
 */
inline fun Logger.warn(throwable: Throwable?, message: () -> String) {
    if (isWarnEnabled) {
        val msg = message()
        warn(msg, throwable)
        if (consoleOutputEnabled) {
            println("${formatTime()} WARN  [${name.substringAfterLast('.')}] $msg")
            throwable?.printStackTrace()
        }
    }
}

/**
 * ERROR 级别日志（Lambda 版本，延迟计算消息，同时输出到控制台）
 */
inline fun Logger.error(message: () -> String) {
    if (isErrorEnabled) {
        val msg = message()
        error(msg)
        if (consoleOutputEnabled) {
            System.err.println("${formatTime()} ERROR [${name.substringAfterLast('.')}] $msg")
        }
    }
}

/**
 * ERROR 级别日志（Lambda 版本，带异常，同时输出到控制台）
 */
inline fun Logger.error(throwable: Throwable?, message: () -> String) {
    if (isErrorEnabled) {
        val msg = message()
        error(msg, throwable)
        if (consoleOutputEnabled) {
            System.err.println("${formatTime()} ERROR [${name.substringAfterLast('.')}] $msg")
            throwable?.printStackTrace(System.err)
        }
    }
}

// ==================== 异步日志扩展函数 ====================

private val asyncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
    Thread(r, "AsyncLogger").apply { isDaemon = true }
}

/**
 * 异步记录 info 日志（在后台线程执行消息格式化和日志输出）
 * 适用于消息格式化开销较大的场景
 */
fun Logger.asyncInfo(message: () -> String) {
    if (isInfoEnabled) {
        asyncExecutor.execute {
            try {
                info(message())
            } catch (_: Exception) {
                // 忽略异步日志错误
            }
        }
    }
}

/**
 * 异步记录 debug 日志
 */
fun Logger.asyncDebug(message: () -> String) {
    if (isDebugEnabled) {
        asyncExecutor.execute {
            try {
                debug(message())
            } catch (_: Exception) {
                // 忽略异步日志错误
            }
        }
    }
}
