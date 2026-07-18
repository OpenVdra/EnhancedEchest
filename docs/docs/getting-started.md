# Getting Started

<img class="page-banner" src="/banner.png" alt="EnhancedEchest banner" />

Meet **EnhancedEchest** and explore everything it brings to your Minecraft server.

<CardGrid>

<DocCard icon="Sparkles" title="Features" link="/docs/features" desc="See everything EnhancedEchest adds for your players." />

<DocCard icon="Settings" title="Configuration" link="/docs/configuration" desc="Set up chest size, storage, backups, and more." />

<DocCard icon="ArrowRightLeft" title="Migration" link="/docs/migration" desc="Import players' existing vanilla ender chest data." />

<DocCard icon="Globe" title="Localization" link="/docs/language" desc="All player-facing text is editable and translatable." />

</CardGrid>

## Requirements

| Requirement | Specification |
|-------------|---------------|
| **Minecraft Version** | 1.21.11 - 26.2 |
| **Server Software** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) or compatible Paper forks |
| **Java Version** | Java 21 |

::: tip Running Folia?
Everything works the same as on Paper, with one admin-facing exception: if an admin opens a player's chest with `/ee view` while that player already has it open, Paper lets both view it together, while Folia asks the admin to wait until the player closes it. No items are ever lost either way.
:::

::: warning Using the LPX anti-exploit plugin? Update it to 3.8.4 or newer
EnhancedEchest's chest menus use Minecraft's built-in Dialog feature, which older versions of [LPX](https://builtbybit.com/resources/lpx-antipacketexploit.15709/) block. Update to 3.8.4 or newer if you run it.
:::

## Download {#download}

<div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 1.5rem 0;">
  <a href="https://modrinth.com/plugin/enhancedechest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg" alt="Modrinth" style="height: 24px;">
    Modrinth
  </a>
  <a href="https://www.spigotmc.org/resources/enhancedechest-double-echest-plugin-%E2%9C%A8-26-1-2-26-2-%EF%B8%8F.136442/" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg" alt="Spigot" style="height: 24px;">
    Spigot
  </a>
  <a href="https://hangar.papermc.io/Nighter/EnhancedEchest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg" alt="Hangar" style="height: 24px;">
    Hangar
  </a>
</div>

::: info Modrinth release
The Modrinth page is being prepared and may not be public yet. Until it goes live, grab the latest build from **Spigot** or **Hangar** above.
:::

## Install

1. **Stop your server** completely
2. Download the latest `.jar` file from a source above
3. Place it in your server's `plugins/` folder
4. **Start your server** (avoid using `/reload`)

::: tip No extra dependencies
Everything the plugin needs, including the database driver, is bundled inside the jar. By default it uses SQLite, so it works out of the box with no further setup. See [Configuration](/docs/configuration) and [Database](/docs/database) for what's created and how to change it.
:::

## Updating

Download the new version, stop your server, replace the old `.jar` file, then start it again. Your data and configuration are preserved across updates.

## Need Help?

Check your **console logs** for error messages, report bugs on [GitHub Issues](https://github.com/OpenVdra/EnhancedEchest/issues), or ask on our [Discord](https://discord.com/invite/FJN7hJKPyb).
