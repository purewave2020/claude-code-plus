<template>
  <WriteToolDisplay :tool-call="toolCall" />
</template>

<script setup lang="ts">
import type { GenericToolCall } from '@/types/display'
import WriteToolDisplay from '@/components/tools/base/WriteToolDisplay.vue'

interface Props {
  toolCall: GenericToolCall
}

defineProps<Props>()
</script>
