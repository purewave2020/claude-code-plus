/**
 * 链接识别和渲染工具
 *
 * 职责：
 * 1. 识别文本中的链接（URL、文件路径）
 * 2. 将纯文本转换为带链接的 HTML
 *
 * 注意：链接点击处理统一由 browserSecurity.ts 负责
 */

import {
  handleLinkClick as securityHandleLinkClick,
  type LinkType as SecurityLinkType,
} from './browserSecurity'

// ==================== 类型定义 ====================

export interface LinkifyResult {
  html: string
  hasLinks: boolean
}

/** 内部链接类型（用于识别） */
type InternalLinkType = 'url' | 'file'

interface LinkMatch {
  start: number
  end: number
  text: string
  href: string
  type: InternalLinkType
}

// ==================== 工具函数 ====================

/**
 * HTML 转义并保留换行
 */
function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
  }
  return text.replace(/[&<>"']/g, (m) => map[m]).replace(/\n/g, '<br>')
}

/**
 * 识别文本中的所有链接
 */
function findLinks(text: string): LinkMatch[] {
  const matches: LinkMatch[] = []

  // HTTP/HTTPS URL
  const urlPattern = /https?:\/\/[^\s<>"{}|\\^`]+/g
  let match
  while ((match = urlPattern.exec(text)) !== null) {
    matches.push({
      start: match.index,
      end: match.index + match[0].length,
      text: match[0],
      href: match[0],
      type: 'url',
    })
  }

  // @文件路径: @src/App.vue, @./relative/path.ts
  const atFilePattern = /@([.\w-/\\]+\.\w+)/g
  while ((match = atFilePattern.exec(text)) !== null) {
    // 检查是否与已有匹配重叠
    const start = match.index
    const end = match.index + match[0].length
    const overlaps = matches.some((m) => start < m.end && end > m.start)
    if (!overlaps) {
      matches.push({
        start,
        end,
        text: match[0],
        href: match[1], // 不含 @ 符号的路径
        type: 'file',
      })
    }
  }

  // 绝对路径: /path/to/file.ts, C:\path\file.ts
  // 需要在空格或行首后面
  const absolutePathPattern =
    /(?:^|[\s(])([/\\][\w\-.@/\\]+\.\w+|[A-Z]:[/\\][\w\-.@/\\]+\.\w+)/gm
  while ((match = absolutePathPattern.exec(text)) !== null) {
    const path = match[1]
    const start = match.index + match[0].indexOf(path)
    const end = start + path.length
    const overlaps = matches.some((m) => start < m.end && end > m.start)
    if (!overlaps) {
      matches.push({
        start,
        end,
        text: path,
        href: path,
        type: 'file',
      })
    }
  }

  // 按位置排序
  matches.sort((a, b) => a.start - b.start)

  return matches
}

// ==================== 公共 API ====================

/**
 * 将纯文本转换为带链接的 HTML
 * 用于消息气泡显示
 */
export function linkifyText(text: string): LinkifyResult {
  const links = findLinks(text)

  if (links.length === 0) {
    return {
      html: escapeHtml(text),
      hasLinks: false,
    }
  }

  const parts: string[] = []
  let lastIndex = 0

  for (const link of links) {
    // 添加链接前的文本
    if (link.start > lastIndex) {
      parts.push(escapeHtml(text.slice(lastIndex, link.start)))
    }

    // 添加链接
    const className = link.type === 'file' ? 'linkified-link file-link' : 'linkified-link'
    const dataType = link.type
    const escapedHref = escapeHtml(link.href)
    const escapedText = escapeHtml(link.text)

    parts.push(
      `<a href="${escapedHref}" class="${className}" data-link-type="${dataType}">${escapedText}</a>`
    )

    lastIndex = link.end
  }

  // 添加最后的文本
  if (lastIndex < text.length) {
    parts.push(escapeHtml(text.slice(lastIndex)))
  }

  return {
    html: parts.join(''),
    hasLinks: true,
  }
}

/**
 * 从点击事件中提取链接信息
 */
export function getLinkFromEvent(
  event: MouseEvent
): { href: string; type: InternalLinkType } | null {
  const target = event.target as HTMLElement
  if (target.tagName !== 'A') return null

  const href = target.getAttribute('href')
  const type = (target.getAttribute('data-link-type') as InternalLinkType) || 'url'

  if (!href) return null

  return { href, type }
}

/**
 * 处理链接点击事件
 * 委托给 browserSecurity.ts 统一处理
 *
 * @param href 链接地址
 * @param type 链接类型（'url' 或 'file'）
 * @param openFile 打开文件的回调函数（可选，用于覆盖全局回调）
 */
export function handleLinkClick(
  href: string,
  type: InternalLinkType,
  openFile?: (path: string) => void
): void {
  // 委托给 browserSecurity 统一处理
  securityHandleLinkClick(href, { openFile })
}
