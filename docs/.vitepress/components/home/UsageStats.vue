<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'

const { lang } = useData()
const isVi = computed(() => (lang.value || '').startsWith('vi'))

const BSTATS_URL = 'https://bstats.org/plugin/bukkit/EnhancedEchest/32142'
const CHART_SRC = 'https://bstats.org/signatures/bukkit/EnhancedEchest.svg'

const t = computed(() => (isVi.value
  ? {
      title: 'Đang Được Tin Dùng',
      sub: 'Máy chủ và người chơi đang dùng EnhancedEchest trên khắp thế giới.',
      foot: 'Thống kê ẩn danh qua bStats, bạn có thể tắt trong plugins/bStats/config.yml.',
    }
  : {
      title: 'Trusted in the Wild',
      sub: 'Servers and players running EnhancedEchest around the world.',
      foot: 'Anonymous stats via bStats, you can switch it off in plugins/bStats/config.yml.',
    }))
</script>

<template>
  <div class="usage-stats">
    <div class="usage-inner">
      <h2 class="usage-title">{{ t.title }}</h2>
      <p class="usage-sub">{{ t.sub }}</p>

      <a class="usage-chart" :href="BSTATS_URL" target="_blank" rel="noopener noreferrer">
        <img :src="CHART_SRC" alt="EnhancedEchest bStats charts" loading="lazy" />
      </a>

      <p class="usage-foot">{{ t.foot }}</p>
    </div>
  </div>
</template>

<style scoped>
/* Top padding matches the Contributors section (64px) so the chart sits with the same breathing
   room above it; the bottom gap to Contributors is provided by that section's own top padding. */
.usage-stats {
  padding: 64px 24px 0;
}

.usage-inner {
  max-width: 860px;
  margin: 0 auto;
  text-align: center;
}

.usage-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--vp-c-text-1);
  margin: 0 0 10px;
  letter-spacing: -0.02em;
  border: 0;
}

.usage-sub {
  color: var(--vp-c-text-2);
  font-size: 0.97rem;
  line-height: 1.7;
  margin: 0 0 32px;
}

/* bStats renders the chart with dark text on a transparent background, so a light card keeps it
   readable in dark mode too. */
.usage-chart {
  display: block;
  max-width: 760px;
  margin: 0 auto;
  padding: 20px 22px;
  background: #ffffff;
  border: 1px solid var(--vp-c-border);
  border-radius: 16px;
  box-shadow: 0 6px 24px color-mix(in srgb, var(--vp-c-text-1) 8%, transparent);
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;
}

.usage-chart:hover {
  border-color: var(--vp-c-brand-1);
  transform: translateY(-3px);
  box-shadow: 0 12px 30px color-mix(in srgb, var(--vp-c-brand-1) 16%, transparent);
}

.usage-chart img {
  display: block;
  width: 100%;
  height: auto;
}

.usage-foot {
  margin: 18px 0 0;
  font-size: 0.8rem;
  color: var(--vp-c-text-3);
}
</style>
