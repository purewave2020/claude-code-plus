import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/composables/useSessionTab', async () => {
  const { ref, reactive } = await import('vue')

  return {
    useSessionTab: (order: number) => {
      const tab: any = {
        tabId: `tab-${order}`,
        order: ref(order),
        name: ref(''),
        backendType: ref('claude'),
        sessionId: ref(null),
        projectPath: ref(''),

        connectionState: reactive({ status: 'DISCONNECTED', lastError: null }),
        isGenerating: ref(false),
        isConnected: ref(false),

        modelId: ref<string | null>(null),
        thinkingEnabled: ref(false),
        permissionMode: ref('default'),
        skipPermissions: ref(false),

        messages: [],
        displayItems: [],
        historyState: reactive({ hasMore: false }),

        setBackendType: (type: string) => {
          tab.backendType.value = type
        },
        setInitialConnectOptions: (options: any) => {
          tab.__initialConnectOptions = { ...options }
          if (options.model !== undefined) tab.modelId.value = options.model
          if (options.permissionMode !== undefined) tab.permissionMode.value = options.permissionMode
          if (options.skipPermissions !== undefined) tab.skipPermissions.value = options.skipPermissions
        },

        setPendingSetting: vi.fn(),
        setOrder: (next: number) => {
          tab.order.value = next
        },
        touch: vi.fn(),
        disconnect: vi.fn(),
        reset: vi.fn(),
        rename: (nextName: string) => {
          tab.name.value = nextName
        },
        loadHistory: vi.fn().mockResolvedValue(undefined),
      }

      return tab
    },
  }
})

import { useSettingsStore } from '@/stores/settingsStore'
import { useSessionStore } from '@/stores/sessionStore'

describe('sessionStore.createTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('创建 codex tab 时，即使只传 backendType，也会合并默认连接参数', async () => {
    const settingsStore = useSettingsStore()
    settingsStore.settings.codexModel = 'gpt-5.2'
    settingsStore.settings.codexReasoningEffort = 'high' as any
    settingsStore.settings.codexSandboxMode = 'workspace-write' as any

    const sessionStore = useSessionStore()
    const tab: any = await sessionStore.createTab(undefined, { backendType: 'codex' })

    expect(tab.backendType.value).toBe('codex')
    expect(tab.modelId.value).toBe('gpt-5.2')
    expect(tab.__initialConnectOptions?.continueConversation).toBeUndefined()
    expect(tab.__initialConnectOptions?.resumeSessionId).toBeUndefined()
  })

  it('创建 codex tab 时，调用方传入的 resume 选项应保留，同时不丢默认 model', async () => {
    const settingsStore = useSettingsStore()
    settingsStore.settings.codexModel = 'gpt-5.2-codex'

    const sessionStore = useSessionStore()
    const tab: any = await sessionStore.createTab(undefined, {
      backendType: 'codex',
      continueConversation: true,
      resumeSessionId: 'thread_test_123',
    })

    expect(tab.backendType.value).toBe('codex')
    expect(tab.modelId.value).toBe('gpt-5.2-codex')
    expect(tab.__initialConnectOptions?.continueConversation).toBe(true)
    expect(tab.__initialConnectOptions?.resumeSessionId).toBe('thread_test_123')
  })
})

