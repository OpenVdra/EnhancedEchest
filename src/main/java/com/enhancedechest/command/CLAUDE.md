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
| `admin/ConfigCommand`, `ImportCommand`, `ReloadCommand` | `/ee config`, `/ee import`, `/ee reload` |
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
base `enhancedechest.command.admin`.

| Subcommand | Permission | Action |
|---|---|---|
| `reload` | `admin.reload` | Reload config + language, re-apply runtime-tunable settings |
| `config` | `admin.config` | In-game `config.yml` editor (saving reloads automatically) |
| `import` | `admin.import` | Copy an old EnhancedEchest database into the active backend |
| `migrate vanilla <player>\|all` | `admin.migrate` | Vanilla ender chest → chest #1. **Online-only** |
| `migrate axvaults\|playervaultsx\|customenderchest [<player>\|all]` | `admin.migrate` | Import another plugin's data; works offline |
| `add <player> <size> [count] [duration]` | `admin.add` | Grant chest(s); `duration` makes them expire |
| `resize <player> <index> <size>` | `admin.resize` | Resize, spilling cut-off items to a temp chest. **Rejected on a PERM chest** (`admin.cannot-modify-perm`) |
| `delete <player> <count> [force]` | `admin.delete` | Delete the `count` newest **NORMAL** chests; chest #1 always kept; `force` discards items; PERM skipped |
| `view <player> [list \| index]` | `admin.view` | Open the target's chest through the **shared session** |
| `transfer <from> <to> <#index \| name \| all> [override \| temp]` | `admin.transfer` | Move a player's NORMAL chests onto another account |

## Suggestion providers

Providers run on **every keystroke**, so they are precomputed constants and never block:

- **`KNOWN_PLAYERS`** — online names first, then offline names from `PlayerNameIndex` once a prefix is
  typed, capped at `MAX_PLAYER_SUGGESTIONS` (50). Used by everything that accepts an offline target.
- **`ONLINE_PLAYERS`** — `migrate vanilla` only, which reads the live vanilla ender chest.
- **`TARGET_CHESTS`** — index suggestions for `view`, resolving the target through the name index
  (cache-only; never `Bukkit.getOfflinePlayer(String)`, which does a blocking web lookup).

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
enhancedechest.admin.config
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
