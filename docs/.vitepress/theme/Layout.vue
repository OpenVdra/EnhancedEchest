<script setup>
import DefaultTheme from 'vitepress/theme'
import AsideLinks from '../components/aside/AsideLinks.vue'

// Wrap the default layout to add action links beneath the right-hand
// "On this page" outline.
const { Layout } = DefaultTheme
</script>

<template>
  <Layout>
    <template #aside-outline-after>
      <AsideLinks />
    </template>
  </Layout>
</template>
