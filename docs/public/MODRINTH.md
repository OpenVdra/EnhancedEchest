![Banner](https://cdn.modrinth.com/data/xgEWccga/images/484cb792ed1b21c4ad2cae8aa082b0d13d1bd554.png)

<div align="center">

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/enhancedechest)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/enhancedechest-double-echest-plugin-%E2%9C%A8-26-1-2-26-2-%EF%B8%8F.136442/)
[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/Nighter/EnhancedEchest)
[![Documentation](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/documentation/ghpages_vector.svg)](https://openvdra.github.io/EnhancedEchest/)
[![discord-plural](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-plural_46h.png)](http://discord.com/invite/FJN7hJKPyb)

</div>

The vanilla ender chest is stuck at 27 slots, one per player, forever. **EnhancedEchest** fixes that: up to **54 slots**, as many chests per player as you want, each with its own name and icon, all managed from one clean in-game menu.

## Highlights

- **Up to 54 slots**: a full double chest instead of 27, in steps of 9. Same block, same right-click.

- **Multiple chests per player**: switch, rename, and set icons from an in-game menu (`/eclist`), no commands needed.

- [**Permission-based grants**](https://openvdra.github.io/EnhancedEchest/docs/permissions#default-size-permission): hand out extra chests by rank. They sync automatically and revoke cleanly, items spill safely instead of vanishing.

- **Admin tools**: add, resize, delete, or view any player's chests, online or offline. Temporary chests can auto-expire.

- **Any database**: <img src="https://skillicons.dev/icons?i=sqlite" width="18" height="18" alt="SQLite" style="vertical-align:middle;margin:0 2px" /> SQLite out of the box, or plug into <img src="https://skillicons.dev/icons?i=mysql" width="18" height="18" alt="MySQL" style="vertical-align:middle;margin:0 2px" /> MySQL, <span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;background:#242938;border-radius:5px;vertical-align:middle;margin:0 2px;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="13" height="13" alt="MariaDB" style="display:block" /></span> MariaDB, <img src="https://skillicons.dev/icons?i=postgres" width="18" height="18" alt="PostgreSQL" style="vertical-align:middle;margin:0 2px" /> PostgreSQL. Saving happens in the background, so it never lags your server.

- **Painless migration**: imports vanilla ender chests automatically, plus one-command imports from **AxVaults**, **PlayerVaultsX**, and **CustomEnderChest**.

- **Bedrock-ready**: menus work natively through Geyser, zero setup.

- **Fully translatable**: every message is editable, with MiniMessage formatting.

## Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/f693c05c-7427-489b-aa41-b68f3341cda1" alt="The chest list menu showing several owned ender chests" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/76bc97fa-1dcb-4e39-8bde-9504ebc4d768" alt="A chest's management menu" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/573814dd-6f58-4e9c-b65a-58842e3ba2a2" alt="The rename prompt for a chest" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/ce6b235b-980c-4403-86d3-503c25f32d77" alt="The searchable item picker for choosing a chest icon" />
</p>

## Commands

**Player**

| Command | Permission | Does |
|---|---|---|
| `/ec` | `enhancedechest.command.open` | Open your main chest (or the menu, if you own several) |
| `/ec <name>` / `/ec #<index>` | `enhancedechest.command.open` | Open one specific chest directly |
| `/eclist` | `enhancedechest.command.open` | Open the chest management menu |

**Admin** (`/ee`)

| Command | Permission | Does |
|---|---|---|
| `/ee add <player> <size> [count] [duration]` | `enhancedechest.admin.add` | Grant chest(s), optionally temporary |
| `/ee resize <player> <index> <size>` | `enhancedechest.admin.resize` | Resize a chest (overflow spills safely) |
| `/ee delete <player> <count> [force]` | `enhancedechest.admin.delete` | Delete newest chests (first one is always kept) |
| `/ee view <player> [list\|index]` | `enhancedechest.admin.view` | View or edit another player's chest |
| `/ee transfer <from> <to> <#index\|name\|all>` | `enhancedechest.admin.transfer` | Move chest(s) onto another account |
| `/ee import` | `enhancedechest.admin.import` | Copy all data from an old database into the active one |
| `/ee migrate vanilla\|axvaults\|playervaultsx\|customenderchest` | `enhancedechest.admin.migrate` | Import from vanilla, AxVaults, PlayerVaultsX, or CustomEnderChest |
| `/ee reload` | `enhancedechest.admin.reload` | Reload config and language files |

## Get started

**Minecraft** 1.21.11 - 26.2 · **Server** Paper / Folia / Purpur · **Java** 21+

Drop the `.jar` into `plugins/` and restart. SQLite works out of the box, zero config.

EnhancedEchest is **free and open source**. Fork it, build on it, or contribute back.

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142">
    <img src="https://bstats.org/signatures/bukkit/EnhancedEchest.svg" alt="EnhancedEchest bStats charts" width="100%" />
  </a>
</p>
