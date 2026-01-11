package com.asakii.plugin.logging

import com.intellij.openapi.diagnostic.Logger

/**
 * IDEA Logger 的 Kotlin 扩展函数
 *
 * 提供 lambda 风格的日志调用，与 unified-logging 模块保持一致的 API 风格。
 * 延迟计算消息内容，只有在日志级别启用时才执行 lambda。
 *
 * 用法：
 * ```kotlin
 * import com.asakii.plugin.logging.info
 * import com.asakii.plugin.logging.debug
 * import com.asakii.plugin.logging.warn
 * import com.asakii.plugin.logging.error
 *
 * logger.info { "消息内容" }
 * logger.debug { "调试信息: $variable" }
 * logger.warn { "警告" }
 * logger.error(exception) { "错误信息" }
 * ```
 */

/**
 * INFO 级别日志（Lambda 版本，延迟计算消息）
 */
inline fun Logger.info(message: () -> String) {
    if (isDebugEnabled || isTraceEnabled || true) { // IDEA Logger 没有 isInfoEnabled，INFO 默认启用
        info(message())
    }
}

/**
 * DEBUG 级别日志（Lambda 版本，延迟计算消息）
 */
inline fun Logger.debug(message: () -> String) {
    if (isDebugEnabled) {
        debug(message())
    }
}

/**
 * TRACE 级别日志（Lambda 版本，延迟计算消息）
 */
inline fun Logger.trace(message: () -> String) {
    if (isTraceEnabled) {
        trace(message())
    }
}

/**
 * WARN 级别日志（Lambda 版本，延迟计算消息）
 */
inline fun Logger.warn(message: () -> String) {
    warn(message())
}

/**
 * WARN 级别日志（Lambda 版本，带异常）
 */
inline fun Logger.warn(throwable: Throwable?, message: () -> String) {
    warn(message(), throwable)
}

/**
 * ERROR 级别日志（Lambda 版本，延迟计算消息）
 *
 * 注意：使用 logError 而非 error，避免与已废弃的 Logger.error(Object) 成员函数冲突。
 * 成员函数优先级高于扩展函数，如果使用 error 作为名称，Kotlin 会调用废弃的成员函数。
 */
inline fun Logger.logError(message: () -> String) {
    error(message(), null as Throwable?)
}

/**
 * ERROR 级别日志（Lambda 版本，带异常）
 *
 * 注意：使用 logError 而非 error，避免与已废弃的 Logger.error(Object) 成员函数冲突。
 */
inline fun Logger.logError(throwable: Throwable?, message: () -> String) {
    error(message(), throwable)
}
