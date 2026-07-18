# Installation

## Requirements

Before installing EnhancedEchest, make sure your server meets these requirements:

| Requirement | Specification |
|-------------|---------------|
| **Minecraft Version** | 1.21.11 - 26.2 |
| **Server Software** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) or compatible Paper forks |
| **Java Version** | Java 21 |

::: warning Paper 1.21.11 - 26.2 is required
EnhancedEchest requires Paper or a Paper-compatible fork (Folia, Purpur). It will **not** run on CraftBukkit, nor on Minecraft versions older than 1.21.11.
:::

::: warning Java 21 is required
The plugin is compiled for Java 21. Make sure your server runs on Java 21 or newer, otherwise it will fail to load.
:::

::: warning Using the LPX anti-exploit plugin? Update it to 3.8.4 or newer
EnhancedEchest's chest menus (`/eclist` and the management screens) use Minecraft's built-in Dialog feature. Older versions of [LPX (LPX-AntiPacketExploit)](https://builtbybit.com/resources/lpx-antipacketexploit.15709/) block those dialog packets, so the menus never open. The author fixed this in LPX [3.8.4](https://builtbybit.com/resources/lpx-antipacketexploit.15709/updates#resource-update-261684) ("Fixed dialog not working in certain situations"), so update LPX to 3.8.4 or newer if you run it.
:::

## Paper vs Folia

EnhancedEchest runs on Paper, Folia, and Paper forks such as Purpur. Almost everything behaves identically on every platform. The only difference appears when the **same chest** is opened on two screens at once.

### Opening the same chest twice

The common case is an admin running `/ee view` while the chest owner already has that chest open.

| Situation | Paper | Folia |
|-----------|-------|-------|
| Two people open the **same** chest at once | Both can view and edit it together, kept in sync | Only the first viewer is allowed in; the second is told the chest is **in use** |
| Opening **different** chests | Works for everyone | Works for everyone |
| One player, one chest at a time | Normal | Normal |

On Paper, an admin and the owner can have the same chest open side by side. On Folia, a chest is limited to one viewer at a time because different regions can run on separate threads. The second viewer simply tries again after the first closes it; no items are lost or duplicated.

### What administrators notice

When you use `/ee view` or **Clear chest** while the owner has that chest open:

- **Paper** opens the chest and keeps both screens synchronized.
- **Folia** declines the action with an "in use" message until the owner closes the chest.

Storage, multiple chests, custom names and icons, permission-granted chests, temporary chests, migration, account transfers, and all other commands work the same on Paper and Folia.

## Download

Choose your preferred download source:

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

## Installation Steps

### 1. Install the Plugin

1. **Stop your server** completely
2. Download the latest `.jar` file from a source above
3. Place it in your server's `plugins/` folder
4. **Start your server** (avoid using `/reload`, it can cause issues)

::: tip No extra dependencies
Everything the plugin needs is bundled inside the jar. You do not need to install anything else on your server.
:::

### 2. Verify Installation

Run the following in your server console or in-game to confirm the plugin loaded:

```
/plugins
```

EnhancedEchest should appear in the list with a green status. By default it uses SQLite, so it works out of the box with no further setup.

### 3. Generated Files

The plugin automatically creates its files in `plugins/EnhancedEchest/`:

| File | Description |
|------|-------------|
| `config.yml` | Main configuration: chest size, storage backend, migration |
| `enderchests.db` | SQLite database (default storage) |
| `language/<locale>/messages.yml` | Player-facing messages and the plugin prefix |
| `language/<locale>/gui.yml` | Inventory titles and chest-menu labels |

## Updating

1. **Download** the new version
2. **Stop** your server
3. **Replace** the old `.jar` file with the new one
4. **Start** your server

Your data and configuration are preserved across updates.

## Getting Help

If you run into issues:

1. Check your **console logs** for error messages
2. Report bugs on **[GitHub Issues](https://github.com/OpenVdra/EnhancedEchest/issues)**
3. Ask for help on our **[Discord](https://discord.com/invite/FJN7hJKPyb)**
