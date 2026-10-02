<div align="center">

<br>

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_64h.png)](https://modrinth.com/plugin/enhancedechest)
[![hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/hangar_64h.png)](https://hangar.papermc.io/Nighter/EnhancedEchest)
[![ghpages](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_64h.png)](https://openvdra.github.io/EnhancedEchest/)

</div>

## Larger Ender Chests
* **Up to 54 Slots:** A full double chest instead of 27, in steps of 9.
* **Same Block:** Right-click an ender chest or run `/ec`. The lid still opens and closes.
* **Size by Rank:** Set the base size per rank with the [`enhancedechest.default_size.<size>`](https://openvdra.github.io/EnhancedEchest/docs/access/permission-chests#default-size-permission) permission.

<div align="center">
<table>
  <tr>
    <th align="center">27 slots</th>
    <th align="center">54 slots</th>
  </tr>
  <tr>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/chest-27.webp" alt="A 27-slot ender chest named Ores" width="320"></td>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/chest-54.webp" alt="A 54-slot ender chest named Treasure Vault" width="320"></td>
  </tr>
</table>
</div>

## Multiple Chests
* **Chest List:** `/eclist`, or shift and right-click an ender chest, lists every chest a player owns.
* **Main Chest:** Players pick the one `/ec` opens. A new chest never becomes main on its own.
* **Edit Mode:** Tick it to manage a chest on click instead of opening it.

<div align="center">
<table>
  <tr>
    <th align="center">Chest List</th>
    <th align="center">Managing a Chest</th>
  </tr>
  <tr>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/eclist.webp" alt="The chest list showing seven named chests with icons" width="372"></td>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/chest-detail.webp" alt="A chest's management menu with Open, Set as main, Rename and Choose icon" width="372"></td>
  </tr>
</table>
</div>

## Names and Icons
* **Rename:** Give each chest its own name, with optional colours. It becomes the inventory title.
* **Choose an Icon:** Pick from over a thousand items, with a search box.
* **Server Toggles:** Rename, icons and sorting can each be switched off in `config.yml`.

<p align="center">
  <img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/icon-picker.webp" alt="The searchable item picker for choosing a chest icon" width="100%">
</p>

## Admin Tools
* **Manage Any Player:** Add, resize and delete chests with `/ee add`, `/ee resize` and `/ee delete`, online or offline.
* **Temporary Chests:** Give a chest that expires, such as `/ee add Steve 27 1 7d`.
* **View and Edit:** `/ee view <player>` opens the same menu the owner sees. Admins can look, edit, or clear the chest, each by permission.
* **Safe Shrinking:** Items that no longer fit after a resize or a lost rank move to a temporary chest instead of vanishing.

<p align="center">
  <img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/admin-view-detail.webp" alt="An admin viewing another player's chest, with the Clear chest button" width="372">
</p>

## Activity Log
* **Who Took What:** Every visit that added or took items is recorded with the player, the time and the changes.
* **Look Back:** Click a visit to see exactly what the chest held when it closed.
* **In Game:** Read it with `/ee log <player>`. Off by default, turn it on in `config.yml`.

<div align="center">
<table>
  <tr>
    <th align="center">One Visit</th>
    <th align="center">The Chest at That Moment</th>
  </tr>
  <tr>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/log-tooltip.webp" alt="The chest log with one visit listing the items added" width="372"></td>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/EnhancedEchest/main/docs/public/screenshots/log-snapshot.webp" alt="The contents a chest held at the end of one logged visit" width="372"></td>
  </tr>
</table>
</div>

## Storage and Migration
* **Any Database:** <img src="https://skillicons.dev/icons?i=sqlite" width="18" height="18" alt="SQLite" align="absmiddle"> SQLite out of the box, or <img src="https://skillicons.dev/icons?i=mysql" width="18" height="18" alt="MySQL" align="absmiddle"> MySQL, MariaDB and <img src="https://skillicons.dev/icons?i=postgres" width="18" height="18" alt="PostgreSQL" align="absmiddle"> PostgreSQL. Saving runs in the background.
* **Several Servers:** Share one database between servers, kept in sync with [Redis](https://openvdra.github.io/EnhancedEchest/docs/database/cross-server).
* **Import:** Bring vanilla ender chests over when players join, and import from **AxVaults**, **PlayerVaultsX** and **CustomEnderChest** with one command.

## Commands

<details>
<summary><b>Player and admin commands</b>, click to expand</summary>

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ec` | `enhancedechest.command.open` | Open the main chest, or the list with several |
| `/ec <name>`, `/ec #<index>` | `enhancedechest.command.open` | Open one chest directly |
| `/eclist` | `enhancedechest.command.open` | Open the chest list |
| `/ee add <player> <size> [count] [duration]` | `enhancedechest.admin.add` | Give chests, optionally temporary |
| `/ee resize <player> <index> <size>` | `enhancedechest.admin.resize` | Resize a chest |
| `/ee delete <player> <count> [force]` | `enhancedechest.admin.delete` | Delete the newest chests |
| `/ee view <player> [list\|index]` | `enhancedechest.admin.view` | View or edit a player's chest, also `/endersee` |
| `/ee log <player>` | `enhancedechest.admin.log` | Open a player's activity log |
| `/ee transfer <from> <to> <#index\|name\|all>` | `enhancedechest.admin.transfer` | Move chests to another account |
| `/ee import` | `enhancedechest.admin.import` | Copy data from another database |
| `/ee migrate <source>` | `enhancedechest.admin.migrate` | Import from vanilla, AxVaults, PlayerVaultsX or CustomEnderChest |
| `/ee reload` | `enhancedechest.admin.reload` | Reload config and language files |

Every permission is on the [permissions page](https://openvdra.github.io/EnhancedEchest/docs/access/permissions).

</details>

## Version Support

| Minecraft version | Server | Java | Support status |
|---|---|---|---|
| 1.21.11 – 26.2 | Paper, Purpur, Folia | 21 | Active |

## Quick FAQ

**Does it work on Folia?**  
Yes. Paper, Purpur and Folia are all supported. Spigot is not.

**Can Bedrock players use it?**  
Yes. Through [Geyser](https://geysermc.org/), the menus show as Bedrock forms with no extra setup.

**Will players lose their old ender chest items?**  
No. Turn on `migration` in `config.yml` and each player's vanilla ender chest is copied into their first chest when they join.

**Supported languages:**  
English and Vietnamese, and each player sees their own client language. Every message can be edited with MiniMessage.

Need help or want to suggest something? Join the [Discord](http://discord.com/invite/FJN7hJKPyb).

## Credits

* Free and open source on [GitHub](https://github.com/OpenVdra/EnhancedEchest), under the [GPL-3.0](https://github.com/OpenVdra/EnhancedEchest/blob/main/LICENSE).
* Anonymous usage statistics through [bStats](https://bstats.org/plugin/bukkit/EnhancedEchest/32142), which can be turned off in `plugins/bStats/config.yml`.

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142">
    <img src="https://bstats.org/signatures/bukkit/EnhancedEchest.svg" alt="EnhancedEchest bStats charts" width="100%" />
  </a>
</p>
