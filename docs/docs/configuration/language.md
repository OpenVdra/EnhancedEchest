# Language

All player-facing text in EnhancedEchest lives in editable language files, so you can translate or reword every message. Files are loaded from:

```
plugins/EnhancedEchest/language/<locale>/
```

The plugin ships with `en_US` (English) and `vi_VN` (Tiếng Việt).

## Automatic Per-Player Language

By default each player sees messages and menus in **their own Minecraft client language**, as long as a matching translation is available (bundled, or added by you). A player using the game in Vietnamese sees Vietnamese; a player using English sees English, at the same time, on the same server. Changing the language in Minecraft's Options and reopening a menu updates it, no relog needed.

This is controlled by two options in [`config.yml`](/docs/configuration/):

```yaml
# Fallback language for clients whose language has no translation.
language: en_US
# Auto-detect each player's client language (default). Turn off to show everyone the 'language' above.
language-auto-detect: true
```

- A client whose language **has** a translation → that language.
- A client whose language has **no** translation → the `language` fallback above.
- With `language-auto-detect: false` → every player sees the single `language` locale, regardless of their client.

## Files

| File | Contents |
|------|----------|
| `messages.yml` | The plugin prefix and all chat messages (commands, errors, admin feedback, update notices) |
| `gui.yml` | Inventory titles and the labels used in the `/eclist` management menu |

## Formatting

Color is written with legacy `&` codes, including hex colors in the form `&#RRGGBB`. Placeholders such as `{prefix}`, `{player}`, `{index}`, and `{size}` are replaced at runtime.

```yaml
prefix: '&#9B59B6EɴʜᴀɴᴄᴇᴅEᴄʜᴇsᴛ &8⏩ &r'

admin:
  chest-added: '{prefix}&aAdded Ender Chest &e{index}&a (&e{size}&a slots) to &e{player}&a.'
```

The default messages follow a simple color palette:

| Color | Used for |
|-------|----------|
| `&#FF4444` | Errors |
| `&#F0C857` | Warnings |
| `&a` | Success |
| `&e` / `&f` | Highlighted values |
| `&7` / `&8` | Muted text |

::: tip MiniMessage is also supported
Any message that contains a `<` is parsed as [MiniMessage](https://docs.advntr.dev/minimessage/format) instead of legacy codes. This is how the update notice keeps its clickable download link.
:::

## Chest Titles

`gui.yml` controls how chest inventory titles are shown:

```yaml
enderchest:
  # Title of the first chest (index 1) when it has no custom name.
  title: 'Ender Chest'
  # Title of chests 2+ when they have no custom name. {index} is the chest number.
  title-numbered: 'Ender Chest {index}'
```

- Chest **#1** shows the un-numbered `title`
- Chests **#2 and up** show `title-numbered` with their index
- A chest with a **custom name** (set via `/eclist`) shows that name instead

## Adding a Translation

1. Copy the `en_US` folder inside `language/`
2. Rename the copy to your locale (for example `de_DE` or `fr_FR`)
3. Translate the text inside `messages.yml` and `gui.yml`
4. Run `/ee reload`

With auto-detect on (the default), players using that language now see it automatically, no need to change `language`. Set `language: <your-locale>` only if you want it to also be the **fallback** for clients whose language you haven't translated (or if you keep auto-detect off).

## Icon Picker Item Names {#icon-picker-item-names}

Item names in the chest icon picker work differently from the messages above. They come straight from each player's own Minecraft client and always show in whatever language that client is set to, with no configuration needed here.

Searching by name in the icon picker currently works best in English or Vietnamese. Other client languages still show correct item names, but a typed search term may only match the English name.

### Adding Icon Search for Another Language

You can add icon search support for a language we don't bundle, or override English/Vietnamese, without a plugin update. While the server is running, create a file at:

```
plugins/EnhancedEchest/icons/lang/<locale>.json
```

using a lowercase Minecraft locale id for `<locale>`, for example `de_de.json` or `fr_fr.json`. Each entry maps a Minecraft item or block translation key to the name you want search to match:

```json
{
  "item.minecraft.diamond": "Diamant",
  "block.minecraft.oak_planks": "Eichenbohlen"
}
```

Then run `/ee reload` to pick it up, no restart needed. The item's name shown in the picker already matches the player's client automatically either way; this file only affects what a typed search term matches.

### Building the File From Mojang's Data

Mojang publishes the official item names for free, buried inside JSON files meant for programs. Here's how to find them, using version `26.2` as a worked example (repeat for any other version):

1. Open the [version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json), search (Ctrl+F) for your version in quotes (e.g. `"26.2"`), and open the `url` link next to it.
2. On that page, search for `assetIndex` and open its `url` link. This lists every game asset, so search again for `minecraft/lang/` followed by your locale (e.g. `minecraft/lang/vi_vn.json`) and note the `hash` value right after it.
3. Download the file from `https://resources.download.minecraft.net/<first 2 chars of hash>/<full hash>`. For Vietnamese (`vi_vn`) on `26.2`, the hash `06fd8f3fcfc2c75f874f69e720d574be140b1261` gives [this link](https://resources.download.minecraft.net/06/06fd8f3fcfc2c75f874f69e720d574be140b1261).

::: tip English (`en_us`) works differently
It's not a separate file in the asset index, it ships inside the game client. On the version page from step 1, download the `client` jar under `downloads`, open it with any zip tool (rename the copy to end in `.zip` first if needed), and pull out `assets/minecraft/lang/en_us.json`.
:::

The downloaded file is one unformatted line of text. Open it in [VS Code](https://code.visualstudio.com/) or Notepad++ and use **Format Document** to make it readable. It has thousands of entries besides items, but nothing needs removing: the icon picker only reads keys starting with `item.minecraft.` or `block.minecraft.`.

4. Save the finished file at `plugins/EnhancedEchest/icons/lang/<locale>.json` (lowercase, e.g. `de_de.json`) and run `/ee reload`.
