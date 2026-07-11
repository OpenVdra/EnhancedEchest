# Multi-chest UI & GUI guards

## Dialog API (`gui/dialog/ChestDialogs`)

Isolates Paper's experimental Dialog API so a breaking change is contained to one file. Dialogs:

- **list** — one button per owned chest → opens the detail dialog. Marks the main with a gold `★`
  (`gui.yml dialog.main-tag`). Carries the edit-mode checkbox (see below).
- **detail** (`detailDialog(chest, DetailContext)`) — Open / Set-as-main / Rename / Choose icon / Sort /
  (admin) Clear / Back. A **single** dialog shared by the owner and an admin (`/ee view`); the
  `DetailContext` record decides the button set and *which owner* every mutation targets. Temp chests show
  only Open / (Clear) / Back, plus an "expires in" snapshot.
- **rename** (`renameDialog(chest, ctx)`) — text input + Save / Cancel (separate dialog, no inline input).
- The icon picker (`iconPickerDialog(chest, ctx, filter)`) is a single scrollable multi-action grid with a
  search input.
- **admin view list** (`adminViewListDialog`) — one button per chest for `/ee view <player> [list]`,
  opening the per-chest **detail** dialog for the *target* via `openAdminDetail`. Plain Close exit button;
  title shows the owner's name (`gui.yml dialog.admin-list-title`).
- **admin clear confirm** (`adminClearConfirmDialog`) — yes/no gate before `ChestSpillService.clearChest`.

### `DetailContext` (owner vs. admin, feature gating)

`ChestDialogs.DetailContext(owner, ownerName, self, canEdit, canSetMain, canClear, sourceBlock, locale)`
collapses the old separate owner/admin detail dialogs into one path:

- **`owner`** — every storage mutation (rename, icon, sort, set-main) targets this UUID, *not* the clicker.
  That is how an admin edits another player's chest. `self`/`openDetailDialog(player,int)` builds a
  self-context (`owner = player`); `openAdminDetail` builds an admin-context (`owner = target`).
- **`self`** — picks Open routing (`openChest` vs. `adminOpen`) and Back routing (own list vs.
  `openAdminViewList`).
- **`canEdit`** — gates the appearance edits. `true` for the owner; for an admin it is
  `hasPermission(enhancedechest.admin.edit)`. Each edit is **also** gated by a global config toggle:
  Rename → `enderchest.features.rename`, Choose icon → `…features.icon`, Sort → `…features.sort` (read live
  from `PluginConfig`, which `ChestDialogs` holds a reference to — `/ee reload` mutates it in place; the
  three flags are `volatile`).
- **`canSetMain`** — owner-only (the open permission); always `false` for admins.
- **`canClear`** — admin Clear button, `hasPermission(enhancedechest.admin.clear)`; routes through the
  confirm dialog.
- **`locale`** — the *viewer's* client locale (`Player#locale()`), not the owner's; threaded into
  `iconPickerDialog` so `IconCatalog.search` can match localized item names — see below.

**Sort** is a server action, not a `show_dialog`: the button calls `ChestOpener.sortChest(viewer, ctx,
index)`, which enforces a per-clicker cooldown (`enderchest.features.sort-cooldown`, rejected with
`chest.sort-cooldown`) then calls `ChestSpillService.sortChest(owner, index)` — force-close all viewers +
`runExclusive` load→merge-similar→reorder-by-material-key→save (dupe-safe, mirrors `clearChest`). On
success it sends `chest.sorted` and re-pushes the detail dialog.

Navigation avoids cursor recentering: forward transitions use a client-side
`DialogAction.staticAction(ClickEvent.showDialog(child))` (child dialogs built first so parents can
reference them), while Back / Cancel / post-mutation paths re-query the DB and re-push via
`player.showDialog`. Dialog label text lives in `gui.yml` under `dialog:` (not `messages.yml`).

Item/block icons are rendered as Adventure sprite object components (no resource pack required).

### Icon picker names: client-locale label vs. server-side search (`gui/dialog/IconCatalog`)

Two different problems, two different mechanisms:

- **Label shown to the player** — `IconCatalog.Entry#name()` is `Component.translatable(material)`
  (`Material` implements Paper's `net.kyori.adventure.translation.Translatable`; `Component.translatable`
  has an overload that reads the key straight off it). Because it's a real translatable component and
  not plain text, **the client resolves it against its own installed language file when rendering** — so
  every viewer sees the item name in their own client's language, automatically, no server-side locale
  lookup needed. This part works for *every* Minecraft client locale, unconditionally.
