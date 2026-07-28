# lang/

Per-viewer localization. Every player-facing string is a key resolved against **each recipient
client's own locale** at send time, not against one server-wide language.

| File | Responsibility |
|---|---|
| `LanguageManager` | Loads every locale, normalizes the strings, hands out `Component`s and chest titles |
| `EnhancedEchestTranslator` | The `MiniMessageTranslator` registered on Adventure's `GlobalTranslator` |

## How a string becomes text

1. `LanguageManager` loads **every** bundled locale (`BUNDLED_LOCALES`: `en_US`, `vi_VN`) plus any
   operator-added `language/<locale>/` folder from the data directory.
2. At load each string is normalized to **one MiniMessage string**: the format is auto-detected per
   string (contains `<` → MiniMessage, otherwise legacy `&` codes with `&#RRGGBB` hex), `{prefix}` is
   inlined, and `{placeholder}` becomes a `<placeholder>` argument tag.
3. `get()` / `getGui()` / `getChestTitle()` / `getChestLabel()` return a **locale-free**
   `Component.translatable(...)` with a key under `enhancedechest.msg.*` / `enhancedechest.gui.*`.
4. `EnhancedEchestTranslator` — registered **once** on the `GlobalTranslator` in
   `EnhancedEchestPlugin` enable and removed in disable — resolves that key against the recipient's
   locale. Fallback chain: exact → same language → the configured `language:` → `en_US`.

Gated by `language-auto-detect` (default on). Off ⇒ everyone sees the configured `language:`, i.e. the
legacy single-locale behaviour.

## Which surfaces auto-render (load-bearing)

Paper runs the `GlobalTranslator` per viewer for **chat** (`sendMessage`) and **inventory window
titles** (the `createInventory` title) only. Those keep the deferred `Component.translatable` and need
no `Locale`.

Paper does **NOT** render the **Dialog API** or inventory **item** names and lore. A raw translatable
there reaches the client as its literal key. For those, render **eagerly** with the viewer's locale via
the `get(Locale, …)` / `getGui(Locale, …)` / `getChestLabel(Locale, …)` overloads (which wrap
`GlobalTranslator.render`). Every `ChestDialogs` and `ChestListMenu` builder threads `player.locale()`
through; the detail/rename/icon dialogs take it from `DetailContext.locale()`. **Do not drop those
`Locale` arguments back to the deferred form** — that reintroduces the raw-key bug.

## Substitutions

Arguments are passed as `Argument.string` / `Argument.component` and inserted **literally, not
re-parsed** — a chest name can never inject formatting. A value that itself needs a click event or
formatting (the update-notification download link) is built with `getRich(...)` instead; note that a
placeholder inside a `<click:...>` attribute is **not** substituted per viewer.

## Files and keys

- `language/<locale>/messages.yml` — chat and action-bar strings (`enhancedechest.msg.*`).
- `language/<locale>/gui.yml` — dialog and menu labels (`enhancedechest.gui.*`), including
  `dialog.main-tag` (the gold `★` marking the main chest) and the docs URLs used by `DialogLinks`.
- Chest titles: a custom name is shown verbatim as plain text; otherwise chest #1 uses the un-numbered
  `enderchest.title` and chests 2+ use `enderchest.title-numbered` with `{index}`.

**Adding a key** means adding it to *every* bundled locale — a missing key falls back, but a missing
English key shows the raw key. **Adding a bundled locale** means a folder under
`src/main/resources/language/` plus an entry in `BUNDLED_LOCALES` (and, for icon-picker search,
a table under `icons/lang/` — see [../gui/CLAUDE.md](../gui/CLAUDE.md)).

**Renaming a key** goes through `ConfigMigrations` like a config key does, so existing installs with
edited language files upgrade cleanly — see [../config/CLAUDE.md](../config/CLAUDE.md).

## Gotchas

- The translator is a JVM-wide singleton source. `onDisable` removes it **first and unconditionally**,
  so a failure later in shutdown cannot leave a stale source behind to double-register on re-enable.
- `/ee reload` refreshes the contents of the existing translator; it never re-registers it.
- Parsed components are cached at load — don't parse MiniMessage on the hot path.
