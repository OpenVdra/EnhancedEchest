# Expiry & temporary chests

Chests can expire, and items that no longer fit anywhere spill into **temporary chests** instead of
being lost silently.

- **Temp chest (`kind = TEMP`)** — an overflow holder created automatically when items are cut off by a
  shrink, a non-`force` delete, or a normal chest expiring with items inside. It always carries an
  `expires_at` (config `temp-enderchest.expiry`, default `7d`), is never primary, and cannot be renamed
  or set-as-main in the dialog (Open + Back only). It is **take-only** (deposits are cancelled for
  everyone, including admins — see [ui-dialogs.md](ui-dialogs.md) / `EnderChestGuiListener`). It
  **auto-deletes the moment it is emptied** (`persist` deletes the row instead of writing an empty temp),
  and on expiry it is hard-deleted with any remaining items **permanently lost**.
- **Expiring normal chest** — `/ee add <player> <size> <duration>` grants a `kind = NORMAL` chest with an
  `expires_at`. On expiry its items spill into a temp chest, then the chest is removed.

## Sweeper (`expiry/ExpirySweeper`)

An async repeating timer (via `Scheduler`) at `temp-enderchest.check-interval` (default `5m`). Each tick runs
`findExpired(now)` — a DB-side candidate query (the only way to see offline, non-resident owners'
expired chests) whose hits are loaded into the cache and re-verified against the authoritative
in-memory rows, plus the scan of already-resident owners — and routes each
hit through the service — NORMAL → `removeChest(..., force=false)` (spill), TEMP →
`removeChest(..., force=true)` (discard). PERM chests carry no `expires_at`, so they never appear in
`findExpired` and are never swept (they are managed by the permission reconcile instead). Expiry is
deliberately **swept, not lazy on access**, so the hot
open/close path stays free of expiry filtering and the dangerous mutation is centralised in one
serialized place.

## Dupe-safety for item-moving ops

Every item-moving op — shrink spill, delete spill, normal-chest expiry spill, temp auto-delete, temp
expiry — goes through `ChestSpillService` (which delegates the force-close + exclusive run to
`ChestSessionManager`) and reuses the model in
[concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md):

1. `forceCloseAll(owner, index)` closes the GUI of **every** viewer of the affected chest, then persists
   the shared inventory and registers the save in `pendingSaves` before the op proceeds.
2. `runExclusive(owner, index, dbWork)` chains the work behind that pending save (and any other op for
   the key) and registers its own marker, so a concurrent `open` waits for it.
3. The actual row changes happen atomically under the `CachedStorage` lock:
   - `spillShrink`: update the original to the new size/contents + insert a temp row holding the overflow.
   - `spillRemove`: insert the temp row + remove the original (**no** primary promotion — the main is an
     explicit player choice).
   The temp index is `max(chest_index)+1` computed under that same lock, so items never exist in
   two rows visible to any outside reader.

The spill ops themselves run entirely on the DB executor inside `runExclusive` — decode, split,
re-encode and the store mutation. The session save-side encode that precedes them (step 1's `forceCloseAll`
persist) stays synchronous on the global thread, per the dupe-safety contract
([concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md)).

## `DurationFormat` (`util/`)

Parses time strings (`20s`, `5m`, `1h`, `1d_2h_30m`; units `s m h d w mo y`, with `mo = 30d` and
`y = 365d`) and formats the static "expires in" snapshot shown on dialog buttons (a live ticking
countdown is impossible with the static Dialog API).
