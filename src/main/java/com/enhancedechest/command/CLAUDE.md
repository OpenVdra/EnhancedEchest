# command/

Command bodies. The **tree itself is not here** — it is built in
[EnhancedEchestBootstrap.java](../EnhancedEchestBootstrap.java) with Paper Brigadier on the
`LifecycleEvents.COMMANDS` lifecycle, **not** in `plugin.yml`. Adding a command means editing both:
the node (+ `.requires(...)` gate + suggestion provider) in the bootstrap, and the logic here.

## Files

| File | Responsibility |
|---|---|
| `EnderChestOpenCommand` | `/enderchest` (`/ec`), `/ec <#index\|name>`, `/eclist` |
| `admin/ChestAdminCommand` | `add`, `resize`, `delete`, `view`, `viewList` and the offline-target resolution |
| `admin/ChestTransferCommand` | `/ee transfer` argument parsing (the target token and the flag share one greedy argument) |
| `admin/ImportCommand`, `ReloadCommand` | `/ee import`, `/ee reload` |
| `admin/MigrateVanillaCommand`, `MigrateAxVaultsCommand`, `MigratePlayerVaultsXCommand`, `MigrateCustomEnderChestCommand` | The `/ee migrate <source>` subcommands |
| `admin/PlayerResolver` | Name → UUID for offline targets |

## Player commands

| Command | Permission | Action |
|---|---|---|
| `/enderchest` (`/ec`) | `enhancedechest.command.open` | Open per the routing rules in [../service/CLAUDE.md](../service/CLAUDE.md) |
| `/ec <#index \| name>` | `enhancedechest.command.open` | Open one chest by index or custom name (miss → `chest.unknown`) |
| `/eclist` | `enhancedechest.command.open` | Always open the management UI |

