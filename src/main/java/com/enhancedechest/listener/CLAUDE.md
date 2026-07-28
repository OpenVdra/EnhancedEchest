# listener/

Bukkit event handlers. They are deliberately thin: each one recognises a situation and hands it to the
service layer. Business logic does not live here.

| File | Event | Responsibility |
|---|---|---|
| `VanillaEnderChestListener` | `PlayerInteractEvent` | Right-click an ender chest block → our GUI |
| `EnderChestGuiListener` | `InventoryClickEvent` / `InventoryDragEvent` / `InventoryCloseEvent` | Read-only and take-only guards; detach on close |
| `ChestListMenuListener` | `InventoryClickEvent` | Clicks in the inventory-mode chest chooser |
| `PlayerSettingsListener` | join / quit | Cache lifecycle: pin + preload, unpin + write-back |
| `PlayerQuitListener` | quit | Detach backstop for a chest still open at quit |
| `JoinMigrationListener` | join | Auto-migrate the vanilla ender chest when `migration.enabled` |
| `TempChestJoinNotifyListener` | join | Chat + action bar + sound reminder about pending temp-chest items |

## Right-click (`VanillaEnderChestListener`)

- Handles **`EquipmentSlot.HAND` only**. The interact event fires once per hand; without the filter one
  right-click starts two overlapping open flows for the same chest (see the spurious-close hazard in
  [../service/CLAUDE.md](../service/CLAUDE.md)).
- Opening by block needs **no permission** — `enhancedechest.command.open` gates the commands only.
- **Shift + right-click** is a shortcut to the chest list, gated by `enderchest.shift-click-list`; when
  off, a sneaking click opens like any other click.
- The lid animation is **not** started here. It is driven by the inventory open/close lifecycle, because
  several chests open a dialog first and a dialog has no close event to pair an eager `open()` with —
  the lid would stay stuck open.

## Inventory guards (`EnderChestGuiListener`)

Our inventory is identified by `inv.getHolder() instanceof EnderChestHolder`. Two rules on click and
drag:

1. **Read-only viewers** — a non-owner without `enhancedechest.admin.edit` (an admin who opened via
   `/ee view` with only `admin.view`). Anything that would change the shared top inventory is cancelled
   with `chest.view-only` on the action bar; they still see live updates.
2. **Temp chests are take-only for everyone**, admins included. Deposits into the top inventory are
   cancelled (`chest.temp-take-only`, throttled deny sound); take-outs are untouched.

On close the listener calls `ChestSessionManager.detach(...)`, which removes the viewer from the shared
session and, on the **last** viewer, persists. `PlayerQuitListener` repeats the detach as a backstop for
a player who disconnects with a chest open.

## Join / quit lifecycle (`PlayerSettingsListener`)

- **Join** — `storage.pin(uuid)` (a pinned owner is never evicted while online), then
  `settingsCache.preloadSettings(uuid)`, whose `loadSettings` call is also what **materializes the
  player's chest rows** in the cache. This is the prefetch the whole lazy-cache model depends on.
- **Quit** — evict the settings entry and the sort cooldown, `storage.unpin(uuid)`, and
  `autosave.flushQuitterLater(uuid)`, so a leaver's changes reach the database within seconds and their
  memory is freed. A rejoin before that runs simply re-pins them; the eviction then declines and the
  still-warm rows are reused.

`JoinMigrationListener` early-returns when migration is off, so it must **never** be relied on to
perform the preload. `EnhancedEchestPlugin.onEnable` repeats the pin + preload for players already
online, since a `/reload` or hot-load fires no join event for them.

## Gotchas

- Registration is by hand in `EnhancedEchestPlugin.onEnable` — a new listener has to be added there, and
  the constructor arguments come from the already-wired services.
- These handlers run on a region/main thread. Anything touching storage must go through the service
  layer, which dispatches onto the `DbExecutor`.
- Join handlers must not do blocking work: `JoinMigrationListener` and `TempChestJoinNotifyListener`
  both hop straight onto the executor, so that the steady state (already migrated, no temp chests) costs
  nothing on the join thread even during a mass reconnect.
