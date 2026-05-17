<script setup lang="ts">
defineProps<{
  id: string;
}>();
</script>

<template>
  <section class="page-panel">
    <h2>资源详情</h2>
    <p>当前资源 ID：{{ id }}</p>
  </section>
</template>
