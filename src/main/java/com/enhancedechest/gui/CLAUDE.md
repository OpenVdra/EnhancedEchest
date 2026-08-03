# gui/

Everything the player looks at: the Paper Dialog API menus, the alternative inventory chooser, and the
holders that identify our inventories. The click/drag guards that police those inventories live in
[../listener/CLAUDE.md](../listener/CLAUDE.md).

## Files

| File | Responsibility |
|---|---|
| `dialog/ChestDialogs` | Every chest dialog: list, detail, rename, icon picker, admin picker, admin clear confirm |
| `dialog/IconCatalog` | The pickable icon set, sprite lookup, and locale-aware search |
| `dialog/DialogLinks` | The shared "open the documentation" button (book sprite, URL from `gui.yml`, never shown on screen) |
| `ChestListMenu` | The inventory-based chest chooser used when `enderchest.list-menu: inventory` |
| `EnderChestHolder` | `InventoryHolder` carrying owner, index, size, kind — how every other layer recognises our chest |
| `ChestListHolder` | `InventoryHolder` for the chooser menu |
| `EnderChestAnimator` | Ender chest block lid open/close animation (pure `Lidded` API) |

**All Dialog API use is confined to `dialog/`**, so a breaking change in Paper's experimental API stays
in one place. Don't build dialogs elsewhere.

## `DetailContext` — one detail dialog for owner and admin

`detailDialog(chest, DetailContext)` is a **single** dialog serving both the owner's `/eclist` flow and
an admin's `/ee view`. Don't split the admin path back out — it is intentionally the same code.

`DetailContext(owner, ownerName, self, canEdit, canSetMain, canClear, sourceBlock, locale)`:

- **`owner`** — every storage mutation targets this UUID, *not* the clicker. That is how an admin edits
  another player's chest. `openDetailDialog` builds a self-context; `openAdminDetail` an admin one.
- **`self`** — picks Open routing (`openChest` vs `adminOpen`) and Back routing (own list vs admin picker).
- **`canEdit`** — gates the appearance edits: always true for the owner, `enhancedechest.admin.edit` for
  an admin. Each edit is **also** gated by a global config toggle — Rename → `enderchest.features.rename`,
  Choose icon → `…features.icon`, Sort → `…features.sort` (read live from the shared `PluginConfig`,
  whose flags are `volatile`; `/ee reload` mutates it in place).
- **`canSetMain`** — owner-only (`enhancedechest.command.open`); always false for admins.
- **`canClear`** — `enhancedechest.admin.clear`; routes through `adminClearConfirmDialog`.
- **`locale`** — the *viewer's* client locale, not the owner's.

Temp chests show only Open / (Clear) / Back plus a static "expires in" snapshot — a live countdown is
impossible with the static Dialog API.

**Sort is a server action, not a `show_dialog`**: the button calls `ChestOpener.sortChest`, which
enforces the per-clicker cooldown and delegates to `ChestSpillService.sortChest`, then re-pushes the
detail dialog.

## Navigation and text

Forward transitions use a client-side `DialogAction.staticAction(ClickEvent.showDialog(child))` — child
dialogs are built first so parents can reference them — which avoids cursor recentering. Back, Cancel
and post-mutation paths re-query and re-push via `player.showDialog`.

Dialog label text lives in `gui.yml` under `dialog:`, never in `messages.yml`. **Every builder takes a
`Locale` and renders eagerly**: Paper does *not* run the `GlobalTranslator` for the Dialog API or for
inventory item names, so a deferred `Component.translatable` reaches the client as its raw key. See
[../lang/CLAUDE.md](../lang/CLAUDE.md). Item and block icons are Adventure sprite object components — no
resource pack needed.

### Edit-mode persistence

The edit-mode checkbox is a client-side `DialogInput.bool` that never notifies the server on toggle; its
value is only readable when a button carrying an action is clicked. So the preference is saved on **any**
action click that leaves the list (a chest button *or* Close), and only when it differs from the seeded
state. Fresh list opens seed it from `PlayerSettingsCache`. The detail-dialog Back path forces edit mode
on — that is navigation, not preference. One gap is unavoidable: closing with **Escape** fires no
callback, so a toggle followed by Escape does not persist.

## Icon picker names: two different problems

- **The label a player sees** is `Component.translatable(material)` (`Material` implements Adventure's
  `Translatable`). Because it is a real translatable component, **the client resolves it against its own
  language file** — every viewer sees the item name in their own language, for *every* Minecraft locale,
  with no server-side lookup.
- **Server-side search matching** (`IconCatalog.search(query, viewerLocale)`) is different: the server
  must decide which entries match *before* anything is sent, so it needs its own name table. That only
  works for locales with a bundled table.

Bundled: `icons/lang/en_us.json` and `icons/lang/vi_vn.json` — `translationKey → name` maps generated
from Mojang's client assets, filtered to `item.minecraft.*` / `block.minecraft.*` (~2.6k entries each).
`icons/valid-icon-sprites.txt` lists the materials with a single flat texture, i.e. what can be offered
as an icon at all. `search` normalizes `Player#locale()` to a Minecraft-style id (`vi_VN` → `vi_vn`) and
loads `icons/lang/<id>.json` lazily, caching the miss too. A locale with no table still shows correct
labels; only search falls back to English.

**Adding a locale's search table** is a resource drop, no code change: fetch the vanilla
`assets/minecraft/lang/<locale>.json` for a version this plugin targets (via the Mojang version manifest
→ asset index; `en_us.json` is the exception, it ships inside the client jar), filter it to the
`item.minecraft.` / `block.minecraft.` keys, and save it as
`src/main/resources/icons/lang/<locale>.json` in lowercase.

**Server owners can override without a rebuild**: `IconCatalog.setExternalLangDir` points at
`plugins/EnhancedEchest/icons/lang/`, which is checked **before** the classpath resource. A malformed
external file is logged and falls through to the bundled table rather than throwing — that input comes
from an operator, not the build. The cache has no TTL, so on-disk changes need
`IconCatalog.reloadLocaleNames()`, wired into `/ee reload`. Keep the user-facing instructions on the docs
site's Language page in sync with the file format.

## `ChestListMenu` (inventory mode)

`enderchest.list-menu: inventory` swaps the list **dialog** for a plain chest chooser: click an icon to
open that chest, nothing else (no rename, main, icon or sort — those stay dialog-only). Layout is a
one-cell padding border on every side, so the menu grows a row at a time and the icons stay centred: up
to 7 chests → 27 slots, 14 → 36, 21 → 45, 28 → 54. `MAX_CHESTS` is 28; **callers must gate on it** and
fall back to the dialog for anyone with more, because `build` silently drops the overflow.

## Gotchas

- `EnderChestHolder` identity (`inv.getHolder() instanceof EnderChestHolder`) is how the listeners, the
  session manager and the open-dedupe logic recognise our inventories. Anything that creates a chest
  inventory must attach it.
- The lid animation is driven from the open/detach paths using the **per-viewer** source block and must
  be dispatched to the block's region thread.
- `ChestDialogs` holds a live `PluginConfig` reference on purpose — feature toggles must reflect
  `/ee reload` without rebuilding the dialogs.