Right-clicking an ender chest block needs **no** permission — `command.open` gates the commands only
(and the dialog's "Set as main" action).

## Admin commands (`/enhancedechest`, alias `/ee`)

The root literal has **no permission of its own**; each subcommand gates on its own node. There is no
base `enhancedechest.command.admin`. The root does carry a `.requires` that is the **union** of the
admin nodes (`ADMIN_PERMISSIONS` / `hasAnyAdminPermission` in the bootstrap) — purely so the command
disappears from tab-completion for players who can run nothing under it. Brigadier only strips a node
from the tree it sends a client when *that node's own* requirement fails, so a root without one stays
visible even when every child is hidden. **Adding a subcommand means adding its node to that array**,
or its holders can no longer see `/ee` at all.

| Subcommand | Permission | Action |
|---|---|---|
| `reload` | `admin.reload` | Reload config + language, re-apply runtime-tunable settings |
| `import` | `admin.import` | Copy an old EnhancedEchest database into the active backend |
| `migrate vanilla <player>\|all` | `admin.migrate` | Vanilla ender chest → chest #1. **Online-only** |
| `migrate axvaults\|playervaultsx\|customenderchest [<player>\|all]` | `admin.migrate` | Import another plugin's data; works offline |
| `add <player> <size> [count] [duration]` | `admin.add` | Grant chest(s); `duration` makes them expire |
| `resize <player> <index> <size>` | `admin.resize` | Resize, spilling cut-off items to a temp chest. **Rejected on a PERM chest** (`admin.cannot-modify-perm`) |
| `delete <player> <count> [force]` | `admin.delete` | Delete the `count` newest **NORMAL** chests; chest #1 always kept; `force` discards items; PERM skipped |
| `view <player> [list \| index]` | `admin.view` | Open the target's chest through the **shared session**. Also registered standalone as `/endersee <player> …` — Paper aliases a whole root literal, not a subcommand, so the shorthand is a second `commands.register` sharing `viewPlayerArgument()` and the same handlers |
| `transfer <from> <to> <#index \| name \| all> [override \| temp]` | `admin.transfer` | Move a player's NORMAL chests onto another account |

## Suggestion providers

Providers run on **every keystroke**, so they are precomputed constants and never block:

- **`KNOWN_PLAYERS`** — online names first, then offline names from `PlayerNameIndex` once a prefix is
  typed, capped at `MAX_PLAYER_SUGGESTIONS` (50) and filtered to players seen within
  `commands.suggest-offline-within`. Used by everything that accepts an offline target.
- **`ONLINE_PLAYERS`** — `migrate vanilla` only, which reads the live vanilla ender chest.
- **`TARGET_CHESTS`** — index suggestions for `view`, resolving the target through the name index
  (cache-only; never `Bukkit.getOfflinePlayer(String)`, which does a blocking web lookup).

**Nothing is suggested that is not a real value.** There used to be a `suggestHeader` that pinned a fake
`(player)` / `(duration)` entry to the top of the dropdown; it was removed because the client already
renders the argument names of the tree as a usage hint above the chat bar (`<player> [<index>]`), which
is what an admin actually reads. Don't reintroduce it — name the argument node instead.

**Offline names come from the database, via `PlayerNameIndex` — never from the `playerdata` folder.**
`Bukkit.getOfflinePlayers()` builds one `OfflinePlayer` per file in that folder, and `getName()` on one
whose profile is missing from the usercache loads and decompresses that player's `.dat` file. Called per
keystroke it was thousands of NBT reads on a region thread; called once at startup it is the same
pressure in one burst, which is enough to get a memory-tight server OOM-killed. **There is no scan
anywhere in the plugin, and none may be added** — a name the DB has never seen is simply not suggested,
and the admin types it in full. `knownPlayerUuid` resolves an already-typed name from memory only:
online → name index → `Bukkit.getOfflinePlayerIfCached` (usercache, no disk, no Mojang call).

Coverage comes from writes instead: `PlayerSettingsCache.markSeenAsync` records the player's name and
`last_online` on join and quit, so every player who joins is in the `players` table from then on, and the
suggestion list trims itself to whoever has been on recently. `PlayerResolver`'s final
`getOfflinePlayer(String)` step is the one remaining disk/network touch — one named lookup, on command
execution only, never on suggestions.

## `/ee view`

Opens the target's chest by joining the **shared session** (`ChestOpener.adminOpen`), so the admin sees
and edits the *same* inventory the owner has open — no dupe is possible. Every entry point funnels
through the shared detail dialog with an admin `DetailContext` (see [../gui/CLAUDE.md](../gui/CLAUDE.md)):

- **no argument** — 0 chests → `admin.view-no-chests`, 1 → the detail dialog, 2+ → the admin picker
- **`list`** — always the picker, even for a single chest
- **`<index>`** — the detail dialog for that chest, verified to exist (`admin.chest-not-found`)
- **offline owners are supported** — the admin becomes the sole viewer and the chest persists on close

**Read-only vs editable is decided per click in `EnderChestGuiListener`, not by this command**:
`admin.view` may look (every item move is cancelled with `chest.view-only`), `admin.edit` may take and
add. An admin viewing their **own** chest is the owner and is never restricted. On Folia only one live
viewer per chest exists, so an admin and the owner cannot both be in it.

## Permission summary

```
enhancedechest.command.open       /ec, /eclist, "set as main"
enhancedechest.additional_amount.<count>.slot.<size>
                                  grant <count> chests of <size> slots (stacks; no op default)
enhancedechest.default_size.<size>
                                  base-chest size override (no op default)
enhancedechest.admin.reload
enhancedechest.admin.import
enhancedechest.admin.migrate
enhancedechest.admin.add
enhancedechest.admin.resize
enhancedechest.admin.delete
enhancedechest.admin.transfer
enhancedechest.admin.view         open another player's chest (read-only by default)
enhancedechest.admin.edit         additionally take/add items, and rename/icon/sort while viewing
enhancedechest.admin.clear        show and use the Clear chest button in the /ee view detail dialog
```

All admin nodes default to `op`. The two grant-style nodes deliberately have **no** default — they are
grants, not gates. Every node must also be declared in `plugin.yml` / `paper-plugin.yml`.

## Gotchas

- Command bodies never touch storage directly on the command thread: dispatch through the service layer
  (`ChestOpener`, `StorageGateway`, the migration services), which owns the `DbExecutor`.
- `add`, `resize`, `delete`, `view` and `transfer` accept **offline** targets (`PlayerResolver` →
  `OfflinePlayer#hasPlayedBefore` / the name index), so their handlers must not assume a `Player`.
- Anything reported back to the sender goes through `LanguageManager` keys — see
  [../lang/CLAUDE.md](../lang/CLAUDE.md).
