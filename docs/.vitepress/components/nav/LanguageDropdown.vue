<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useData, withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

defineProps({ screenMenu: Boolean })

const { lang, page } = useData()
const open = ref(false)
const root = ref(null)
const isVi = computed(() => lang.value.startsWith('vi'))

const localizedPath = (locale) => {
  let path = page.value.relativePath || 'index.md'
  path = path.replace(/^vi\//, '').replace(/\.md$/, '').replace(/(^|\/)index$/, '$1')
  return withBase(`${locale === 'vi' ? '/vi' : ''}/${path}`.replace(/\/$/, '/') || '/')
}

const items = computed(() => [
  { flag: '🇺🇸', text: 'English', href: localizedPath('en'), active: !isVi.value },
  { flag: '🇻🇳', text: 'Tiếng Việt', href: localizedPath('vi'), active: isVi.value },
])

const toggle = () => { open.value = !open.value }
const close = () => { open.value = false }
const onDocClick = (event) => { if (root.value && !root.value.contains(event.target)) close() }
const onKey = (event) => { if (event.key === 'Escape') close() }

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div ref="root" class="language-dd" :class="{ open, 'screen-menu': screenMenu }">
    <button
      class="language-dd-btn"
      type="button"
      :aria-expanded="open"
      :aria-label="isVi ? 'Đổi ngôn ngữ' : 'Change language'"
      aria-haspopup="menu"
      @click="toggle"
    >
      <LucideIcon name="Languages" :size="17" />
      <svg class="language-dd-caret" width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="m6 9 6 6 6-6" />
      </svg>
    </button>

    <Transition name="language-dd">
      <div v-if="open" class="language-dd-menu" role="menu">
        <a
          v-for="item in items"
          :key="item.text"
          class="language-dd-item"
          :class="{ active: item.active }"
          role="menuitem"
          :aria-current="item.active ? 'page' : undefined"
          :href="item.href"
          @click="close"
        >
          <span class="language-dd-item-label">
            <span class="language-dd-flag" aria-hidden="true">{{ item.flag }}</span>
            <span>{{ item.text }}</span>
          </span>
          <LucideIcon v-if="item.active" name="Check" :size="15" />
        </a>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.language-dd {
  position: relative;
  display: inline-flex;
  align-items: center;
  align-self: stretch;
  height: var(--vp-nav-height);
  margin: 0 4px;
}

.language-dd.screen-menu {
  display: none;
}

.language-dd-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  box-sizing: border-box;
  height: 36px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  font-family: inherit;
  line-height: 1;
  color: var(--vp-c-text-1);
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s, border-color 0.2s;
}

.language-dd-btn:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-default-soft);
}

.language-dd-caret {
  display: block;
  flex: 0 0 auto;
  opacity: 0.7;
  transition: transform 0.22s ease;
}

.language-dd.open .language-dd-caret {
  transform: rotate(180deg);
}

.language-dd-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 100;
  min-width: 168px;
  padding: 6px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  background-color: color-mix(in srgb, var(--vp-c-bg) 80%, transparent);
  backdrop-filter: blur(12px) saturate(150%);
  -webkit-backdrop-filter: blur(12px) saturate(150%);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.22);
}

.language-dd-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 12px;
  border-radius: 8px;
  color: var(--vp-c-text-1);
  font-size: 0.875rem;
  font-weight: 500;
  text-decoration: none !important;
  transition: color 0.18s, background-color 0.18s;
}

.language-dd-item:hover,
.language-dd-item.active {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-brand-soft);
}

.language-dd-item-label {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.language-dd-flag {
  font-size: 1rem;
  line-height: 1;
}

.language-dd-enter-active {
  animation: language-dd-in 0.2s ease-out;
}

.language-dd-leave-active {
  animation: language-dd-in 0.15s ease-in reverse;
}

@keyframes language-dd-in {
  from {
    opacity: 0;
    transform: translateY(-8px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
