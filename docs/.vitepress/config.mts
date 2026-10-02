import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/OpenVdra/EnhancedEchest'
const DISCORD = 'https://discord.com/invite/FJN7hJKPyb'
const JAVADOCS = 'https://openvdra.github.io/EnhancedEchest/javadocs/'
// Simple Icons' Modrinth mark, the same one the ThirstWasTaken2 docs use.
const MODRINTH = 'https://modrinth.com/plugin/enhancedechest'
const MODRINTH_ICON = '<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>Modrinth</title><path d="M12.252.004a11.78 11.768 0 0 0-8.92 3.73 11 10.999 0 0 0-2.17 3.11 11.37 11.359 0 0 0-1.16 5.169c0 1.42.17 2.5.6 3.77.24.759.77 1.899 1.17 2.529a12.3 12.298 0 0 0 8.85 5.639c.44.05 2.54.07 2.76.02.2-.04.22.1-.26-1.7l-.36-1.37-1.01-.06a8.5 8.489 0 0 1-5.18-1.8 5.34 5.34 0 0 1-1.3-1.26c0-.05.34-.28.74-.5a37.572 37.545 0 0 1 2.88-1.629c.03 0 .5.45 1.06.98l1 .97 2.07-.43 2.06-.43 1.47-1.47c.8-.8 1.48-1.5 1.48-1.52 0-.09-.42-1.63-.46-1.7-.04-.06-.2-.03-1.02.18-.53.13-1.2.3-1.45.4l-.48.15-.53.53-.53.53-.93.1-.93.07-.52-.5a2.7 2.7 0 0 1-.96-1.7l-.13-.6.43-.57c.68-.9.68-.9 1.46-1.1.4-.1.65-.2.83-.33.13-.099.65-.579 1.14-1.069l.9-.9-.7-.7-.7-.7-1.95.54c-1.07.3-1.96.53-1.97.53-.03 0-2.23 2.48-2.63 2.97l-.29.35.28 1.03c.16.56.3 1.16.31 1.34l.03.3-.34.23c-.37.23-2.22 1.3-2.84 1.63-.36.2-.37.2-.44.1-.08-.1-.23-.6-.32-1.03-.18-.86-.17-2.75.02-3.73a8.84 8.839 0 0 1 7.9-6.93c.43-.03.77-.08.78-.1.06-.17.5-2.999.47-3.039-.01-.02-.1-.02-.2-.03Zm3.68.67c-.2 0-.3.1-.37.38-.06.23-.46 2.42-.46 2.52 0 .04.1.11.22.16a8.51 8.499 0 0 1 2.99 2 8.38 8.379 0 0 1 2.16 3.449 6.9 6.9 0 0 1 .4 2.8c0 1.07 0 1.27-.1 1.73a9.37 9.369 0 0 1-1.76 3.769c-.32.4-.98 1.06-1.37 1.38-.38.32-1.54 1.1-1.7 1.14-.1.03-.1.06-.07.26.03.18.64 2.56.7 2.78l.06.06a12.07 12.058 0 0 0 7.27-9.4c.13-.77.13-2.58 0-3.4a11.96 11.948 0 0 0-5.73-8.578c-.7-.42-2.05-1.06-2.25-1.06Z"/></svg>'

// Page paths (relative to /docs/ or /vi/docs/) grouped by folder, matching the
// on-disk layout under docs/docs/ and docs/vi/docs/: access/, configuration/,
// database/, features/. Trailing slash = that group's index.md.
const manualPages = [
  'getting-started', 'access/commands', 'access/permissions', 'access/permission-chests',
  'configuration/', 'configuration/migration', 'configuration/language',
  'configuration/activity-log',
  'database/', 'database/sqlite', 'database/mysql-mariadb', 'database/postgresql',
  'database/ssl-tls', 'database/cross-server', 'database/switching-backends'
]

// Features has its own top-level nav item (not part of Manual), so its pages
// get their own sidebar instead of living in enManualSidebar.
const featuresPages = ['features/', 'features/larger-ender-chests', 'features/multi-chest-system', 'features/bedrock-support']

const enFeaturesSidebar = [
  {
    text: 'Features',
    items: [
      { text: 'Overview', link: '/docs/features/' },
      { text: 'Larger Ender Chests', link: '/docs/features/larger-ender-chests' },
      { text: 'Multi-Chest System', link: '/docs/features/multi-chest-system' },
      { text: 'Bedrock Support', link: '/docs/features/bedrock-support' }
    ]
  }
]

