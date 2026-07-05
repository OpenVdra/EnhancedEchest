# Open / save flow, shared sessions & dupe-safety

> The single most important part of the system. **Do not regress the dupe-safety model.**
> Cross-refs: [storage-and-schema.md](storage-and-schema.md) (DB serialization), [commands-and-permissions.md](commands-and-permissions.md) (`/ee view`, read-only), [expiry-and-temp-chests.md](expiry-and-temp-chests.md) (item-moving ops).

The guiding invariant is **no item duplication**: a chest's contents exist in exactly one place at a
time. This is enforced on two levels:

- **In memory** — every open chest is backed by **one shared `Inventory`** (the *session*), so two
  concurrent viewers (e.g. owner + admin) mutate the same `ItemStack[]` and Bukkit serialises their
  moves. Item-level duping between viewers is structurally impossible on a single-threaded platform.
- **At the DB** — a chest is loaded fresh on its first open and written back when its **last** viewer
  closes; a re-open waits for any in-flight save of that same chest first (`pendingSaves` / `waitPending`).

The dupe-safety core (the `sessions` registry, attach/detach, `runExclusive`, `forceCloseAll`) lives in
one closed class, `ChestSessionManager` (package `com.enhancedechest.service`). The GUI-flow layers sit
on top of it: `ChestOpener` decides *what* to open and drives the dialogs, `ChestSpillService` runs the
item-moving ops, and all async storage work is dispatched through the shared `DbExecutor`.

## Open routing (`/ec`, right-click) — `ChestOpener.open(player, sourceBlock)`

