<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

// airi-style second navigation row: top-level section tabs shown directly under
// the main nav bar on doc pages. The main (VitePress) nav keeps the site-level
// links + search + social/theme; these are the in-docs section tabs.
const { page, lang } = useData()

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
      link: `${base}/docs/getting-started`,
      actives: [
        'docs/getting-started', 'docs/commands', 'docs/permissions',
        'docs/permission-chests', 'docs/configuration', 'docs/migration', 'docs/language',
        'docs/database', 'docs/sqlite', 'docs/mysql-mariadb', 'docs/postgresql',
        'docs/ssl-tls', 'docs/cross-server', 'docs/switching-backends',
      ],
    },
    {
      icon: 'Sparkles',
      text: isVi.value ? 'Tính năng' : 'Features',
      link: `${base}/docs/features`,
      actives: ['docs/features', 'docs/larger-ender-chests', 'docs/multi-chest-system', 'docs/bedrock-support'],
    },
    {
      icon: 'Database',
      text: isVi.value ? 'Xem SQLite' : 'SQLite Viewer',
      link: 'https://sqliteviewer.app/',
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
      <LucideIcon v-if="tab.external" name="ArrowUpRight" :size="13" class="secondary-nav-tab-external" />
    </a>
  </nav>
</template>

<style scoped>
.secondary-nav-tab {
  gap: 7px;
}

.secondary-nav-tab-external {
  opacity: 0.55;
}
</style>
