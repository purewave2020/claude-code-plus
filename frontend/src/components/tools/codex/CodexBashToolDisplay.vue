<template>
  <BashToolDisplay :tool-call="toolCall" />
</template>

<script setup lang="ts">
import type { GenericToolCall } from '@/types/display'
import BashToolDisplay from '@/components/tools/base/BashToolDisplay.vue'

interface Props {
  toolCall: GenericToolCall
}

defineProps<Props>()
</script>
