/**
 * 统一日志框架
 *
 * 支持日志级别控制和模块过滤
 */

export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
  NONE = 4
}

interface LoggerConfig {
  level: LogLevel
  enabledModules: Set<string> | null  // null 表示启用所有模块
  timestamp: boolean
}

const isDev = typeof import.meta !== 'undefined' && import.meta.env?.DEV

const config: LoggerConfig = {
  level: LogLevel.DEBUG,
  enabledModules: null,
  timestamp: false
}

/**
 * 设置日志级别
 */
export function setLogLevel(level: LogLevel) {
  config.level = level
}

/**
 * 设置启用的模块
 * @param modules 模块名称数组，null 表示启用所有
 */
export function setEnabledModules(modules: string[] | null) {
  config.enabledModules = modules ? new Set(modules) : null
}

/**
 * 是否启用时间戳
 */
export function setTimestamp(enabled: boolean) {
  config.timestamp = enabled
}

function shouldLog(level: LogLevel, module?: string): boolean {
  if (level < config.level) return false
  if (config.enabledModules && module && !config.enabledModules.has(module)) return false
  return true
}

function formatMessage(module: string | undefined, message: string): string {
  const parts: string[] = []
  if (config.timestamp) {
    parts.push(`[${new Date().toISOString()}]`)
  }
  if (module) {
    parts.push(`[${module}]`)
  }
  parts.push(message)
  return parts.join(' ')
}

function safeStringify(value: unknown): string {
  try {
    const seen = new WeakSet<object>()
    return JSON.stringify(value, (_key, val) => {
      if (typeof val === 'bigint') {
        return val.toString()
      }
      if (typeof val === 'object' && val !== null) {
        if (seen.has(val)) return '[Circular]'
        seen.add(val)
      }
      return val
    })
  } catch {
    return String(value)
  }
}

function formatArgs(args: unknown[]): unknown[] {
  if (args.length === 0) return args
  return args.map(arg => {
    if (arg instanceof Error) return arg
    if (typeof arg === 'object' && arg !== null) {
      return safeStringify(arg)
    }
    return arg
  })
}

/**
 * 创建模块专用的 logger
 */
export function createLogger(module: string) {
  return {
    debug(message: string, ...args: any[]) {
      if (shouldLog(LogLevel.DEBUG, module)) {
        console.debug(formatMessage(module, message), ...formatArgs(args))
      }
    },

    info(message: string, ...args: any[]) {
      if (shouldLog(LogLevel.INFO, module)) {
        console.info(formatMessage(module, message), ...formatArgs(args))
      }
    },

    warn(message: string, ...args: any[]) {
      if (shouldLog(LogLevel.WARN, module)) {
        console.warn(formatMessage(module, message), ...formatArgs(args))
      }
    },

    error(message: string, ...args: any[]) {
      if (shouldLog(LogLevel.ERROR, module)) {
        console.error(formatMessage(module, message), ...formatArgs(args))
      }
    }
  }
}

// 预定义的模块 loggers
export const loggers = {
  session: createLogger('SessionStore'),
  rpc: createLogger('RPC'),
  stream: createLogger('StreamEvent'),
  display: createLogger('Display'),
  claude: createLogger('ClaudeService'),
  agent: createLogger('AiAgentService')
}

// 全局 logger（无模块前缀）
export const log = {
  debug(message: string, ...args: any[]) {
    if (shouldLog(LogLevel.DEBUG)) {
      console.debug(message, ...formatArgs(args))
    }
  },

  info(message: string, ...args: any[]) {
    if (shouldLog(LogLevel.INFO)) {
      console.info(message, ...formatArgs(args))
    }
  },

  warn(message: string, ...args: any[]) {
    if (shouldLog(LogLevel.WARN)) {
      console.warn(message, ...formatArgs(args))
    }
  },

  error(message: string, ...args: any[]) {
    if (shouldLog(LogLevel.ERROR)) {
      console.error(message, ...formatArgs(args))
    }
  }
}
