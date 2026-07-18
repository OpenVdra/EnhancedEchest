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
  <Layout>
    <!-- Default theme's own `.Layout` is a flex column (VPNav, VPLocalNav,
         VPSidebar, VPContent, VPFooter, in that order). `layout-top` renders
         as its first flex child, not a page-level sibling before VPNav, so on
         desktop (SecondaryNav is position:fixed, flex order is irrelevant to
         it) nothing changes; on mobile (VPNav drops out of fixed positioning
         and SecondaryNav becomes an in-flow bar) an `order` override in
         style.css reshuffles it to sit right after VPNav instead of before it. -->
    <template #layout-top>
      <SecondaryNav v-if="showSecondaryNav" />
    </template>
    <template #aside-outline-after>
      <AsideLinks />
    </template>
  </Layout>
</template>
