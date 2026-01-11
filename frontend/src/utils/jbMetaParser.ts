/**
 * JetBrains 元数据解析器
 *
 * 解析工具结果中的 JetBrains 特定元数据。
 * 元数据格式：[jb:key=value]，放在结果开头。
 *
 * 示例：
 * [jb:historyTs=1704672000000]
 * [jb:isOverwrite=true]
 * Edited File: `src/main.ts`
 */

export interface JbMeta {
  /** LocalHistory 时间戳（毫秒） */
  historyTs?: number
  /** 是否为覆写操作（Write 工具） */
  isOverwrite?: boolean
  /** 是否支持回滚（项目内文件才支持） */
  canRollback?: boolean
}

/** 元数据正则：匹配 [jb:key=value] 格式 */
const JB_META_PATTERN = /^\[jb:(\w+)=([^\]]+)\]\s*/gm

/**
 * 从工具结果中解析 JetBrains 元数据
 *
 * @param result 工具结果文本
 * @returns { meta, content } 解析出的元数据和去除元数据后的内容
 */
export function parseJbMeta(result: string): { meta: JbMeta; content: string } {
  const meta: JbMeta = {}
  let content = result

  // 匹配所有 [jb:key=value] 格式的元数据
  let match: RegExpExecArray | null
  const regex = new RegExp(JB_META_PATTERN)

  while ((match = regex.exec(result)) !== null) {
    const [, key, value] = match
    switch (key) {
      case 'historyTs':
        meta.historyTs = parseInt(value, 10)
        break
      case 'isOverwrite':
        meta.isOverwrite = value === 'true'
        break
      case 'canRollback':
        meta.canRollback = value === 'true'
        break
      // 可扩展更多元数据类型
    }
  }

  // 移除所有元数据行
  content = result.replace(JB_META_PATTERN, '').trimStart()

  return { meta, content }
}

/**
 * 检查结果中是否包含 JetBrains 元数据
 *
 * @param result 工具结果文本
 * @returns 是否包含元数据
 */
export function hasJbMeta(result: string): boolean {
  return JB_META_PATTERN.test(result)
}

/**
 * 从结果中提取 historyTs 时间戳
 *
 * @param result 工具结果文本
 * @returns 时间戳，如果不存在返回 undefined
 */
export function extractHistoryTs(result: string): number | undefined {
  const { meta } = parseJbMeta(result)
  return meta.historyTs
}

/**
 * 检查是否为覆写操作
 *
 * @param result 工具结果文本
 * @returns 是否为覆写，如果没有元数据返回 undefined
 */
export function isOverwriteOperation(result: string): boolean | undefined {
  const { meta } = parseJbMeta(result)
  return meta.isOverwrite
}

/**
 * 检查是否支持回滚
 *
 * @param result 工具结果文本
 * @returns 是否支持回滚，如果没有元数据返回 undefined
 */
export function canRollback(result: string): boolean | undefined {
  const { meta } = parseJbMeta(result)
  return meta.canRollback
}
