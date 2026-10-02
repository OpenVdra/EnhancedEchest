<div align="center">

<img src="docs/public/logo.png" alt="EnhancedEchest logo" width="160">

# EnhancedEchest

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_64h.png)](https://modrinth.com/plugin/enhancedechest)
[![hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/hangar_64h.png)](https://hangar.papermc.io/Nighter/EnhancedEchest)
[![ghpages](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_64h.png)](https://openvdra.github.io/EnhancedEchest/)

Bigger ender chests, and several of them per player, for **Paper**, **Purpur** and **Folia**.

</div>

## Overview
* **Larger Chests:** Up to 54 slots instead of 27, sized per rank by permission.
* **Multiple Chests:** Each player owns several, with their own names and icons, managed from an in-game menu.
* **Admin Tools:** Add, resize, view and transfer any player's chests, online or offline, with temporary chests that expire.
* **Activity Log:** See who added or took what, and the chest exactly as it was after each visit.
* **Storage:** SQLite, MySQL, MariaDB or PostgreSQL, shared between servers through Redis.

Every feature is explained on the [documentation site](https://openvdra.github.io/EnhancedEchest/).

<div align="center">
<table>
  <tr>
    <td align="center"><img src="docs/public/screenshots/eclist.webp" alt="The chest list showing seven named chests with icons" width="372"></td>
    <td align="center"><img src="docs/public/screenshots/chest-54.webp" alt="A 54-slot ender chest named Treasure Vault" width="320"></td>
  </tr>
</table>
</div>

## Requirements

| Minecraft | Server | Java |
| :--- | :--- | :---: |
| 1.21.11 – 26.2 | Paper, Purpur or Folia | 21 |

Spigot and CraftBukkit are not supported. Bedrock players joining through Geyser get the menus as Bedrock forms.

## Installation
1. Download the latest `.jar` from [Modrinth](https://modrinth.com/plugin/enhancedechest), [Hangar](https://hangar.papermc.io/Nighter/EnhancedEchest) or [Releases](https://github.com/OpenVdra/EnhancedEchest/releases).
2. Place it in the server's `plugins/` folder.
3. Restart the server. SQLite works with no setup.

## Configuration
* **Files:** `plugins/EnhancedEchest/config.yml`, plus `messages.yml` and `gui.yml` for each language under `language/`.
* **Reload:** Most settings apply with `/ee reload`. The few that need a restart are marked in the file.
* **Reference:** Every key is documented on the [configuration page](https://openvdra.github.io/EnhancedEchest/docs/configuration/).

## Commands

| Command | Description |
| :--- | :--- |
| `/ec [name\|#index]` | Open the main chest, or one chest directly |
| `/eclist` | Open the chest list |
| `/ee add\|resize\|delete` | Give, resize or delete a player's chests |
| `/ee view <player>` | View or edit a player's chest |
| `/ee log <player>` | Open a player's activity log |
| `/ee transfer`, `/ee import`, `/ee migrate` | Move chests between accounts, databases and other plugins |
| `/ee reload` | Reload config and language files |

Permissions are on the [permissions page](https://openvdra.github.io/EnhancedEchest/docs/access/permissions).

## Building
A single Gradle module on Java 21.

```bash
git clone https://github.com/OpenVdra/EnhancedEchest.git
cd EnhancedEchest
./gradlew build
```

The plugin jar lands in `build/libs/`.

| Task | Command |
| :--- | :--- |
| Dev server (Paper, with the plugin) | `./gradlew runServer` |
| Dev client that joins it | `./gradlew runClient` |
| Both at once | `./gradlew runDev` |

Contributions are welcome. English and Vietnamese are included; translation PRs are welcome too.

## Statistics
Anonymous usage statistics are sent to [FastStats](https://faststats.dev/project/enhancedechest/minecraft-plugin). Turn them off with `enabled=false` in `plugins/faststats/config.properties`.

<div align="center">
<table>
  <tr>
    <td align="center"><a href="https://faststats.dev/project/enhancedechest/minecraft-plugin"><img src="https://faststats.dev/embed/default:87333e13-217e-44a2-ad46-91def03a3a79:online-servers.svg?w=480&h=180&theme=dark" alt="Online servers" width="420"></a></td>
    <td align="center"><a href="https://faststats.dev/project/enhancedechest/minecraft-plugin"><img src="https://faststats.dev/embed/default:87333e13-217e-44a2-ad46-91def03a3a79:online-players.svg?w=480&h=180&theme=dark" alt="Online players" width="420"></a></td>
  </tr>
  <tr>
    <td colspan="2" align="center"><a href="https://faststats.dev/project/enhancedechest/minecraft-plugin"><img src="https://faststats.dev/embed/default:87333e13-217e-44a2-ad46-91def03a3a79:servers-and-players.svg?w=960&h=340&theme=dark" alt="Servers and players over the last week" width="852"></a></td>
  </tr>
</table>
</div>

## License
Licensed under the [GPL-3.0](LICENSE).
