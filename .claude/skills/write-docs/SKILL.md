---
name: write-docs
description: Write EnhancedEchest end-user content - CHANGELOG entries, VitePress documentation pages, and the comments in config.yml. Use when adding a changelog entry for a release, writing/editing pages on the docs site (docs/), or adding/rewording a setting's comment in src/main/resources/config.yml. Enforces the plain, non-technical writing style and the site's component and Lucide-icon conventions.
---

# Writing EnhancedEchest docs and changelog

You are writing for **server owners and players**, not developers. They install the plugin, edit config files, and open an ender chest in game. They do not care about classes, methods, refactors, or how something works internally.

## Voice and rules (always)

- Short and clear. One idea per sentence. Cut every word that adds nothing.
- Say what changed for the user and why it matters to them. Never describe the code or the internal mechanism.
- No duplicate information. If a point is already made, do not restate it in another section.
- Plain words over jargon. If a technical term is unavoidable, keep it to the exact config key, command, or permission node the user types.
- Never mention HikariCP, Brigadier, the region scheduler, the dupe-safe load and save model, codecs, shading, or async executors. A feature that exists only for internal reliability is not a feature the reader can see.
- Name a setting by its own key plus the file: "the `default-size` setting in `config.yml`". Never write a dotted path like `enderchest.default-size`, the reader never sees one in the file.
- When permission nodes overlap, the result **applies**. It does not "win" (Vietnamese: "được áp dụng", not "thắng").
- Write "ender chest", not "ender chest block" (Vietnamese: drop "khối"), except when the sentence is really about the placed block.
- Never use the em-dash character. Use a comma, a full stop, or rewrite the sentence.
- Never use emoji anywhere.
- Match the existing tone of the file you are editing.

## Changelog

- File: `CHANGELOG.md` at the repo root. Both changelog pages on the docs site include this file, so you only edit this one file.
- Newest version goes at the top, right under the intro line.
- Format:

  ```
  ## <version> - <YYYY-MM-DD>

  ### Added
  - ...

  ### Changed
  - ...

  ### Fixed
  - ...

  ### Notes
  - ...
  ```

- No `v` prefix on the version. Use only the sections you need (`Added`, `Changed`, `Fixed`, `Removed`, `Notes`). Skip empty ones.
- Get the version from `build.gradle.kts` (`version = "..."`).
- Build the change list from `git log <previous-tag-or-commit>..HEAD --format="%h %s%n%b"`, then drop every commit that only touches `docs/`, `.claude/`, any `CLAUDE.md`, or internals with no visible effect. Never copy a commit subject verbatim.
- Each line is one user-facing outcome. Good: "Shift and right-click an ender chest now opens the chest list." Bad: "Refactored ConfigVersionService into YamlMigrator."
- Never address the reader as `you`, `your`, or `yours`. State the behavior or required action directly.
- Summarize the affected behavior once. Do not list multiple example scenarios when one general statement is accurate.
- Keep each bullet to one or two short sentences, roughly 25 words when possible. Use sub-bullets only when essential details cannot be stated clearly in the main bullet.
- Do not explain both the old and new behavior unless the contrast is required to understand the change.
- Backticks for every command, permission node, config key, and plugin name.
- A renamed or removed command, permission, or config key is breaking. Put it first in `Changed`, prefix it with **Breaking:**, give old to new, and name the one action the admin must take.
- Reserve `Notes` for required upgrade actions, compatibility details, or a brief "no action required" statement. Do not repeat other sections or document every edge case.

## Config comments (`src/main/resources/config.yml`)

The comment above a setting is the only documentation most admins ever read. It has to answer "what does this do and what happens if I change it" in one or two lines, in the same plain voice as the docs site.

