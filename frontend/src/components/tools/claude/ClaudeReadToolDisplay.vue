<template>
  <ReadToolDisplay :tool-call="toolCall" />
</template>

<script setup lang="ts">
import type { GenericToolCall } from '@/types/display'
import ReadToolDisplay from '@/components/tools/base/ReadToolDisplay.vue'

interface Props {
  toolCall: GenericToolCall
}

defineProps<Props>()
</script>
