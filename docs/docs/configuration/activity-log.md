# Activity Log

EnhancedEchest can record every time an ender chest is opened and closed, keeping a snapshot of what the chest held each time. The record is viewed in game with `/ee log <player>`.

This is evidence for investigating a theft. It does **not** restore items. For that, see [Backups](/docs/configuration/#backup).

It is off by default. Turn it on with the `enabled` setting under `activity-log` in `config.yml`, then run `/ee reload`.

The log is kept in its own file, `plugins/EnhancedEchest/log.db`, separate from the main chest data. It is a database, not a text file, so it is read through the in-game viewer rather than opened in an editor.

## Viewing a Player's Log

Run `/ee log <player>` to open the viewer for that player's ender chests. It needs the `enhancedechest.admin.log` permission.

Each open and close is one pane, newest first:

- A **green** pane is an open. A **red** pane is a close.
- The pane name is the action and the time it happened.
- The lore names who did it and the chest number, and on a close it lists what changed: a green `+` line for each item added to the chest, a red `-` line for each item taken out.
- The bottom row pages through older and newer entries.

## Viewing a Chest at a Moment in Time

Click any pane to see the exact contents the chest held at that moment. Items can be picked up and moved around inside this preview to inspect them, but nothing can be taken out of it, and closing it changes nothing. The stored record is never altered.

## Someone Opening Another Player's Chest

When an admin opens a chest that is not theirs, the entry records the admin as the person who did it, so admin access shows up in the owner's log alongside the owner's own visits.

## Visits That Changed Nothing

Most people open their chest, look at it, and close it again. For those visits only the open is kept, so the log stays short enough to read. A chest whose items were only moved around counts as unchanged too: nothing was gained or lost.

To also keep a close entry for visits that changed nothing, set `log-unchanged` to `true` under `activity-log` in `config.yml`.

## How Much Is Kept

Old entries are cleaned up automatically so the log cannot grow forever:

- Entries older than `retention-days` are deleted.
- Each player keeps at most `max-entries-per-player` entries, so one very active player, or a bot, cannot fill the log.
- `prune-interval` sets how often this cleanup runs.

::: tip Changing settings
`enabled`, `log-unchanged`, `retention-days`, `max-entries-per-player` and `prune-interval` apply on `/ee reload`. `queue-capacity` is read once when the server starts, so changing it needs a full restart.
:::
