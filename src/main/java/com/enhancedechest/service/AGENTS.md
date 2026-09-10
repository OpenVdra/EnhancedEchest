# service/

Everything between a player action and the storage layer: what to open, the live shared inventories,
every item-moving operation, and the async dispatch. **This is where the dupe-safety model lives** —
if you change one thing here, change it knowing why the current shape exists.

## Files

| File | Responsibility |
|---|---|
| `ChestSessionManager` | Dupe-safety core: the shared live-inventory registry, attach/detach, `runExclusive`, `forceCloseAll`. One closed class — keep it that way |
| `ChestOpener` | Decides *what* to open (`/ec`, right-click, `/eclist`, `/ee view`) and drives the dialogs |
| `ChestSpillService` | Item-moving ops: resize/delete spill, expiry disposal, clear, sort, temp auto-reclaim |
| `ChestTransferService` | `/ee transfer`: move a player's NORMAL chests onto another account |
| `PermissionChestService` | Grants/resizes/revokes `kind = PERM` chests from permissions; reconcile-on-open |
| `TempReclaimNotifier` | Tells the owner which chest an auto-reclaim moved their temp-chest items into |
| `ChestActivityLogger` | Bounded, batched audit pipeline: who opened what, what they added or took |
| `StorageGateway` | Thin async wrappers over `EnderChestStorage` (list/create/rename/icon/primary/name lookup) |
| `PlayerSettingsCache` | Write-through per-player settings cache, bounded by online players |
| `PlayerNameIndex` | In-memory name → UUID index for command suggestions and offline lookups |
| `DbExecutor` | The one shared daemon pool (`EnhancedEchest-db`) every storage call is dispatched onto |

## The dupe-safety model

A chest's contents exist in exactly one place at a time, enforced on two levels:

- **In memory** — every open chest is backed by **one shared `Inventory`** (the *session*), so two
  concurrent viewers (owner + admin) mutate the same `ItemStack[]` and Bukkit serialises their moves.
- **At the store** — a chest is loaded fresh on its first open and written back when its **last**
  viewer closes; a reopen waits for any in-flight save of that same chest (`pendingSaves` / `waitPending`).

`sessions` is a `ConcurrentHashMap<SaveKey(owner,index), Session>`; a `Session` holds the shared `inv`,
its `viewers`, the per-viewer `viewerBlocks` (for the lid animation), the `waiting` queue of opens
raised before the first load finished, and the `ready`/`closing` flags.

**Every session mutation runs on one bookkeeping thread** via `onGlobal(Runnable)` — the main thread on
Paper, the global region thread on Folia. That removes registry-level races on both platforms. The DB
read **and** the byte→ItemStack decode both run on the DB executor; the global thread only builds the
`Inventory`. The **encode on save is synchronous on the global thread** and that is the load-bearing
half: it only ever happens after every viewer closed, so it never races a live edit. Do not move it.

### `ChestSessionManager.open` is the single funnel

`ChestOpener.open` / `openChest` / `adminOpen` and every dialog button all end up here. **If you add a
new way to open a chest, route it through this method** — a second independently loaded `Inventory`
reintroduces duping.

1. On the player's entity thread: a request for the chest they are **already viewing** is dropped (stale
   duplicate); a *different* open chest GUI is closed first (its close fires `detach`). Then hop to
   `onGlobal` → `decideOpen`.
2. `decideOpen` on the global thread: a live, non-closing session → attach (queue in `waiting` if it is
   not `ready` yet); no session → create one, `waitPending(owner, index)`, then async load+decode →
   `finishCreate`.
3. `finishCreate` builds the shared inventory, marks it `ready`, flushes `waiting`. A decode failure
   arrives as the future's error and aborts the open (`chest.load-failed`, row untouched).
4. `addViewerAndOpen` registers the viewer on the global thread, then calls `openInventory` on the
   player's entity thread and plays the lid animation if a source block was supplied.

### The spurious-close hazard (three guards, all load-bearing)

Opening an inventory while another is open makes Bukkit close the current view first, firing an
`InventoryCloseEvent`. If that close is for the *same* shared inventory being re-shown, `detach` would
tear the session down while the player's GUI stays open on an orphaned `Inventory` — a dupe on the next
fresh load. On Folia an open spans many thread hops, so this is easy to hit:

1. `VanillaEnderChestListener` handles only `EquipmentSlot.HAND` (the interact event fires per hand).
2. `decideOpen` keeps **one `Pending` per player** in `waiting`, so `finishCreate` never double-opens.
3. `addViewerAndOpen` skips `openInventory` when the player already views that exact inventory, and
   re-verifies afterwards that the session is still live and the viewer still registered.

Both entry points also **drop stale open requests instead of cycling the GUI** (a player cannot click a
block or type a command while a chest GUI is open, so such a request is queued spam) — otherwise every
spammed right-click ran a full close → save → load → reopen cycle and replayed the lid sounds.

### Closing, force-closing, serialization

- `detach(player, holder)` — remove the viewer on the global thread, play the close animation, and if
  this was the **last** viewer, remove the session and `persist` it. A non-last viewer closing does
  **not** save; the remaining viewers keep editing the one live inventory.