const viFeaturesSidebar = [
  {
    text: 'Tính năng',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/features/' },
      { text: 'Rương Ender Lớn Hơn', link: '/vi/docs/features/larger-ender-chests' },
      { text: 'Hệ Thống Nhiều Rương', link: '/vi/docs/features/multi-chest-system' },
      { text: 'Hỗ Trợ Bedrock', link: '/vi/docs/features/bedrock-support' }
    ]
  }
]

const enManualSidebar = [
  {
    text: 'Getting Started',
    items: [
      { text: 'Overview', link: '/docs/getting-started' }
    ]
  },
  {
    text: 'Documentation',
    items: [
      {
        text: 'Access',
        collapsed: false,
        items: [
          { text: 'Commands', link: '/docs/access/commands' },
          { text: 'Permissions', link: '/docs/access/permissions' },
          { text: 'Permission Chests', link: '/docs/access/permission-chests' }
        ]
      },
      {
        text: 'Configuration',
        collapsed: false,
        items: [
          { text: 'Main Config', link: '/docs/configuration/' },
          { text: 'Migration', link: '/docs/configuration/migration' },
          { text: 'Language', link: '/docs/configuration/language' },
          { text: 'Activity Log', link: '/docs/configuration/activity-log' }
        ]
      },
      {
        text: 'Database',
        collapsed: false,
        items: [
          { text: 'Overview', link: '/docs/database/' },
          { text: 'SQLite', link: '/docs/database/sqlite' },
          { text: 'MySQL / MariaDB', link: '/docs/database/mysql-mariadb' },
          { text: 'PostgreSQL', link: '/docs/database/postgresql' },
          { text: 'SSL / TLS', link: '/docs/database/ssl-tls' },
          { text: 'Cross-Server', link: '/docs/database/cross-server' },
          { text: 'Switching Backends', link: '/docs/database/switching-backends' }
        ]
      }
    ]
  }
]

const viManualSidebar = [
  {
    text: 'Bắt đầu',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/getting-started' }
    ]
  },
  {
    text: 'Tài liệu',
    items: [
      {
        text: 'Truy cập',
        collapsed: false,
        items: [
          { text: 'Lệnh', link: '/vi/docs/access/commands' },
          { text: 'Quyền', link: '/vi/docs/access/permissions' },
          { text: 'Rương theo quyền', link: '/vi/docs/access/permission-chests' }
        ]
      },
      {
        text: 'Cấu hình',
        collapsed: false,
        items: [
          { text: 'Cấu hình chính', link: '/vi/docs/configuration/' },
          { text: 'Chuyển dữ liệu', link: '/vi/docs/configuration/migration' },
          { text: 'Ngôn ngữ', link: '/vi/docs/configuration/language' },
          { text: 'Nhật ký hoạt động', link: '/vi/docs/configuration/activity-log' }
        ]
      },
      {
        text: 'Cơ Sở Dữ Liệu',
        collapsed: false,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/database/' },
          { text: 'SQLite', link: '/vi/docs/database/sqlite' },
          { text: 'MySQL / MariaDB', link: '/vi/docs/database/mysql-mariadb' },
          { text: 'PostgreSQL', link: '/vi/docs/database/postgresql' },
          { text: 'SSL / TLS', link: '/vi/docs/database/ssl-tls' },
          { text: 'Liên máy chủ', link: '/vi/docs/database/cross-server' },
          { text: 'Chuyển đổi Backend', link: '/vi/docs/database/switching-backends' }
        ]
      }
    ]
  }
]

const enSidebar = {
  ...Object.fromEntries(manualPages.map(page => [`/docs/${page}`, enManualSidebar])),
  ...Object.fromEntries(featuresPages.map(page => [`/docs/${page}`, enFeaturesSidebar])),
  '/docs/changelog': [{ text: 'Changelog', items: [{ text: 'Release history', link: '/docs/changelog' }] }],
  '/docs/': enManualSidebar
}

const viSidebar = {
  ...Object.fromEntries(manualPages.map(page => [`/vi/docs/${page}`, viManualSidebar])),
  ...Object.fromEntries(featuresPages.map(page => [`/vi/docs/${page}`, viFeaturesSidebar])),
  '/vi/docs/changelog': [{ text: 'Nhật ký thay đổi', items: [{ text: 'Lịch sử phát hành', link: '/vi/docs/changelog' }] }],
  '/vi/docs/': viManualSidebar
}

