# EnhancedEchest Docs

Documentation site for [EnhancedEchest](https://github.com/OpenVdra/EnhancedEchest), built with [VitePress](https://vitepress.dev/).

## Structure

```
docs/
├── .vitepress/
│   ├── components/        # Vue components used in markdown
│   │   ├── card/          # DocCard, CardGrid, FeatureCard
│   │   ├── config/        # ConfigGroup, ConfigProperty
│   │   ├── home/          # Contributors
│   │   └── table/         # CommandRow, PermCommandRow, PermRow, BaseTable
│   ├── theme/             # Custom theme overrides (style.css, index.js)
│   └── config.mts         # VitePress config (nav, sidebar)
├── docs/                  # All documentation pages (English, root locale)
│   ├── commands.md
│   ├── configuration.md
│   ├── database.md
│   ├── download.md
│   ├── features.md
│   ├── installation.md
│   ├── language.md
│   ├── migration.md
│   └── permissions.md
├── vi/                    # Vietnamese locale, mirrors the root structure
│   ├── docs/              # Translated documentation pages
│   └── index.md           # Translated home page
├── public/                # Static assets (logo, favicon)
├── index.md               # Home page
└── package.json
```

## Internationalization (i18n)

The site is multilingual via VitePress's built-in i18n. Locales are declared in
`.vitepress/config.mts` under `locales`: `root` (English, served from `/`) and `vi`
(Tiếng Việt, served from `/vi/`). Each locale carries its own `nav`, `sidebar`, and UI
labels; shared `themeConfig` (logo, social links, search) stays at the top level and is
merged in. A language-switcher dropdown is added to the nav automatically.

To add another language, add a locale entry in `config.mts` and create a folder mirroring
the root content (`<lang>/index.md` + `<lang>/docs/*.md`). The Vue components are registered
globally, so only the text inside the markdown needs translating. Use locale-prefixed links
(e.g. `/vi/docs/database`) inside translated pages.

## Development

```bash
npm install
npm run docs:dev      # Start dev server at http://localhost:5173
npm run docs:build    # Build for production → .vitepress/dist/
npm run docs:preview  # Preview production build
```

## Custom Components

All components are auto-imported via the VitePress theme.

| Component | Usage |
|---|---|
| `<CommandRow>` | Display a command with its permission node |
| `<PermRow>` | Standalone permission row |
| `<BaseTable>` | Table wrapper for permission rows |
| `<ConfigProperty>` | Config key with type, default, description |
| `<ConfigGroup>` | Groups multiple ConfigProperty entries |
| `<DocCard>` | Link card for navigation |
| `<CardGrid>` | Grid layout for DocCards and FeatureCards |
| `<FeatureCard>` | Feature highlight card |

## Deployment

The site is deployed to GitHub Pages by `.github/workflows/deploy-docs.yml` on every push to `main` that touches `docs/`.

## Contributing

Edit the relevant `.md` file under `docs/` and open a PR. To add new pages, register them in `.vitepress/config.mts` under `themeConfig.sidebar`.
