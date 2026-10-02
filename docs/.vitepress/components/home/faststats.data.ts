// Reads the live server and player counts out of FastStats' embed cards when the site is built.
//
// The cards cannot be shown in the page itself: faststats.dev sends Cross-Origin-Resource-Policy:
// same-site, so a browser refuses them on any other site, and routing them through wsrv.nl turns
// them black (it cannot draw their oklch colours). So the numbers are taken from the SVG text here
// and drawn by UsageStats.vue in the site's own style. The deploy workflow rebuilds daily to keep
// them fresh. Any failure yields null and the tiles are simply left out; it never breaks a build.

const PROJECT = '87333e13-217e-44a2-ad46-91def03a3a79'

export interface StatCard {
  value: string
  // Change against the previous 24 hours, e.g. "-7%", or null when the card shows none.
  delta: string | null
  down: boolean
}

export interface FastStatsData {
  servers: StatCard | null
  players: StatCard | null
}

export declare const data: FastStatsData

async function card(chart: string): Promise<StatCard | null> {
  try {
    const res = await fetch(
      `https://faststats.dev/embed/default:${PROJECT}:${chart}.svg?w=600&h=300&theme=dark`,
      { signal: AbortSignal.timeout(10_000) },
    )
    if (!res.ok) return null
    const svg = await res.text()
    const texts = [...svg.matchAll(/<text([^>]*)>([^<]*)<\/text>/g)]
    // The headline number is the only text drawn at 36px.
    const value = texts.find(([, attrs]) => attrs.includes('font-size="36"'))?.[2].trim()
    if (!value) return null
    const delta = texts.map(([, , text]) => text.trim()).find((text) => /^[+-]?\d+(\.\d+)?%$/.test(text)) ?? null
    return { value, delta, down: delta?.startsWith('-') ?? false }
  } catch {
    return null
  }
}

export default {
  async load(): Promise<FastStatsData> {
    const [servers, players] = await Promise.all([card('online-servers'), card('online-players')])
    return { servers, players }
  },
}