- `persist(session)` — encode synchronously on the global thread, write on the executor, register the
  future in `pendingSaves`. An emptied TEMP chest deletes its row instead. Encode failures abort the
  write rather than corrupting data.
- `forceCloseAll(owner, index)` — set `closing`, close every viewer's screen on their own entity
  thread, then persist the now-quiescent inventory. The returned future completes once the save is in
  `pendingSaves`, so callers can chain `runExclusive` behind it. Every item-moving op starts here.
- `runExclusive(owner, index, dbWork)` / `runExclusiveAcross(refs, dbWork)` — chain arbitrary DB work
  behind the pending future(s) and register their own marker, so a concurrent open waits for it.

### Paper vs Folia

Paper runs all viewers' inventory events on the main thread, so **owner and admin can edit the same
chest at once**. On Folia viewers may live on different region threads where a shared `ItemStack[]` is
unsafe, so only **one live viewer per chest** is allowed; a second opener is denied with `chest.in-use`.
This is the only genuine platform branch in the plugin (`Scheduler.isFolia()` in `decideOpen`).

## Open routing (`ChestOpener.open`)

Every self-open path shares the `reconcileForOpen` prelude: on the entity thread it resolves the
player's permission targets, then `listChestsAsync().thenCompose(reconcile)` so the list it routes on is
already in sync (common case: nothing changed, no extra query), and lazily writes the username into the
`players` row when it differs from the one already loaded. Then:

- **0–1 real chest, no temp chest** → open it directly (bootstrapping chest #1 via `createChest` if the
  player has none). The index comes from the list already in hand — no `getPrimaryIndex` query.
- **2+ chests, an explicit main flagged, caller has `enhancedechest.command.open`** → open the main.
- **2+ chests otherwise** (no main, or no permission), or **any TEMP chest present** → the `/eclist`
  management UI (dialog or inventory menu, per `enderchest.list-menu`).

"Real chest" counts `kind != TEMP`, i.e. both NORMAL and PERM. A PERM chest can itself be the flagged
main. The main is **never** auto-assigned. `sortChest` here enforces the per-clicker
`enderchest.features.sort-cooldown` before delegating to `ChestSpillService`.

## Item-moving ops and temp chests

Items that no longer fit spill into a **temp chest** (`kind = TEMP`) rather than being lost: an overflow
holder with an `expires_at` (`temp-enderchest.expiry`, default `7d`), never primary, not renameable,
**take-only for everyone including admins**, auto-deleted the moment it is emptied, and hard-deleted
with its remaining items on expiry.

Every op — shrink spill, delete spill, expiry spill, clear, sort, reclaim — follows the same shape:
`forceCloseAll` → `runExclusive` → the row change under the `CachedStorage` lock, with the decode /
split / re-encode running on the DB executor inside `runExclusive`. `spillShrink` updates the original
and inserts the temp row; `spillRemove` inserts the temp row and removes the original (**no** primary
promotion). The temp index is `max(chest_index)+1` computed under that same lock, so items are never
visible in two rows at once.

`reclaimTempInto(owner, targetIndex)` is the reverse: it moves **one** temp chest into a newly created,
still-empty chest and deletes the temp row. Called right after a chest is granted — every fresh PERM
chest, and `/ee add` **without** a duration (an expiring grant is a loan, not a home). Three rules, do
not relax them: only a temp chest whose size ≤ the target's is eligible and the bytes are copied
**verbatim** (no decode/merge/repack, every item keeps its slot); one temp chest per new chest, the one
expiring soonest first, ties on lowest index; `CachedStorage.reclaimTemp` re-checks every precondition
under the cache lock and returns `false` rather than doing a partial move.

A move that returns `true` is announced to the owner by `TempReclaimNotifier`
(`temp-enderchest.reclaim-notify`, default on), chat + action bar + sound like the join reminder. The
hook sits on the tail of `reclaimTempInto`, **not** at its call sites, so a new way of granting a chest
cannot forget it; the notifier never throws, because it runs on the reclaim future and the move is
already durable. An offline owner is skipped rather than queued.

Expiry itself is **swept, not lazy on access** (`expiry/ExpirySweeper`, `temp-enderchest.check-interval`,
default `5m`), so the hot open/close path stays free of expiry filtering and the dangerous mutation is
centralised in one serialized place. NORMAL expiry spills, TEMP expiry discards. PERM chests carry no
`expires_at` and are never swept — permissions manage them.

## Permission-granted chests

`enhancedechest.additional_amount.<count>.slot.<size>` grants chests (stacking, summed per size).
**Always on** — the `permission-chests.enabled` toggle was removed in 1.2.0 and the branches it guarded
went with it. Don't reintroduce a config gate here: permissions are the only input.

- `resolveTargets(player)` runs on the entity thread (it reads `getEffectivePermissions()`) and resolves
  **both** permission-derived targets — the PERM target `Map<size, count>` and the `default_size`
  override for the base chest — in a **single pass**. Keep it one pass: this sits on the per-open hot
  path and every `getEffectivePermissions()` call builds a fresh snapshot.