- **Say what the setting does to the server, not how the plugin implements it.** An admin does not know what a capture, a cache, a queue, a thread or a tick is, and telling them it is free "per visit" or costs no "server time" means nothing to them. Translate the consequence into something they can see: log files filling faster, longer lines, more disk used, a slower login.
- Keep it to one or two lines under 100 characters each. If a setting genuinely needs more, link the docs site instead of writing a paragraph.
- Name the trade-off when there is one. `# Log files fill up several times faster.` is worth more than a sentence explaining the feature twice.
- **List the accepted values in full** when there is a fixed set, rather than describing the rule: `# One of: 9, 18, 27, 36, 45, 54.` beats "a multiple of 9 from 9 to 54". For free-form values, give the format and an example: `# Time format: 20s, 5m, 1h, 7d, or 1d_2h_30m. Units: s m h d w mo y.`
- Say what the setting cannot do when that is a likely wrong assumption, for example that only languages with a folder under `language/` can be shown.
- Settings that need a full restart are marked `# NEED RESTART:` on the line above, covering either one setting or a named group of them. Everything else applies on `/ee reload`, which the file header already states, so never repeat it per setting.
- No em-dash, no emoji, no second person outside the header lines that already use it.
- A new key needs its default in three places that must agree: the value in `config.yml`, the fallback in `PluginConfig`, and the `value` prop of its `<ConfigProperty>` in both `docs/docs/configuration/index.md` and `docs/vi/docs/configuration/index.md`.
- Removing a key means removing its `<ConfigGroup>` or `<ConfigProperty>` from both index pages **and** from the full-file example lower down the same pages, then adding a **Breaking:** changelog entry under `Changed`.

## Documentation site (VitePress, in `docs/`)

- Bilingual. English lives in `docs/docs/`, Vietnamese in `docs/vi/docs/`. When you add or change a page, update both languages so they stay in sync.
- Every page starts with frontmatter, at minimum a title:

  ```
  ---
  title: Page Title
  ---
  ```

- Use normal Markdown: headings, short paragraphs, numbered lists for steps. Keep pages scannable.
- Do not put `---` horizontal rules between sections. Heading levels carry the structure.
- VitePress custom containers (`::: tip`, `::: warning`) are fine for a single important callout. Do not overuse them.

### Components

- Cards go inside `<CardGrid>`. `<DocCard icon="Package" title="..." link="/docs/..." desc="one sentence" />` links to another page, `<FeatureCard icon="Archive" title="...">` holds slot content. Omit the `icon` prop entirely when you do not want one, never pass an empty string or an emoji.
- Commands use `<CommandRow commands="/ec" :aliases="['/enderchest']" permission="enhancedechest.command.open">` with one or two sentences inside. Do not wrap it in a div.
- Configuration pages use `<ConfigProperty>` inside `<ConfigGroup>`.
- Permissions pages are plain Markdown, not tables. Use a bold node then its description on the next line, or a single line for the admin command list:

  ```md
  **`enhancedechest.command.open`**
  One sentence describing what this unlocks.

  **`enhancedechest.admin.resize`** - `/ee resize`: one-line description.
  ```


### Lucide icons

- The site uses Lucide icons through a registered component. Inline syntax in Markdown is `<LucideIcon name="Download" :size="20" />`; card icons take the same names through the `icon` prop.
- Only icon names registered in `docs/.vitepress/components/icon/LucideIcon.vue` work. Before using an icon, check that its name is in that file's `ICONS` map.
- To use a new icon: add its import and an entry to the `ICONS` map in `LucideIcon.vue`, then reference it by that name. Use PascalCase Lucide names (for example `ShieldCheck`, `ArrowRightLeft`).
- Prefer a Lucide icon over an emoji or an image whenever you want a small inline symbol.

## Before finishing

- Re-read your text once and delete any sentence that repeats another.
- Shorten any changelog entry that is noticeably more verbose than the surrounding releases without adding necessary information.
- Search changelog changes for second-person wording such as `you`, `your`, and `yours`, and rewrite it.
- Search your output for the em-dash character and any emoji, and remove them.
- If you touched a docs page, confirm the matching page in the other language was updated too.
- If you touched `config.yml`, re-read each comment you wrote and cut any word that describes the plugin's internals rather than what the admin will notice.
</content>
