import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/OpenVdra/EnhancedEchest'
const DISCORD = 'https://discord.com/invite/FJN7hJKPyb'

const manualPages = [
  'manual', 'download', 'installation', 'commands', 'permissions',
  'permission-chests', 'configuration', 'database', 'migration', 'language'
]

const enManualSidebar = [
  {
    text: 'Getting Started',
    items: [
      { text: 'Introduction', link: '/docs/manual' },
      { text: 'Download', link: '/docs/download' },
      { text: 'Installation', link: '/docs/installation' }
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
          { text: 'Database', link: '/docs/database' },
          { text: 'Migration', link: '/docs/migration' },
          { text: 'Language', link: '/docs/language' }
        ]
      }
    ]
  }
]

const viManualSidebar = [
  {
    text: 'Bắt đầu',
    items: [
      { text: 'Giới thiệu', link: '/vi/docs/manual' },
      { text: 'Tải về', link: '/vi/docs/download' },
      { text: 'Cài đặt', link: '/vi/docs/installation' }
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
          { text: 'Cơ sở dữ liệu', link: '/vi/docs/database' },
          { text: 'Chuyển dữ liệu', link: '/vi/docs/migration' },
          { text: 'Ngôn ngữ', link: '/vi/docs/language' }
        ]
      }
    ]
  }
]

const enSidebar = {
  ...Object.fromEntries(manualPages.map(page => [`/docs/${page}`, enManualSidebar])),
  '/docs/changelog': [{ text: 'Changelog', items: [{ text: 'Release history', link: '/docs/changelog' }] }],
  '/docs/': enManualSidebar
}

const viSidebar = {
  ...Object.fromEntries(manualPages.map(page => [`/vi/docs/${page}`, viManualSidebar])),
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
  // Two-tier navigation: the top nav below is deliberately light (site-level
  // links + a version menu); the in-docs section tabs (Manual /
  // Changelog / Contributing) live in the second bar rendered by the custom
  // theme layout (components/nav/SecondaryNav.vue).
  locales: {
    root: {
      label: 'English',
      lang: 'en',
      themeConfig: {
        // Version replaces the former top-level Changelog link. Changelog now
        // appears only in the secondary docs navigation. "Docs" stays active
        // (via activeMatch) while on the Changelog page too, since it's still
        // part of the docs section.
        nav: [
          { text: 'Home', link: '/' },
          { text: 'Docs', link: '/docs/manual', activeMatch: '^/docs/' },
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
          { text: 'Trang chủ', link: '/vi/' },
          { text: 'Tài liệu', link: '/vi/docs/manual', activeMatch: '^/vi/docs/' },
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
