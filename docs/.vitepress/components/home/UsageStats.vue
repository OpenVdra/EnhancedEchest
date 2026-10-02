<script setup>
import { computed } from 'vue'
import { useData } from 'vitepress'
import { data as live } from './faststats.data'

const { lang, isDark } = useData()
const isVi = computed(() => (lang.value || '').startsWith('vi'))

const STATS_URL = 'https://faststats.dev/project/enhancedechest/minecraft-plugin'
// FastStats draws the chart for either theme, so it follows the site's light/dark switch. It is
// fetched through wsrv.nl because faststats.dev sends Cross-Origin-Resource-Policy: same-site,
// which stops a browser showing it on any other site; maxage keeps the proxy's copy a day old at most.
const chartSrc = computed(() => {
  const chart = 'https://faststats.dev/embed/default:87333e13-217e-44a2-ad46-91def03a3a79:servers-and-players.svg'
    + `?w=960&h=340&theme=${isDark.value ? 'dark' : 'light'}`
  return `https://wsrv.nl/?url=${encodeURIComponent(chart)}&output=png&w=1920&maxage=1d`
})

// Only the counts the build could read; an empty list hides the row.
const tiles = computed(() => [
  live.servers && { label: t.value.servers, ...live.servers },
  live.players && { label: t.value.players, ...live.players },
].filter(Boolean))

const t = computed(() => (isVi.value
  ? {
      servers: 'Máy chủ đang online',
      players: 'Người chơi đang online',
      vs: 'so với 24 giờ trước',
      title: 'Đang Được Tin Dùng',
      sub: 'Máy chủ và người chơi đang dùng EnhancedEchest trên khắp thế giới.',
      foot: 'Thống kê ẩn danh qua FastStats, có thể tắt bằng enabled=false trong plugins/faststats/config.properties.',
    }
  : {
      servers: 'Servers online',
      players: 'Players online',
      vs: 'vs previous 24h',
      title: 'Trusted in the Wild',
      sub: 'Servers and players running EnhancedEchest around the world.',
      foot: 'Anonymous stats via FastStats, switched off with enabled=false in plugins/faststats/config.properties.',
    }))
</script>

<template>
  <div class="usage-stats">
    <div class="usage-inner">
      <h2 class="usage-title">{{ t.title }}</h2>
      <p class="usage-sub">{{ t.sub }}</p>

      <div v-if="tiles.length" class="usage-tiles">
        <a v-for="tile in tiles" :key="tile.label" class="usage-tile" :href="STATS_URL" target="_blank" rel="noopener noreferrer">
          <span class="tile-value">{{ tile.value }}</span>
          <span class="tile-label">{{ tile.label }}</span>
          <span v-if="tile.delta" class="tile-delta" :class="tile.down ? 'is-down' : 'is-up'">
            {{ tile.down ? '▼' : '▲' }} {{ tile.delta.replace(/^[+-]/, '') }}
            <span class="tile-vs">{{ t.vs }}</span>
          </span>
        </a>
      </div>

      <a class="usage-chart" :href="STATS_URL" target="_blank" rel="noopener noreferrer">
        <img :src="chartSrc" alt="Servers and players running EnhancedEchest, from FastStats" loading="lazy" />
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

/* The chart comes in the page's own theme, so the card only frames it. */
/* The live counts, read from FastStats at build time (faststats.data.ts). */
.usage-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  max-width: 760px;
  margin: 0 auto 16px;
}

.usage-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 20px 16px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 16px;
  text-decoration: none;
  transition: border-color 0.2s ease;
}

/* The tile is an external link, but the arrow VitePress adds after those would sit under the
   delta line on its own. */
.usage-tile::after {
  display: none !important;
}

.usage-tile:hover {
  border-color: var(--vp-c-brand-1);
}

.tile-value {
  font-size: 2.6rem;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--vp-c-brand-1);
  font-variant-numeric: tabular-nums;
}

.tile-label {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--vp-c-text-2);
}

.tile-delta {
  margin-top: 6px;
  font-size: 0.78rem;
  font-weight: 600;
}

.tile-delta.is-up { color: var(--vp-c-green-1); }
.tile-delta.is-down { color: var(--vp-c-red-1); }

.tile-vs {
  margin-left: 4px;
  font-weight: 400;
  color: var(--vp-c-text-3);
}

.usage-chart {
  display: block;
  max-width: 760px;
  margin: 0 auto;
  padding: 20px 22px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-border);
  border-radius: 16px;
  box-shadow: 0 6px 24px color-mix(in srgb, var(--vp-c-text-1) 8%, transparent);
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
