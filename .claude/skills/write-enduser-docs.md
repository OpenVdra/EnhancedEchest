---
name: write-enduser-docs
description: >
  Guidelines for writing or editing end-user documentation for the EnhancedEchest VitePress docs site.
  Use this skill whenever the user asks to write, rewrite, update, or review any page in docs/ or vi/docs/,
  create new documentation sections, translate docs to Vietnamese, or improve the quality/tone of existing pages.
  Also use when the user asks why docs look wrong, have spacing issues, or use the wrong component props.
---

# End-User Documentation Guidelines

This skill encodes the writing and component conventions for the EnhancedEchest VitePress docs site.
The goal is documentation that reads clearly to **server administrators and players** — not developers
reading source code.

---

## Core Writing Principles

### Write for the person who uses the feature, not the person who built it

End users care about what they can **do** and what they will **see**. They do not care about:
- How data is stored internally (SQLite, HikariCP, connection pooling, async executors)
- Anti-duplication mechanics (pending-save chains, load-on-open, dupe-safe model)
- Server scheduler internals (Folia region-aware scheduling, FoliaLib, Brigadier)
- Java/Paper-specific implementation details (shading, relocation, server tick)

If a feature exists for internal reliability (e.g., no duplication), do not feature it prominently.
Mention it only if it has a direct, visible effect the user would notice.

**Instead of:** "Inventories load fresh on open and save immediately on close, with pending-save
chaining that defeats dupe exploits."
**Write:** Nothing. Players do not notice this works; they only notice when it does not.

**Instead of:** "Built on a region-aware scheduler that runs on Paper, Folia, and Paper forks."
**Write:** If needed at all — "Supports Paper, Folia, and Purpur."

### Separate admin concerns from player concerns

Two distinct audiences read these docs:

- **Server admins**: install the plugin, configure it, manage player chests with `/ee`, set permissions.
  Primary audience for Installation, Configuration, Database, Permissions, and Commands pages.
- **Players**: open their ender chest, rename it, pick an icon, set a main.
  Primary audience for the Features page.

Write each page with its audience in mind. The Features page should read as if explaining to a player.
The Configuration page can be more technical since only admins read it.

### Be concise

One sentence per feature card description. Two sentences maximum for a section intro.
Cut any clause that does not add information the reader will act on.

---

## Formatting Rules

### No emoji in content

Do not use emoji characters (📦, 🛡️, 💾, etc.) anywhere in page content: headings, card titles,
section titles, inline text. Emoji belong only in the YAML frontmatter `features:` block on the
home page — and even there, prefer removing them.

### No em dashes

Do not use the em dash character (`—`). Replace with a comma, period, colon, or rewrite the sentence.

**Wrong:** "Opens on right-click or via `/ec` — the block keeps its animation."
**Right:** "Opens on right-click or via `/ec`. The block keeps its animation."

### No horizontal rules as section separators

Do not use `---` between sections within a page. Use heading hierarchy instead.

---

## VitePress Components

### DocCard

Displays a clickable card linking to another page. Used inside `<CardGrid>`.

```md
<DocCard icon="Package" title="Installation" link="/docs/installation" desc="One-line description." />
```

**`icon` prop**: a Lucide icon name (PascalCase string). If omitted, no icon renders and there is
no extra spacing — the card layout handles this correctly. Never pass an emoji string as `icon`.

**`desc` prop**: one sentence, plain text, no markdown. Keep it under 80 characters.

### FeatureCard

Displays a content card with a title and rich slot content. Used inside `<CardGrid>`.

```md
<FeatureCard icon="Archive" title="Multiple Chests Per Player">
Players can own several ender chests at once, managed from an in-game menu.
</FeatureCard>
```

**`icon` prop**: same Lucide name convention as DocCard. When omitted, no icon box renders.
Do not pass an empty string — just omit the prop entirely.

### Available Lucide Icon Names

Use these names exactly (PascalCase, no spaces):

