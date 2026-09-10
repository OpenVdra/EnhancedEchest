# Activity Log

EnhancedEchest can record every time an ender chest is opened and closed, keeping a snapshot of what the chest held each time. The record is viewed in game with `/ee log <player>`.

This is evidence for investigating a theft. It does **not** restore items. For that, see [Backups](/docs/configuration/#backup).

It is off by default. Turn it on with the `enabled` setting under `activity-log` in `config.yml`, then run `/ee reload`.

The log is kept in its own file, `plugins/EnhancedEchest/log.db`, separate from the main chest data. It is a database, not a text file, so it is read through the in-game viewer rather than opened in an editor.

## Viewing a Player's Log

Run `/ee log <player>` to open the viewer for that player's ender chests. It needs the `enhancedechest.admin.log` permission.

Each visit is one pane, an ender chest, newest first:

- The pane name is who opened the chest and when they closed it.
- The lore shows how long it was open, the chest number, and what changed while it was open: a `+` line for each item added to the chest, a `-` line for each item taken out, together on the one tooltip.
- The bottom row pages through older and newer visits.

## Viewing a Chest at a Moment in Time

Click any pane to see the exact contents the chest held when that visit ended. Items can be picked up and moved around inside this preview to inspect them, but nothing can be taken out of it, and closing it changes nothing. The stored record is never altered. Pressing Esc or E returns to the same page of the log.

## Someone Opening Another Player's Chest

When an admin opens a chest that is not theirs, the entry records the admin as the person who did it, so admin access shows up in the owner's log alongside the owner's own visits.

## Visits That Changed Nothing

Most people open their chest, look at it, and close it again. Visits that took or added nothing are not recorded at all, so the log only ever holds visits where something moved. A chest whose items were only rearranged counts as unchanged too: nothing was gained or lost.

## How Much Is Kept

Old entries are cleaned up automatically so the log cannot grow forever:

- Entries older than `retention-days` are deleted.
- Each player keeps at most `max-entries-per-player` entries, so one very active player, or a bot, cannot fill the log.
- `prune-interval` sets how often this cleanup runs.

::: tip Changing settings
`enabled`, `retention-days`, `max-entries-per-player` and `prune-interval` apply on `/ee reload`. `queue-capacity` is read once when the server starts, so changing it needs a full restart.
:::
