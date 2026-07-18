<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

// airi-style second navigation row: top-level section tabs shown directly under
// the main nav bar on doc pages. The main (VitePress) nav keeps the site-level
// links + search + social/theme; these are the in-docs section tabs.
const { page, lang } = useData()

const REPO = 'https://github.com/OpenVdra/EnhancedEchest'
const isVi = computed(() => lang.value.startsWith('vi'))

// Current page key, normalized to a locale-free "docs/…" path (no .md, index
// collapsed to a trailing slash) so the active-tab match is simple.
const currentKey = computed(() => {
  let p = page.value.relativePath || ''
  p = p.replace(/^vi\//, '').replace(/\.md$/, '').replace(/(^|\/)index$/, '$1')
  return p
})

const tabs = computed(() => {
  const base = isVi.value ? '/vi' : ''
  return [
    {
      icon: 'BookOpen',
      text: isVi.value ? 'Hướng dẫn' : 'Manual',
      link: `${base}/docs/manual`,
      actives: [
        'docs/manual', 'docs/download', 'docs/installation', 'docs/commands', 'docs/permissions',
        'docs/permission-chests', 'docs/configuration', 'docs/database',
        'docs/migration', 'docs/language',
      ],
    },
    {
      icon: 'List',
      text: isVi.value ? 'Nhật ký thay đổi' : 'Changelog',
      link: `${base}/docs/changelog`,
      actives: ['docs/changelog'],
    },
    {
      icon: 'Github',
      text: isVi.value ? 'Mã nguồn' : 'Source Code',
      link: REPO,
      external: true,
      actives: [],
    },
  ]
})

const isActive = (tab) => tab.actives.some(a =>
  a.endsWith('/') ? currentKey.value === a : currentKey.value.startsWith(a))
</script>

<template>
  <nav class="secondary-nav" aria-label="Sections">
    <a
      v-for="tab in tabs"
      :key="tab.text"
      class="secondary-nav-tab"
      :class="{ 'is-active': isActive(tab) }"
      :href="tab.external ? tab.link : withBase(tab.link)"
      :target="tab.external ? '_blank' : undefined"
      :rel="tab.external ? 'noreferrer' : undefined"
    >
      <LucideIcon v-if="tab.icon" :name="tab.icon" :size="15" />
      <span>{{ tab.text }}</span>
    </a>
  </nav>
</template>

<style scoped>
.secondary-nav-tab {
  gap: 7px;
}
</style>