| Purpose | Icon name |
|---------|-----------|
| Download / Install | `Download` |
| Features / Sparkle | `Sparkles` |
| Commands / Terminal | `Terminal` |
| Permissions / Shield | `ShieldCheck` |
| Settings / Config | `Settings` |
| Storage / Box | `Package` |
| Multi-chest / Archive | `Archive` |
| Customize / Color | `Palette` |
| Permissions / Keys | `Key` |
| Language / Globe | `Globe` |
| Migration / Swap | `ArrowRightLeft` |
| Bedrock / Layers | `Layers` |
| Click / Cursor | `MousePointer2` |
| Resize / Sliders | `Sliders` |
| List / Menu | `List` |
| Main chest / Star | `Star` |
| Admin tools | `Wrench` |
| View / Eye | `Eye` |
| Stats / Chart | `BarChart2` |
| Refresh / Sync | `RefreshCw` |

To add a new icon: import it in `docs/.vitepress/components/icon/LucideIcon.vue` inside the `ICONS` map.

### CardGrid

Wraps a set of DocCard or FeatureCard components in a responsive grid. No props needed.

```md
<CardGrid>
<DocCard ... />
<DocCard ... />
</CardGrid>
```

---

## Page-Type Conventions

### Home page (`docs/index.md`, `vi/index.md`)

The `features:` section in YAML frontmatter is for high-level selling points visible to visitors.
Use **user-facing benefits**, not technical capabilities:

Good features to highlight:
- Slot count (players see more space)
- Multiple chests (players manage their own)
- Custom names and icons (players personalize)
- Permission-based grants (admins give perks by rank)
- Bedrock compatibility (Geyser players see native UI)
- Open source

Do not highlight: database backend, no-duplication, Folia support.

### Welcome / index page (`docs/docs/index.md`)

Two sections:
1. **Quick Navigation** — DocCards to all main pages, each with a Lucide `icon` prop.
2. **What X Adds** — FeatureCards explaining player-facing benefits, each with a Lucide `icon` prop.
   Do not include "Real Persistence" or "Dupe-Proof" cards.

### Features page

Sections for each major feature visible to players or admins. Structure:

```md
## Section Title {#anchor}

One or two sentence intro.

<CardGrid>
<FeatureCard icon="..." title="...">
Content here.
</FeatureCard>
</CardGrid>
```

Exclude or minimize:
- Database Storage (mention storage backend only on the Database page)
- No Item Duplication (internal detail, not user-facing)
- Cross-Platform / Folia Ready (put in Installation requirements, not Features)
- Update Notifications (admin-only, not a feature worth a full section)

### Commands page

Use `<CommandRow>` component for each command. Write descriptions in 1-2 sentences maximum.
Do not wrap in `<div class="command-section">`. Do not mention Brigadier, Paper internals,
or offline player database mechanics.

```md
<CommandRow commands="/ec" :aliases="['/enderchest']" permission="enhancedechest.command.open">

Opens your ender chest. One sentence of behavior. Second sentence only if needed.

</CommandRow>
```

### Permissions page

Do not use `<BaseTable>` or `<PermRow>`. Write as plain markdown:

```md
## Player

**`permission.node.here`**
One sentence describing what this unlocks.

## Admin

**`enhancedechest.admin.add`** — `/ee add`: one-line description.
**`enhancedechest.admin.resize`** — `/ee resize`: one-line description.
```

The two-line format (`**node**` then description on next line) is for standalone permissions with detail.
The one-line format (`**node** — command: description`) is for the admin command permission list.

### Configuration page

Uses `<ConfigProperty>` and `<ConfigGroup>` components — keep these, they are not tables.
Remove technical jargon from descriptions:
- `pool-size`: "Maximum number of pooled connections. Only applies to MySQL, MariaDB, and PostgreSQL."
- Backup group: omit "no one is kicked" or "safe while server runs" — that is assumed.

### Database page

This page is for server admins only. Technical content (connection strings, SQL) is appropriate here.
However, remove: HikariCP mentions, "dupe-safe load/save model" phrasing, driver relocation details.

---

## Quick Checklist Before Submitting

- [ ] No emoji in headings, card titles, or body text
- [ ] No em dash (`—`) anywhere
- [ ] No `---` horizontal rules between sections
- [ ] All DocCard and FeatureCard `icon` props use PascalCase Lucide names
- [ ] No mention of HikariCP, FoliaLib, Brigadier, pending-save, or dupe-safe on user-facing pages
- [ ] Database/persistence is not featured on Features or Welcome pages
- [ ] Commands page: `<CommandRow>` directly, descriptions are 1-2 sentences
- [ ] Permissions page: plain markdown bold format, no BaseTable/PermRow
- [ ] Feature sections focus on what the player/admin does and sees
