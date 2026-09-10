# config/

`config.yml` in two shapes: the parsed snapshot the plugin runs on, and the key renames that let
existing installs upgrade.

| File | Responsibility |
|---|---|
| `PluginConfig` | The parsed, live snapshot every service reads |
| `ConfigMigrations` | The rename tables for `config.yml`, `messages.yml`, `gui.yml` |
| `YamlMigrator` | Applies the renames and adds missing keys on load |

`config.yml` is edited on disk and applied with `/ee reload`. There is no in-game editor — the
schema-driven `/ee config` dialog was removed in 1.2.0; don't reintroduce it without asking.

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

## Renaming a key

`YamlMigrator` runs on load: it reads the old key's value, writes it under the new name, removes the
old key, and adds keys missing from the shipped defaults. **When you rename a config or language key,
add a `Rename` entry to the matching list in `ConfigMigrations`** (`CONFIG`, `MESSAGES`, `GUI`) —
without one, existing servers silently lose the setting. Order matters only when renames chain
(A→B then B→C); list them in the order they happened.

There is no `config_version` key and there should not be one — migration here is version-less.