export default defineConfig({
  title: "EnhancedEchest",
  description: "Bigger ender chests for your players, several per person, each with its own name and icon.",
  // GitHub project page is served from /EnhancedEchest/. If you later point a custom
  // domain at the site (add public/CNAME), change this back to '/'.
  base: '/EnhancedEchest/',
  cleanUrls: true,
  head: [
    // Entries in `head` are emitted verbatim, so the `base` is NOT prepended
    // automatically the way it is for themeConfig.logo / markdown links. The
    // href must therefore include the base path, otherwise on the GitHub Pages
    // project site the favicon resolves to the domain root and 404s.
    ['link', { rel: 'icon', type: 'image/png', href: '/EnhancedEchest/logo.png' }],
    ['link', { rel: 'apple-touch-icon', href: '/EnhancedEchest/logo.png' }],
    // Social share preview (Discord, Twitter, etc.) when a docs link is pasted.
    ['meta', { property: 'og:image', content: 'https://openvdra.github.io/EnhancedEchest/logo.png' }],
    ['meta', { name: 'twitter:card', content: 'summary' }],
    ['meta', { name: 'twitter:image', content: 'https://openvdra.github.io/EnhancedEchest/logo.png' }],
  ],
  // Published to Modrinth by hand, not part of the site.
  srcExclude: ['MODRINTH.md'],
  themeConfig: {
    // Shared across every locale; per-locale nav / sidebar / editLink live under
    // each entry in `locales` below and are deep-merged over these.
    logo: '/logo.png',

    // Renders the little diagonal arrow after markdown links that point off-site.
    externalLinkIcon: true,

    socialLinks: [
      { icon: 'github', link: REPO },
      { icon: { svg: MODRINTH_ICON }, link: MODRINTH, ariaLabel: 'Modrinth' },
      { icon: 'discord', link: DISCORD }
    ],

    search: {
      provider: 'local'
    }
  },

  // i18n. The English site is served from the root; the Vietnamese site mirrors
  // it under /vi/. Each locale carries its own nav, sidebar and UI labels. The
  // content lives in `vi/` mirroring the root structure; the Vue components are
  // registered globally so they are reused as-is in both languages.
  //
  locales: {
    root: {
      label: 'English',
      lang: 'en',
      themeConfig: {
        nav: [
          { text: 'Docs', link: '/docs/getting-started', activeMatch: '^/docs/(getting-started|access|configuration|database)(/|$)' },
          { text: 'Features', link: '/docs/features/', activeMatch: '^/docs/features(/|$)' },
          { text: 'SQLite Editor', link: '/docs/sqlite-editor', activeMatch: '^/docs/sqlite-editor(/|$)' },
          { text: 'Javadocs', link: JAVADOCS },
          { component: 'VersionDropdown' },
          { component: 'LanguageDropdown' }
        ],

        sidebar: enSidebar,

        editLink: {
          pattern: 'https://github.com/OpenVdra/EnhancedEchest/edit/main/docs/:path',
          text: 'Edit this page on GitHub'
        }
      }
    },

    vi: {
      label: 'Tiếng Việt',
      lang: 'vi',
      description: 'Rương Ender lớn hơn cho người chơi, nhiều rương mỗi người, mỗi rương có tên và biểu tượng riêng.',
      themeConfig: {
        nav: [
          { text: 'Tài liệu', link: '/vi/docs/getting-started', activeMatch: '^/vi/docs/(getting-started|access|configuration|database)(/|$)' },
          { text: 'Tính năng', link: '/vi/docs/features/', activeMatch: '^/vi/docs/features(/|$)' },
          { text: 'Sửa SQLite', link: '/vi/docs/sqlite-editor', activeMatch: '^/vi/docs/sqlite-editor(/|$)' },
          { text: 'Javadocs', link: JAVADOCS },
          { component: 'VersionDropdown' },
          { component: 'LanguageDropdown' }
        ],

        sidebar: viSidebar,

        editLink: {
          pattern: 'https://github.com/OpenVdra/EnhancedEchest/edit/main/docs/:path',
          text: 'Chỉnh sửa trang này trên GitHub'
        },

        // VitePress UI strings (it does not translate these from `lang` alone).
        outlineTitle: 'Trên trang này',
        docFooter: {
          prev: 'Trang trước',
          next: 'Trang sau'
        },
        lastUpdatedText: 'Cập nhật lần cuối',
        returnToTopLabel: 'Về đầu trang',
        sidebarMenuLabel: 'Menu',
        darkModeSwitchLabel: 'Giao diện',
        lightModeSwitchTitle: 'Chuyển sang giao diện sáng',
        darkModeSwitchTitle: 'Chuyển sang giao diện tối'
      }
    }
  }
})
