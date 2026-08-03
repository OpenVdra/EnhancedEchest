# Activity Log

EnhancedEchest records who opened which ender chest and what they put in or took out. It writes a plain text file you can open in any editor, at `plugins/EnhancedEchest/logs/echest-latest.log`.

This is evidence for investigating a theft. It does **not** restore items. For that, see [Backups](/docs/configuration/#backup).

It is off by default. Turn it on with the `enabled` setting under `activity-log` in `config.yml`, then run `/ee reload`.

## Reading an Entry

Every visit that changed something produces one entry: when the chest was opened, what was added and taken while it was open, and when it was closed.

```
[2026-07-28 15:10:23.913 ICT] OPEN player=Steve uuid=925c51aa-... chest=2 size=54
  ADD   minecraft:redstone x24
  TAKE  minecraft:stone x32
[2026-07-28 15:12:41.002 ICT] CLOSE player=Steve uuid=925c51aa-... chest=2 size=54
```

- `ADD` lists everything put in during that visit, `TAKE` everything taken out. Identical items are totalled, so ten stacks of stone taken from five slots read as one line.
- Items with a name, enchantments or other custom data are spelled out in full on their own line.
- `chest=2` is the player's chest number, the same one they see in `/eclist`. `size=54` is how many slots it has.
- The chest layout is not recorded. The log tells you what moved, not which slot it sat in.

### Shulker Boxes

A shulker box counts as one item, but what it held is listed after it, so items carried in and out inside a shulker are still visible.

```
[2026-08-03 09:41:12.507 ICT] OPEN player=Steve uuid=925c51aa-... chest=1 size=27
  TAKE  minecraft:shulker_box{meta=8f31c2,contents=[minecraft:diamond x192, minecraft:netherite_ingot x7]} x1
[2026-08-03 09:41:58.140 ICT] CLOSE player=Steve uuid=925c51aa-... chest=1 size=27
```

Items inside are totalled the same way, so three stacks of diamonds read as one entry. Only the first level is listed: an item packed inside is shown by its own name, never by what it might contain in turn.

Repacking a shulker while it sits in the chest changes it, so the log records the old one being taken out and the new one put in. Comparing the two `contents` lists shows what moved.

To keep shulker boxes as a single unnamed item instead, set `shulker-contents` to `false` under `activity-log` in `config.yml`.

### Someone Opening Another Player's Chest

When an admin opens a chest that is not theirs, both lines are marked and name the owner:

```
[2026-07-28 15:23:25.085 ICT] OPEN player=Notch uuid=... chest=1 size=54 access=ADMIN_ACCESS owner=5ef5f7b2-...
```

## Visits That Changed Nothing

Most people open their chest, look at it, and close it again. Those entries are not written, so the log stays short enough to actually read. A chest whose items were only moved around counts as unchanged too: nothing was gained or lost.

To record every single visit instead, set `log-unchanged` to `true` under `activity-log` in `config.yml`.

## Log Files and Disk Space

When `echest-latest.log` passes the size set by `max-file-size-mb`, it is renamed and a new one is started. The renamed file is then compressed, which shrinks it to roughly a fiftieth of its size, so old logs cost very little.

| File | What it is |
|------|------------|
| `echest-latest.log` | The file being written right now. Always this name, never deleted. |
| `echest-20260728-151023-913.log.gz` | An older file, compressed. The name is the date and time it was closed. |
| `echest-20260728-151023-913.log` | An older file that has not been compressed yet. |

Because the date runs from year down to milliseconds, sorting the folder by name also sorts it by time. The times are your server's local time.

Compressed files older than `retention-days` are deleted automatically. The file being written right now is never deleted.

To read a compressed file, open it with 7-Zip, WinRAR, or any tool that handles `.gz`.

::: tip Changing file settings
`enabled`, `log-unchanged` and `shulker-contents` apply on `/ee reload`. The other three settings are read once when the server starts, so changing them needs a full restart.
:::
