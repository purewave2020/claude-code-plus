/**
 * 图片处理相关的 composable
 * 包括图片上传、预览、添加到上下文等功能
 */
import { ref } from 'vue'
import type { ContextDisplayType } from '@/types/display'
import type { ImageReference } from '@/types/enhancedMessage'

// 支持的图片 MIME 类型常量
export const VALID_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'] as const
export type ValidImageType = typeof VALID_IMAGE_TYPES[number]

export interface UseImageHandlingOptions {
  /** 添加上下文回调 */
  onContextAdd?: (context: ImageReference) => void
  /** 插入图片到编辑器回调 */
  onInsertToEditor?: (base64: string, mimeType: string) => void
  /** 检查光标是否在最前面 */
  isCursorAtStart?: () => boolean
  /** Toast 提示函数 */
  showToast?: (message: string, duration?: number) => void
}

export function useImageHandling(options: UseImageHandlingOptions = {}) {
  // 图片预览状态
  const previewVisible = ref(false)
  const previewImageSrc = ref('')

  /**
   * 读取图片文件为 base64
   */
  function readImageAsBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = (e) => {
        const result = e.target?.result as string
        // 移除 data:image/xxx;base64, 前缀
        const base64 = result.split(',')[1]
        resolve(base64)
      }
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
  }

  /**
   * 验证图片类型是否支持
   */
  function isValidImageType(mimeType: string): mimeType is ValidImageType {
    return VALID_IMAGE_TYPES.includes(mimeType as ValidImageType)
  }

  /**
   * 将图片添加到上下文
   */
  async function addImageToContext(file: File) {
    console.log(`🖼️ [addImageToContext] 开始处理图片: ${file.name}`)

    try {
      // 验证文件类型
      if (!isValidImageType(file.type)) {
        console.error(`🖼️ [addImageToContext] 不支持的图片格式: ${file.type}`)
        options.showToast?.(`不支持的图片格式: ${file.type}`)
        return
      }

      // 读取图片为 base64
      console.log('🖼️ [addImageToContext] 读取图片为 base64...')
      const base64Data = await readImageAsBase64(file)
      console.log(`🖼️ [addImageToContext] base64 长度: ${base64Data.length}`)

      // 创建图片引用（生成唯一 ID 以区分同名图片）
      const imageRef: ImageReference = {
        id: `img_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        type: 'image',
        displayType: 'TAG' as ContextDisplayType,
        uri: `image://${file.name}`,
        name: file.name,
        mimeType: file.type,
        base64Data: base64Data,
        size: file.size
      }

      console.log('🖼️ [addImageToContext] 创建图片引用:', {
        type: imageRef.type,
        name: imageRef.name,
        mimeType: imageRef.mimeType,
        size: imageRef.size,
        base64Length: base64Data.length
      })

      // 添加到上下文列表
      options.onContextAdd?.(imageRef)
      console.log('🖼️ [addImageToContext] 已发送 context-add 事件')
    } catch (error) {
      console.error('🖼️ [addImageToContext] 读取图片失败:', error)
      options.showToast?.('读取图片失败')
    }
  }

  /**
   * 处理图片粘贴事件
   */
  async function handlePasteImage(file: File) {
    console.log('📋 [handlePasteImage] 接收到粘贴图片:', file.name)

    // 判断光标是否在最前面
    const isAtStart = options.isCursorAtStart?.() ?? true

    if (isAtStart) {
      // 光标在最前面，作为上下文
      console.log('📋 [handlePasteImage] 光标在最前面，将图片作为上下文')
      await addImageToContext(file)
    } else {
      // 光标不在最前面，插入到编辑器中
      console.log('📋 [handlePasteImage] 光标不在最前面，将图片插入编辑器')
      const base64 = await readImageAsBase64(file)
      options.onInsertToEditor?.(base64, file.type)
    }
  }

  /**
   * 处理图片文件选择
   */
  async function handleImageFileSelect(files: FileList | null) {
    if (!files || files.length === 0) return

    // 判断光标是否在最前面
    const isAtStart = options.isCursorAtStart?.() ?? true

    for (let i = 0; i < files.length; i++) {
      if (isAtStart) {
        // 光标在最前面：作为上下文处理
        await addImageToContext(files[i])
      } else {
        // 光标不在最前面：插入到编辑器中
        const base64 = await readImageAsBase64(files[i])
        options.onInsertToEditor?.(base64, files[i].type)
      }
    }
  }

  /**
   * 打开图片预览
   */
  function openImagePreview(src: string) {
    previewImageSrc.value = src
    previewVisible.value = true
  }

  /**
   * 关闭图片预览
   */
  function closeImagePreview() {
    previewVisible.value = false
    previewImageSrc.value = ''
  }

  /**
   * 根据 ImageReference 获取预览 URL
   */
  function getImagePreviewUrl(imageRef: ImageReference): string {
    return `data:${imageRef.mimeType};base64,${imageRef.base64Data}`
  }

  return {
    // 状态
    previewVisible,
    previewImageSrc,
    // 方法
    readImageAsBase64,
    isValidImageType,
    addImageToContext,
    handlePasteImage,
    handleImageFileSelect,
    openImagePreview,
    closeImagePreview,
    getImagePreviewUrl
  }
}
