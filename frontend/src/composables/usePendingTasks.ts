/**
 * usePendingTasks - Pending task display logic for ChatInput
 * 
 * Handles pending task filtering and display formatting
 */

import { computed, type Ref } from 'vue'
import { useI18n } from '@/composables/useI18n'

export interface PendingTask {
  id: string
  type: 'SWITCH_MODEL' | 'QUERY'
  text: string
  alias?: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  realModelId?: string
  error?: string
}

interface UsePendingTasksOptions {
  pendingTasks: Ref<PendingTask[]>
}

export function usePendingTasks(options: UsePendingTasksOptions) {
  const { pendingTasks } = options
  const { t } = useI18n()

  // Filter to show only pending and running tasks
  const visibleTasks = computed(() => {
    return pendingTasks.value.filter(
      task => task.status === 'PENDING' || task.status === 'RUNNING'
    )
  })

  // Get task display label
  function getTaskLabel(task: PendingTask): string {
    if (task.type === 'SWITCH_MODEL') {
      return `/model ${task.alias}`
    }
    return task.text.trim()
  }

  // Get task status display text
  function getTaskStatusText(status: string): string {
    const map: Record<string, string> = {
      PENDING: t('chat.taskStatus.pending'),
      RUNNING: t('chat.taskStatus.running'),
      SUCCESS: t('chat.taskStatus.success'),
      FAILED: t('chat.taskStatus.failed')
    }
    return map[status] || status
  }

  return {
    visibleTasks,
    getTaskLabel,
    getTaskStatusText
  }
}
