# config/

`config.yml` in three shapes: the parsed snapshot the plugin runs on, the declarative schema the
in-game editor is generated from, and the key renames that let existing installs upgrade.

| File | Responsibility |
|---|---|
| `PluginConfig` | The parsed, live snapshot every service reads |
| `ConfigSchema` | **Single source of truth** for the in-game editor: one `Field` per editable key, grouped into `Section` pages |
| `ConfigEditor` | Validates and writes a page's values back to `config.yml` |
| `ConfigMigrations` | The rename tables for `config.yml`, `messages.yml`, `gui.yml` |
| `YamlMigrator` | Applies the renames and adds missing keys on load |

## `PluginConfig`

One object, built at enable and **mutated in place** by `reload()`, so every service that holds a
reference sees changes immediately — that is deliberate, and it is why the feature-toggle fields are
`volatile`. Values consumed on a hot path must be safe to read from any thread (telemetry metric
suppliers read it from SDK threads).

Two kinds of key:

- **Runtime-tunable** — re-applied by `EnhancedEchestPlugin.reload()` through explicit setters
  (`setDefaultSize`, `setTempConfig`, `reschedule`, `setEnabled`…). They only affect work started after
  the call, which is what makes a reload safe while saves are in flight.
- **Bound at startup** — the whole `database` connection block and all of `cross-server`. Rebuilding a
  connection pool or the Redis coordinator mid-save could drop connections and risk dupes, so a reload
  only **warns** when `databaseSignature()` changed.

`isValidSize` / `sanitizeSize` (multiple of 9, clamped 9–54) and `getTablePrefix()` (sanitized to
`[A-Za-z0-9_]` because it is concatenated into SQL) live here, not at the call sites.

## The in-game editor (`/ee config`)

`ConfigSchema` is the single source of truth: `ConfigEditor` validates and writes against it and
`ConfigDialogs` builds its forms generically from it. **A new editable setting is one schema entry plus
one `gui.yml` label in every bundled locale — no dialog code.**

A `Field` carries the config path, a `FieldType` (`BOOLEAN`, `INTEGER`, `TEXT`, `DURATION`, `ENUM`,
`STRING_LIST`), the `gui.yml` label key, bounds/step/options, `allowBlank`, and `restart`. Notes that
bite:

- **Nothing catches a typo for you.** A wrong path or a label key missing from a locale compiles fine.
  Check the path against `config.yml` and the label against every bundled `gui.yml` by hand.
  `ConfigSchemaCoverageTest` is the one automated guard — keep it passing.
- **Dialog input keys cannot be config paths**: the client only accepts `[A-Za-z0-9_]`, hence
  `Field.inputKey()`.
- Integers use a slider only up to `MAX_SLIDER_STEPS` (100) distinct stops; past that a text field is
  kinder.
- Writing goes through Bukkit's `FileConfiguration` (verified to preserve all comments) and is
  **all-or-nothing per page**. A successful save then calls the plugin's own `reload()` — which is why
  an edit from the menu needs no `/ee reload` afterwards.
- Keys bound at startup are flagged `needsRestart()` and only warn.
- Deliberately not exposed: anything outside `config.yml`, and any key the plugin sanitizes into a
  different shape than the admin typed.

## Renaming a key

`YamlMigrator` runs on load: it reads the old key's value, writes it under the new name, removes the
old key, and adds keys missing from the shipped defaults. **When you rename a config or language key,
add a `Rename` entry to the matching list in `ConfigMigrations`** (`CONFIG`, `MESSAGES`, `GUI`) —
without one, existing servers silently lose the setting. Order matters only when renames chain
(A→B then B→C); list them in the order they happened.

There is no `config_version` key and there should not be one — migration here is version-less.
