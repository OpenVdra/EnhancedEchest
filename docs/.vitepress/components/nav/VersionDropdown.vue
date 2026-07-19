<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useData, withBase } from 'vitepress'
import LucideIcon from '../icon/LucideIcon.vue'

defineProps({
  screenMenu: Boolean,
})

// Custom click-to-open version menu (airi-style). VitePress's own flyout opens
// on hover; this one only opens on click, closes on click-outside / Escape, and
// reveals with a downward slide + subtle fade (airi's slideUpAndFade).
const VERSION = 'v1.0.12'
const REPO = 'https://github.com/OpenVdra/EnhancedEchest'

const { lang, page } = useData()
const isVi = computed(() => lang.value.startsWith('vi'))
const isChangelog = computed(() =>
  page.value.relativePath?.replace(/^vi\//, '') === 'docs/changelog.md'
)

const open = ref(false)
const root = ref(null)

const items = computed(() => [
  { icon: 'List', text: isVi.value ? 'Nhật ký thay đổi' : 'Changelog', href: withBase(isVi.value ? '/vi/docs/changelog' : '/docs/changelog'), active: isChangelog.value },
  { icon: 'Tag', text: isVi.value ? 'Bản phát hành' : 'Releases', href: `${REPO}/releases`, external: true },
  { icon: 'Bug', text: isVi.value ? 'Báo lỗi' : 'Report a bug', href: `${REPO}/issues`, external: true },
])

const toggle = () => { open.value = !open.value }
const close = () => { open.value = false }

const onDocClick = (e) => { if (root.value && !root.value.contains(e.target)) close() }
const onKey = (e) => { if (e.key === 'Escape') close() }

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
  <div ref="root" class="version-dd" :class="{ open, 'screen-menu': screenMenu }">
    <button
      class="version-dd-btn"
      type="button"
      :aria-expanded="open"
      aria-haspopup="menu"
      @click="toggle"
    >
      <span>{{ VERSION }}</span>
      <svg class="version-dd-caret" width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="m6 9 6 6 6-6" />
      </svg>
    </button>

    <Transition name="version-dd">
      <div v-if="open" class="version-dd-menu" role="menu">
        <a
          v-for="item in items"
          :key="item.text"
          class="version-dd-item"
          :class="{ active: item.active }"
          role="menuitem"
          :aria-current="item.active ? 'page' : undefined"
          :href="item.href"
          :target="item.external ? '_blank' : undefined"
          :rel="item.external ? 'noreferrer' : undefined"
          @click="close"
        >
          <LucideIcon :name="item.icon" :size="15" />
          <span class="version-dd-item-text">{{ item.text }}</span>
          <LucideIcon v-if="item.external" class="version-dd-item-external" name="ArrowUpRight" :size="14" />
        </a>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.version-dd {
  position: relative;
  display: inline-flex;
  align-items: center;
  align-self: stretch;
  height: var(--vp-nav-height);
  margin: 0 4px;
}

.version-dd.screen-menu {
  display: none;
}

.version-dd-btn {
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
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1;
  color: var(--vp-c-text-1);
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s, border-color 0.2s;
}

.version-dd-btn:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-default-soft);
}

.version-dd-caret {
  display: block;
  flex: 0 0 auto;
  transition: transform 0.22s ease;
  opacity: 0.7;
}

.version-dd.open .version-dd-caret {
  transform: rotate(180deg);
}

.version-dd-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  min-width: 184px;
  padding: 6px;
  /* Frosted glass inspired by Airi's translucent background and backdrop blur. */
  background-color: color-mix(in srgb, var(--vp-c-bg) 80%, transparent);
  backdrop-filter: blur(12px) saturate(150%);
  -webkit-backdrop-filter: blur(12px) saturate(150%);
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.22);
  z-index: 100;
}

.version-dd-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--vp-c-text-1);
  text-decoration: none !important;
  transition: color 0.18s, background-color 0.18s;
}

.version-dd-item:hover,
.version-dd-item.active {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-brand-soft);
}

.version-dd-item > svg:first-child {
  flex-shrink: 0;
  opacity: 0.8;
}

.version-dd-item-text {
  flex: 1;
}

.version-dd-item-external {
  flex-shrink: 0;
  opacity: 0.55;
}

/* airi-style slide-down + subtle fade reveal. */
.version-dd-enter-active {
  animation: version-dd-in 0.2s ease-out;
}

.version-dd-leave-active {
  animation: version-dd-in 0.15s ease-in reverse;
}

@keyframes version-dd-in {
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
