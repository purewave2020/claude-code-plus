/**
 * 语言服务 - 代理到 vue-i18n
 * 保持现有代码兼容
 */

import { setLocale as i18nSetLocale, getLocale as i18nGetLocale, type SupportedLocale } from '@/i18n'

export type { SupportedLocale }

class LocaleService {
  /**
   * 初始化语言服务（现在不需要做任何事，i18n 已在 main.ts 初始化）
   */
  async init(): Promise<void> {
    console.log('🌐 Locale initialized from localStorage/browser:', this.getLocale())
  }

  /**
   * 获取当前语言
   */
  getLocale(): SupportedLocale {
    return i18nGetLocale()
  }

  /**
   * 设置语言
   */
  async setLocale(locale: SupportedLocale): Promise<void> {
    i18nSetLocale(locale)
    console.log('🌐 Locale changed to:', locale)
  }

  /**
   * 获取语言代码（用于 Element Plus 等库）
   */
  getElementPlusLocale(): string {
    const localeMap: Record<SupportedLocale, string> = {
      'zh-CN': 'zh-cn',
      'zh-TW': 'zh-tw',
      'en-US': 'en',
      'ko-KR': 'ko',
      'ja-JP': 'ja'
    }
    return localeMap[this.getLocale()] || 'en'
  }

  /**
   * 是否为中文
   */
  isChinese(): boolean {
    return this.getLocale() === 'zh-CN'
  }

  /**
   * 是否为英文
   */
  isEnglish(): boolean {
    return this.getLocale() === 'en-US'
  }

  /**
   * 是否为韩语
   */
  isKorean(): boolean {
    return this.getLocale() === 'ko-KR'
  }

  /**
   * 是否为日语
   */
  isJapanese(): boolean {
    return this.getLocale() === 'ja-JP'
  }

  /**
   * 获取所有支持的语言
   */
  getSupportedLocales(): Array<{ value: SupportedLocale; label: string }> {
    return [
      { value: 'zh-CN', label: '简体中文' },
      { value: 'zh-TW', label: '繁體中文' },
      { value: 'en-US', label: 'English' },
      { value: 'ko-KR', label: '한국어' },
      { value: 'ja-JP', label: '日本語' }
    ]
  }
}

// 单例
const localeService = new LocaleService()

export default localeService