First **reconciles permission-granted chests** (see [commands-and-permissions.md](commands-and-permissions.md#permission-granted-chests)):
on the entity thread it computes the player's permission target, then `listChestsAsync().thenCompose(reconcile)`
so the chest list it routes on is already in sync (common case: nothing changed → no extra query). Then it
decides what to show:

- **0 or 1 real chest, no temp chest** → open it directly. The index comes straight from the list
  already in hand (no `getPrimaryIndex` query — with one non-TEMP chest the answer is the same);
  only a player with **no** chests at all still goes to the DB, to bootstrap chest #1 via `createChest`.
- **2+ chests, an explicit main flagged *and* caller has `enhancedechest.command.open`** → open the
  flagged main directly.
- **2+ chests otherwise** (no main chosen, or no permission), or **any TEMP chest present** → show the
  `/eclist` management dialog.

"Real chest" counts **both NORMAL and PERM** (`kind != TEMP`): a PERM chest behaves exactly like a NORMAL
one for routing and can itself be the flagged main. A main is **never** auto-assigned (`is_primary = 0` on
insert; deletes don't promote a survivor). The list marks the main with a gold `★`. See
[ui-dialogs.md](ui-dialogs.md) and [storage-and-schema.md](storage-and-schema.md) for the
primary-resolution details.

## The shared live-inventory registry

`ChestSessionManager.sessions` is a `ConcurrentHashMap<SaveKey(owner,index), Session>`. A `Session` holds:

| field | meaning |
|-------|---------|
| `inv` | the one shared `Inventory` (null until the first load finishes) |
| `viewers` | UUIDs currently viewing it |
| `viewerBlocks` | per-viewer source block, for the lid open/close animation |
| `waiting` | opens queued until the first load completes |
| `ready` / `closing` | lifecycle flags |

**Every session mutation runs on a single bookkeeping thread** via `onGlobal(Runnable)` — the main
thread on Paper, the global region thread on Folia (`foliaLib.getScheduler().isGlobalTickThread()` /
`runNextTick`). This removes registry-level races on both platforms. The DB read **and the byte→ItemStack
decode** both run on the async executor (the stored bytes are immutable, and the decoded stacks reach the
global thread through the future's happens-before edge, untouched by any other thread) — the global thread
only builds the `Inventory`. The synchronous *encode* on save is the load-bearing half: it only ever
happens once **all** viewers have closed, so it never races a live edit. **Do not move encode off-thread.**

### Opening — `ChestSessionManager.open` is the single funnel

Every open path goes through `ChestSessionManager.open(player, owner, index, sourceBlock)` (formerly
`openShared`): `ChestOpener.open` → `openPrimaryChest`/`openChest`; the dialog "Open" button →
`openChest`; admin → `adminOpen` — all of which call `sessions.open(...)`.
**If you add a new way to open a chest, route it here** — a second independently-loaded `Inventory`
re-introduces duping.

1. On the player's entity thread: a request for the chest they are **already viewing** is dropped (a
   stale duplicate — reopening would churn a save/load cycle and replay the lid sound); a *different*
   chest GUI is closed first (its close fires `detach`, see below). Then hop to `onGlobal` → `decideOpen`.
2. `decideOpen` (global thread):
   - **Live session exists & not closing** → attach. On **Folia**, if another viewer already holds it,
     deny with `chest.in-use` (single-viewer rule, below). If `ready`, `addViewerAndOpen`; else queue in
     `waiting`.
   - **No session** → create one, put it in the map, then `waitPending(owner,index)` → async
     `loadAndDecode` (row load **and** `CodecException`-guarded decode, both on the DB executor) →
     `finishCreate` on the global thread.
3. `finishCreate` builds the shared `Inventory` (`buildSharedInventory` — just `createInventory` +
   `setContents` of the already-decoded stacks), marks the session `ready`, and flushes the `waiting`
   queue via `addViewerAndOpen`. A codec failure arrived as the future's error and aborts the open
   (`chest.load-failed`, row untouched). If a force-close superseded the session mid-load, waiters get
   `chest.not-found`.
4. `addViewerAndOpen` registers the viewer on the global thread, then `player.openInventory(inv)` on the
   player's entity thread (and plays the open lid animation if a source block was supplied).

### Overlapping opens & the spurious-close hazard

Opening an inventory while another is open makes Bukkit **close the current view first, firing an
`InventoryCloseEvent`**. If that close is for the *same shared inventory* being re-shown, `detach`
would treat it as the viewer leaving — tearing down and persisting the session while the player's GUI
stays open on a now-orphaned `Inventory` whose later edits are never saved (a dupe on the next fresh
load). On Folia an open spans many hops (entity → DB ×2 → entity → global → DB → global → entity), so
fast players easily start two overlapping open flows for one chest. Three guards keep this impossible:

1. `VanillaEnderChestListener` handles only `EquipmentSlot.HAND` (the interact event fires per hand —
   one right-click must not start two open flows).
2. `decideOpen` keeps **one `Pending` per player** in `waiting` (a later request replaces the earlier),
   so `finishCreate` never double-opens.
3. `addViewerAndOpen` skips `openInventory` when the player is **already viewing that exact inventory**,
   and after opening re-verifies on the bookkeeping thread that the session is still live and the viewer
   still registered — closing the view if the attach was superseded by a racing close/force-close.

Additionally, both entry points **drop stale open requests instead of cycling the GUI**: a player
physically can't click a block or type a command while a chest GUI is open, so a request that arrives
with one open is spam queued before the GUI appeared. `ChestOpener.open` returns immediately when *any*
`EnderChestHolder` GUI is open; `ChestSessionManager.open` returns when the player already views the
**same** `(owner, index)` chest and only closes (→ `detach`) when they are switching to a different one.
Without this, each spammed right-click ran a full close → save → load → reopen cycle, replaying the lid
open/close sounds every time.

### Closing & saving — `detach` + `persist`

The GUI close and quit listeners call `ChestSessionManager.detach(player, holder)` (they no longer call `save`):

1. On the global thread, remove the player from `viewers` (and play the close animation from their
   `viewerBlocks` entry).
2. If a force-close set `closing`, do nothing (that path owns the save).
3. If this was the **last** viewer (`viewers` and `waiting` both empty), remove the session and
   `persist` it.

`persist(session)` mirrors the old `save`: encode the shared contents **synchronously** on the global
thread, then write on the async executor, registering the future in `pendingSaves` keyed by
`(owner,index)`. An emptied TEMP chest deletes its row instead (via `runExclusive`). Encode failures
abort the write (data is **not** corrupted).

> A non-last viewer closing does **not** save — the remaining viewers keep editing the one live
> inventory. This is what makes concurrent editing safe and avoids redundant writes.

### Force-close for item-moving ops — `forceCloseAll`

Admin resize/delete and the expiry sweeper must mutate a chest that may be open in several viewers.
`forceCloseAll(owner, index)` (replaces the old `forceCloseIfOpen`):

1. Global thread: set `closing`, dispatch `closeInventory()` to **every** viewer on their entity thread.
2. After all closes complete, `persist` the (now quiescent) shared inventory and remove the session.
3. The returned future completes once the save is registered in `pendingSaves`, so the caller can chain
   `runExclusive(...)` and have the DB op serialise behind that save.

Because the persist runs only after all viewer screens have closed, the encode reads an inventory no
one is editing — safe even on Folia. See [expiry-and-temp-chests.md](expiry-and-temp-chests.md) for the
full item-moving transaction model.

## Concurrent editing: Paper vs Folia

- **Paper** — all viewers' inventory events run on the main thread, so a shared inventory is
  fully safe: **owner and admin (and multiple admins) can edit the same chest at once.**
- **Folia** — viewers may live on different region threads, where a shared `ItemStack[]` is unsafe. So
  Folia allows **only one live viewer per chest**; a second opener (including the owner if an admin is
  viewing) is denied with `chest.in-use`. `decideOpen` enforces this via `foliaLib.isFolia()` +
  `isOccupiedByOther`.

## DB-level serialization primitives (unchanged)

- `pendingSaves: Map<SaveKey, CompletableFuture<Void>>` — in-flight save/op per `(owner,index)`.
- `waitPending(owner,index)` — an open waits on the prior write before loading.
- `runExclusive(owner,index, dbWork)` — chains arbitrary DB work behind the pending future and registers
  its own marker, so a concurrent open waits for it.

## Threading summary

- Storage methods are **synchronous** and thread-agnostic (see `EnderChestStorage` Javadoc).
- The `com.enhancedechest.service` layer is the **only** dispatcher onto the async pool, and it goes
  through the shared `DbExecutor` (daemon pool `EnhancedEchest-db`, capped per backend by the plugin:
  4 threads on SQLite's single connection, ~2× `database.pool-size` on MySQL/PostgreSQL — threads past
  the JDBC pool only block inside Hikari).
- Session bookkeeping is single-threaded via `onGlobal`.
- Anything touching a player/inventory/block runs on the right region thread via FoliaLib.
- On shutdown, `ChestSessionManager.shutdown()` runs `persistOpenSessions()` (saves every still-open
  session) then `flushPendingSaves()` (blocks ≤30s for all writes); only then does `DbExecutor.shutdown()`
  close the pool, and storage closes last.