- **Server-side search matching** (`IconCatalog.search(query, viewerLocale)`) is a different problem: the
  server has to decide *which entries match* before anything is sent to the client, so it needs its own
  copy of "what does this translation key say in language X" — the client-side resolution above doesn't
  help here. This only works for locales the plugin bundles a name table for.

**What's bundled today:** `icons/lang/en_us.json` and `icons/lang/vi_vn.json` (matching this plugin's two
supported message locales, `language/en_US` / `language/vi_VN`) — each a `translationKey -> name` map,
generated once from Mojang's own client assets and filtered down to just `item.minecraft.*` /
`block.minecraft.*` keys (~2.6–2.7k entries, ~150–180 KB each). `IconCatalog.search` normalizes the
viewer's `Player#locale()` to a lowercase Minecraft-style id (`Locale#toString()`, e.g. `vi_VN` →
`vi_vn`) and looks up `icons/lang/<that id>.json` as a classpath resource, cached forever (including a
cached "no table" miss) after first lookup. **A client locale with no bundled table just doesn't get
localized search** — the label still renders correctly (see above), only search silently falls back to
matching the English name.

**Runbook — adding another locale's search table** (no code change, just drop in a resource file):

1. Get the vanilla `assets/minecraft/lang/<locale>.json` for a Minecraft version this plugin targets
   (1.21.11+):
   - Fetch `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`, find the version's `url`,
     fetch that for `assetIndex.url`, fetch that for the asset index, and look up
     `minecraft/lang/<locale>.json` in its `objects` map to get a `hash`. Download from
     `https://resources.download.minecraft.net/<hash[0:2]>/<hash>`.
   - **Exception: `en_us.json`** is not a separate asset object — it ships baked into the client jar
     itself (`downloads.client.url` in the version json) at `assets/minecraft/lang/en_us.json`, since
     it's the always-present source language. Unzip it out of the jar instead.
2. Filter the raw file down to just the keys used by the icon catalog — keep entries whose key starts
   with `item.minecraft.` or `block.minecraft.`, drop everything else (gui/advancements/subtitles/etc. —
   several thousand unrelated keys). This is what keeps the bundled file to ~150–200 KB instead of the
   ~500 KB–1 MB raw file.
3. Save the filtered JSON as `src/main/resources/icons/lang/<locale>.json`, lowercase, matching the
   Minecraft locale id (e.g. `de_de.json`, `zh_cn.json`). That's it — `IconCatalog` loads it lazily by
   locale id, nothing else references the file list.
4. Regenerate on Minecraft version bumps only if you want newly-added items' names covered immediately;
   stale entries for renamed/removed items are harmless (they just never match), and new items simply
   fall back to English search until regenerated — same non-fatal-staleness tradeoff as
   `icons/valid-icon-sprites.txt` above.

## Edit-mode persistence flow

The edit-mode checkbox is a client-side `DialogInput.bool` that never notifies the server on toggle — its
value is only readable when a button carrying an action is clicked. So the preference is saved on **any**
action click that leaves the list (a chest button *or* Close), and only when it differs from the seeded
state, to avoid needless writes. Fresh list opens (`/eclist` and the routing dialog in `open`) seed the
checkbox from the saved value (`settingsCache`, see [storage-and-schema.md](storage-and-schema.md)). The
detail-dialog Back path forces edit-mode on — that is navigation, not preference. One gap is
unavoidable: closing with **Escape** fires no callback in the Dialog API, so a toggle followed by Escape
does not persist.

## Inventory GUI guards (`listener/EnderChestGuiListener`)

The custom chest inventory is identified by `inv.getHolder() instanceof EnderChestHolder`. The listener
enforces two rules on `InventoryClickEvent` / `InventoryDragEvent`:

1. **Read-only viewers** — a non-owner without `enhancedechest.admin.edit` (i.e. an admin who opened via
   `/ee view` with only `admin.view`). Any action that would change the shared top inventory is cancelled
   (`chest.view-only` on the action bar); they still see live updates. See
   [commands-and-permissions.md](commands-and-permissions.md).
2. **Temp chests are take-only** for everyone — deposits into the top inventory are cancelled
   (`chest.temp-take-only`, with a throttled deny sound), take-outs left untouched.

On close (and as a quit backstop in `PlayerQuitListener`), the listener calls `service.detach(...)`,
which removes the viewer from the shared session and, on the last viewer, persists — see
[concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md). The lid open/close animation
(`EnderChestAnimator`, pure `Lidded` API) is driven from the open/detach paths using the per-viewer
source block, dispatched to the block's region thread.
