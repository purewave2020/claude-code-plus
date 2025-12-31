<template>
  <WebSearchToolDisplay :tool-call="toolCall" />
</template>

<script setup lang="ts">
import type { GenericToolCall } from '@/types/display'
import WebSearchToolDisplay from '@/components/tools/base/WebSearchToolDisplay.vue'

interface Props {
  toolCall: GenericToolCall
}

defineProps<Props>()
</script>
