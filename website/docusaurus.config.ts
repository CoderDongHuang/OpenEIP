import type {Config} from '@docusaurus/types';
import type {Options, ThemeConfig} from '@docusaurus/preset-classic';

const config: Config = {
  title: 'OpenEIP',
  tagline: 'Open Enterprise Intelligence Platform',
  favicon: 'img/favicon.svg',
  url: 'https://coderdonghuang.github.io',
  baseUrl: '/OpenEIP/',
  organizationName: 'CoderDongHuang',
  projectName: 'OpenEIP',
  onBrokenLinks: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },
  i18n: {
    defaultLocale: 'zh-CN',
    locales: ['zh-CN'],
  },
  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: 'docs',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Options,
    ],
  ],
  themeConfig: {
    navbar: {
      title: 'OpenEIP',
      items: [
        {type: 'docSidebar', sidebarId: 'docs', position: 'left', label: '文档'},
        {href: 'https://github.com/CoderDongHuang/OpenEIP', label: 'GitHub', position: 'right'},
      ],
    },
    footer: {
      style: 'light',
      copyright: `Copyright ${new Date().getFullYear()} OpenEIP. Apache License 2.0.`,
    },
  } satisfies ThemeConfig,
};

export default config;