- `reconcile(owner, desired, chests)` diffs against the current PERM chests. Fast path: base chest
  present and the multiset already matches → no writes, returns the list it was given. Otherwise it
  bootstraps the inviolable base NORMAL chest first, keeps PERM chests already at a wanted size, resizes
  surplus in place (preserving items/name/icon via `resizeOrSpill`), creates missing sizes, and removes
  true surplus highest-index-first with items spilled to a temp chest. A per-owner guard skips a
  concurrent reconcile.

To a player a PERM chest is indistinguishable from a NORMAL one; only admin commands skip it. The base
chest is never created, deleted or overridden by permissions. No schema change — PERM is `kind = 2`.

## `/ee transfer`

`ChestTransferService` **moves** (never copies) a player's NORMAL chests onto another account; TEMP and
PERM are excluded (PERM is re-granted by the destination's own permissions). `all` replaces the
destination's NORMAL chests with the source's at the same indices; `#index`/name moves one. If the
destination already holds items where the transfer would land, the command aborts unless `override`
(discard) or `temp` (spill to a recoverable temp chest) is given — "has items" is decided by decoding,
not by a non-null blob. Both players' chests are force-closed first and the whole row swap runs as one
transaction inside `runExclusiveAcross`.

## Activity log

`ChestActivityLogger` writes `logs/echest-latest.log` under the plugin folder (`activity-log.enabled`,
default off). The Bukkit-thread side captures each occupied slot into immutable strings and numbers
**once**; no `ItemStack`, `ItemMeta`, registry lookup or component serializer crosses the thread
boundary. Diffing, rendering, rotation and all file I/O happen on one dedicated worker behind a bounded
queue, so a stalled disk can never grow the heap. Item identity is `Material` + `ItemMeta.hashCode()`,
interned in a shared cache — it only ever has to be stable **within one OPEN/CLOSE cycle**, which is
what makes the capture cheap enough for a region thread.

A shulker box's contents are **rendered, not accounted** (`activity-log.shulker-contents`, default on).
The list is built in `buildMetaId`, i.e. only on a `META_CACHE` miss, so a given shulker is unpacked
once and every later capture of it is a cache hit — that placement is the whole point. Folding the
inner items into `Snapshot.totals` instead would unpack every container on **every** capture (up to 27
items per occupied slot, on a region thread) and is what was deliberately not built. Expansion is one
level deep and inner identities are never interned; both rules exist so a crafted CONTAINER component
cannot turn one capture into an unbounded walk or evict the shared cache.

`activity-log.chest-contents` (default **on**) adds a `HAVE` line under each header listing what the
chest held at that moment. It is pure formatting: both snapshots are captured and queued either way, so
the flag costs nothing on a Bukkit thread and only multiplies bytes on disk (roughly 3x, or far more
once shulker contents are rendered into it). `HAVE` is **unsorted on purpose** — `capture` fills a
`LinkedHashMap` in slot order and `merge` does not reorder an existing key, so iterating the totals
reproduces the chest's own order. ADD/TAKE stay alphabetical; the two lines answer different questions.

`ChestSessionManager` drives it: `opened(...)` when the first viewer attaches, `closed(...)` on detach,
`abandon(...)` when `needsCapture(s.touched)` says nobody touched the chest. The `touched` flag and
`isRecording()` exist so a disabled log costs nothing on the hot path — keep those checks in front of
any new capture site.

## Gotchas

- Storage methods are synchronous. **This package is the only one allowed to dispatch them onto the
  `DbExecutor`.** Never call storage from a region/main thread, and never introduce a second pool.
- `PlayerSettingsCache` is write-through and **bounded by the online-player count**: entries are added
  only by the join preload and removed by the quit eviction (with a post-load online re-check closing
  the join-then-immediate-quit race). A cache miss falls back to a one-off read that is *not* cached.
- `PlayerNameIndex` is loaded once at startup (`loadAll` from the `players` table) and kept fresh by
  `markSeenAsync` — called on join and quit by `PlayerSettingsListener`, and by `ChestOpener`'s open
  prelude when the row it already loaded holds a stale name. Command suggestions read it and **nothing
  else**: never `getOfflinePlayer(String)` (blocking web lookup) and **never `getOfflinePlayers()`**,
  which reads a `.dat` file per uncached name. That scan is banned outright, at startup as much as per
  keystroke — it was both a TPS collapse and an OOM-kill risk. The database is the only name source.
- The index carries each player's `last_online`, and `prefixMatches` hides anyone last seen longer ago
  than `commands.suggest-offline-within` (default `30d`, `all` disables the filter, runtime-tunable via
  `setSuggestWindowMillis`). **`findUuid` is deliberately unfiltered** — the window decides who is
  *offered* while typing, never who can be *reached*; a fully typed name must always resolve.
- Runtime-tunable settings arrive through setters called from `EnhancedEchestPlugin.reload()`
  (`setDefaultSize`, `setTempConfig`, `setConfig`, `setEnabled`…). They must only affect work started
  after the call, which is what makes reloading safe while saves are in flight.
