<template>
  <EditToolDisplay :tool-call="toolCall" />
</template>

<script setup lang="ts">
import type { GenericToolCall } from '@/types/display'
import EditToolDisplay from '@/components/tools/base/EditToolDisplay.vue'

interface Props {
  toolCall: GenericToolCall
}

defineProps<Props>()
</script>
