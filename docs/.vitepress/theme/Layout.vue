<script setup>
import { computed } from 'vue'
import DefaultTheme from 'vitepress/theme'
import { useData } from 'vitepress'
import AsideLinks from '../components/aside/AsideLinks.vue'
import SecondaryNav from '../components/nav/SecondaryNav.vue'

// Wrap the default layout to add airi-style two-tier navigation (a second
// section-tab bar under the main nav) and the action links beneath the
// right-hand "On this page" outline.
const { Layout } = DefaultTheme
const { frontmatter } = useData()

// The home page has its own hero and no left sidebar, so the second bar (and
// its layout offsets) would only get in the way — show it on doc pages only.
const showSecondaryNav = computed(() => frontmatter.value.layout !== 'home')
</script>

<template>
  <SecondaryNav v-if="showSecondaryNav" />
  <Layout>
    <template #aside-outline-after>
      <AsideLinks />
    </template>
  </Layout>
</template>
