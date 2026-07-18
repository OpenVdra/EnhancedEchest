<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

// Extra action links rendered under the "On this page" outline in the right
// aside (airi-style). The edit link is derived from the active locale's
// editLink pattern so it always points at the current page's source.
const { page, theme, lang } = useData()

const REPO = 'https://github.com/OpenVdra/EnhancedEchest'
const DISCORD = 'https://discord.com/invite/FJN7hJKPyb'
const KOFI = 'https://ko-fi.com/openvdra'

const isVi = computed(() => lang.value.startsWith('vi'))
const t = (en, vi) => (isVi.value ? vi : en)

const editHref = computed(() => {
  const pattern = theme.value.editLink?.pattern
  const path = page.value.filePath
  if (!pattern || !path) return null
  return typeof pattern === 'function'
    ? pattern(page.value)
    : pattern.replace(/:path/g, path)
})

const links = computed(() => {
  const list = []
  if (editHref.value) {
    list.push({ icon: 'Pencil', text: t('Edit this page on GitHub', 'Chỉnh sửa trang này trên GitHub'), href: editHref.value })
  }
  list.push(
    { icon: 'Star', text: t('Star on GitHub', 'Star trên GitHub'), href: REPO },
    { icon: 'MessageCircle', text: t('Chat on Discord', 'Trò chuyện trên Discord'), href: DISCORD },
    { icon: 'Heart', text: t('Support the project', 'Ủng hộ dự án'), href: KOFI },
  )
  return list
})
</script>

<template>
  <div class="aside-links">
    <a
      v-for="link in links"
      :key="link.text"
      class="aside-link"
      :href="link.href"
      target="_blank"
      rel="noreferrer"
    >
      <LucideIcon :name="link.icon" :size="16" />
      <span>{{ link.text }}</span>
    </a>
  </div>
</template>

<style scoped>
.aside-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--vp-c-divider);
}

.aside-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 10px;
  font-size: 0.8rem;
  font-weight: 500;
  line-height: 1.3;
  color: var(--vp-c-text-2);
  text-decoration: none !important;
  transition: color 0.25s, background-color 0.25s;
}

.aside-link:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-brand-soft);
}

.aside-link svg {
  flex-shrink: 0;
  color: var(--vp-c-brand-1);
}
</style>
