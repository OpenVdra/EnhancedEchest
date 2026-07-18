import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/OpenVdra/EnhancedEchest'
const DISCORD = 'https://discord.com/invite/FJN7hJKPyb'

const manualPages = [
  'getting-started', 'commands', 'permissions',
  'permission-chests', 'configuration', 'migration', 'language',
  'database', 'sqlite', 'mysql-mariadb', 'postgresql', 'ssl-tls', 'cross-server', 'switching-backends'
]

// Features has its own top-level nav item (not part of Manual), so its pages
// get their own sidebar instead of living in enManualSidebar.
const featuresPages = ['features', 'larger-ender-chests', 'multi-chest-system', 'bedrock-support']

const enFeaturesSidebar = [
  {
    text: 'Features',
    items: [
      { text: 'Overview', link: '/docs/features' },
      { text: 'Larger Ender Chests', link: '/docs/larger-ender-chests' },
      { text: 'Multi-Chest System', link: '/docs/multi-chest-system' },
      { text: 'Bedrock Support', link: '/docs/bedrock-support' }
    ]
  }
]

const viFeaturesSidebar = [
  {
    text: 'Tính năng',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/features' },
      { text: 'Rương Ender Lớn Hơn', link: '/vi/docs/larger-ender-chests' },
      { text: 'Hệ Thống Nhiều Rương', link: '/vi/docs/multi-chest-system' },
      { text: 'Hỗ Trợ Bedrock', link: '/vi/docs/bedrock-support' }
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
          { text: 'Commands', link: '/docs/commands' },
          { text: 'Permissions', link: '/docs/permissions' },
          { text: 'Permission Chests', link: '/docs/permission-chests' }
        ]
      },
      {
        text: 'Configuration',
        collapsed: false,
        items: [
          { text: 'Main Config', link: '/docs/configuration' },
          { text: 'Migration', link: '/docs/migration' },
          { text: 'Language', link: '/docs/language' }
        ]
      },
      {
        text: 'Database',
        collapsed: false,
        items: [
          { text: 'Overview', link: '/docs/database' },
          { text: 'SQLite', link: '/docs/sqlite' },
          { text: 'MySQL / MariaDB', link: '/docs/mysql-mariadb' },
          { text: 'PostgreSQL', link: '/docs/postgresql' },
          { text: 'SSL / TLS', link: '/docs/ssl-tls' },
          { text: 'Cross-Server', link: '/docs/cross-server' },
          { text: 'Switching Backends', link: '/docs/switching-backends' }
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
          { text: 'Lệnh', link: '/vi/docs/commands' },
          { text: 'Quyền', link: '/vi/docs/permissions' },
          { text: 'Rương theo quyền', link: '/vi/docs/permission-chests' }
        ]
      },
      {
        text: 'Cấu hình',
        collapsed: false,
        items: [
          { text: 'Cấu hình chính', link: '/vi/docs/configuration' },
          { text: 'Chuyển dữ liệu', link: '/vi/docs/migration' },
          { text: 'Ngôn ngữ', link: '/vi/docs/language' }
        ]
      },
      {
        text: 'Cơ Sở Dữ Liệu',
        collapsed: false,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/database' },
          { text: 'SQLite', link: '/vi/docs/sqlite' },
          { text: 'MySQL / MariaDB', link: '/vi/docs/mysql-mariadb' },
          { text: 'PostgreSQL', link: '/vi/docs/postgresql' },
          { text: 'SSL / TLS', link: '/vi/docs/ssl-tls' },
          { text: 'Liên máy chủ', link: '/vi/docs/cross-server' },
          { text: 'Chuyển đổi Backend', link: '/vi/docs/switching-backends' }
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
    ['meta', { property: 'og:image', content: 'https://openvdra.github.io/EnhancedEchest/banner.png' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:image', content: 'https://openvdra.github.io/EnhancedEchest/banner.png' }],
  ],
  themeConfig: {
    // Shared across every locale; per-locale nav / sidebar / editLink live under
    // each entry in `locales` below and are deep-merged over these.
    logo: '/logo.png',

    // Renders the little diagonal arrow after markdown links that point off-site.
    externalLinkIcon: true,

    socialLinks: [
      { icon: 'github', link: REPO },
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
          { text: 'Docs', link: '/docs/getting-started', activeMatch: '^/docs/(getting-started|commands|permissions|permission-chests|configuration|migration|language|database|sqlite|mysql-mariadb|postgresql|ssl-tls|cross-server|switching-backends)(/|$)' },
          { text: 'Features', link: '/docs/features', activeMatch: '^/docs/(features|larger-ender-chests|multi-chest-system|bedrock-support)(/|$)' },
          { text: 'SQLite Editor', link: '/docs/sqlite-editor', activeMatch: '^/docs/sqlite-editor(/|$)' },
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
          { text: 'Tài liệu', link: '/vi/docs/getting-started', activeMatch: '^/vi/docs/(getting-started|commands|permissions|permission-chests|configuration|migration|language|database|sqlite|mysql-mariadb|postgresql|ssl-tls|cross-server|switching-backends)(/|$)' },
          { text: 'Tính năng', link: '/vi/docs/features', activeMatch: '^/vi/docs/(features|larger-ender-chests|multi-chest-system|bedrock-support)(/|$)' },
          { text: 'Sửa SQLite', link: '/vi/docs/sqlite-editor', activeMatch: '^/vi/docs/sqlite-editor(/|$)' },
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
