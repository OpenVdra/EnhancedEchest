# Language

All player-facing text in EnhancedEchest lives in editable language files, so you can translate or reword every message. Files are loaded from:

```
plugins/EnhancedEchest/language/<locale>/
```

The plugin ships with `en_US` (English) and `vi_VN` (Tiếng Việt).

## Automatic Per-Player Language

By default each player sees messages and menus in **their own Minecraft client language**, as long as a matching translation is available (bundled, or added by you). A player using the game in Vietnamese sees Vietnamese; a player using English sees English — at the same time, on the same server. Changing the language in Minecraft's Options and reopening a menu updates it, no relog needed.

This is controlled by two options in [`config.yml`](/docs/configuration):

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

With auto-detect on (the default), players using that language now see it automatically — no need to change `language`. Set `language: <your-locale>` only if you want it to also be the **fallback** for clients whose language you haven't translated (or if you keep auto-detect off).

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

Typing out every item by hand is a lot of work. Mojang publishes the exact official names for free, but they are buried inside large JSON files meant for programs, not people, so here is a walkthrough with real links you can click through, using version `26.2` (the latest release as of this writing) as a worked example. Get fresh links the same way for any other version.

**1. Find your version.** Open the version manifest:

[https://piston-meta.mojang.com/mc/game/version_manifest_v2.json](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)

Most browsers display JSON as a searchable page. Press Ctrl+F (Cmd+F on Mac), search for your version number in quotes, for example `"26.2"`, and click the `url` link shown right next to it. For `26.2` that link is:

[https://piston-meta.mojang.com/v1/packages/c8eb00be8a1f9fb9adf70ee415b7e1f746b636e8/26.2.json](https://piston-meta.mojang.com/v1/packages/c8eb00be8a1f9fb9adf70ee415b7e1f746b636e8/26.2.json)

**2. Find the asset index.** On the page you just opened, search for `assetIndex` and click the `url` next to it. For `26.2` that link is:

[https://piston-meta.mojang.com/v1/packages/49da57a9512de46382d2fe4b68af047fea7a16f9/32.json](https://piston-meta.mojang.com/v1/packages/49da57a9512de46382d2fe4b68af047fea7a16f9/32.json)

This page lists every game asset, so it is large. The search box is essential here.

**3. Find your language file.** On that asset index page, search for `minecraft/lang/` followed by your locale, for example `minecraft/lang/vi_vn.json`. Right after it is a `hash` value, a long string of letters and numbers. Build a download link from it:

```
https://resources.download.minecraft.net/<first 2 characters of the hash>/<the full hash>
```

For example, Vietnamese (`vi_vn`) on `26.2` has the hash `06fd8f3fcfc2c75f874f69e720d574be140b1261`, so its download link is:

[https://resources.download.minecraft.net/06/06fd8f3fcfc2c75f874f69e720d574be140b1261](https://resources.download.minecraft.net/06/06fd8f3fcfc2c75f874f69e720d574be140b1261)

Opening that link downloads (or displays) the language file itself. Save it as `plugins/EnhancedEchest/icons/lang/vi_vn.json` (swap in your own locale) and skip to the last step below.

::: tip English (`en_us`) works differently
English is not a separate file in the asset index, it ships inside the game client itself. On the page from step 1, search for `client` under `downloads` and click its `url` to download the client jar (a large file, several tens of megabytes). Open it with any zip tool (7-Zip, WinRAR, or Windows' built-in "Extract All", right-click the file and rename the copy to end in `.zip` first if Windows does not recognise `.jar`) and pull out `assets/minecraft/lang/en_us.json` from inside.
:::

**Reading and saving the file.** The downloaded file is one giant line of text with no spacing at all, which is unreadable in a plain text editor like Notepad. Open it instead in a free code editor such as [Visual Studio Code](https://code.visualstudio.com/) or Notepad++. In VS Code, right-click anywhere in the text and choose **Format Document** to spread it into readable, indented lines, then use Ctrl+F to search inside it.

The file has thousands of entries for menus, advancements, and more, not just items, but you do not need to remove any of them. The icon picker only ever looks at keys starting with `item.minecraft.` or `block.minecraft.`, so saving it exactly as downloaded works fine, the extra entries are simply never read.

**4. Save it.** Place the finished file at `plugins/EnhancedEchest/icons/lang/<locale>.json` (lowercase, matching the locale id from step 3, for example `de_de.json`) and run `/ee reload`.
