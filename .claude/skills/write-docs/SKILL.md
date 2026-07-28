---
name: write-docs
description: Write EnhancedEchest end-user content - CHANGELOG entries and VitePress documentation pages. Use when adding a changelog entry for a release, or writing/editing pages on the docs site (docs/). Enforces the plain, non-technical writing style and the site's component and Lucide-icon conventions.
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
- Build the change list from `git log <previous-tag-or-commit>..HEAD --format="%h %s%n%b"`, then drop every commit that only touches `docs/`, `.claude/`, or internals with no visible effect. Never copy a commit subject verbatim.
- Each line is one user-facing outcome. Good: "Shift and right-click an ender chest now opens your chest list." Bad: "Refactored ConfigVersionService into YamlMigrator."
- Roughly 25 words per bullet, at most 3 sub-bullets per feature. A sub-bullet must add something its lead bullet did not say.
- Backticks for every command, permission node, config key, and plugin name.
- A renamed or removed command, permission, or config key is breaking. Put it first in `Changed`, prefix it with **Breaking:**, give old to new, and name the one action the admin must take.
- Put upgrade advice or "no action needed" in `Notes`.

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
- Search your output for the em-dash character and any emoji, and remove them.
- If you touched a docs page, confirm the matching page in the other language was updated too.
</content>
