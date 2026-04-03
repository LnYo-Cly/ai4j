import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'AI4J 文档站',
  tagline: '面向 JDK8 的 Java 大模型 SDK、Coding Agent 与 Agent 框架',
  favicon: 'img/favicon.ico',
  future: {v4: true},
  url: 'https://docs.ai4j.dev',
  baseUrl: '/',
  organizationName: 'LnYo-Cly',
  projectName: 'ai4j-sdk',
  onBrokenLinks: 'throw',
  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans'],
  },
  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: 'docs',
          sidebarPath: './sidebars.ts',
          include: [
            'intro.md',
            'glossary.md',
            'faq.md',
            'getting-started/**/*.md',
            'getting-started/**/*.mdx',
            'ai-basics/**/*.md',
            'ai-basics/**/*.mdx',
            'guides/**/*.md',
            'guides/**/*.mdx',
            'mcp/**/*.md',
            'mcp/**/*.mdx',
            'coding-agent/**/*.md',
            'coding-agent/**/*.mdx',
            'agent/**/*.md',
            'agent/**/*.mdx',
            'flowgram/**/*.md',
            'flowgram/**/*.mdx',
            'deploy/**/*.md',
            'deploy/**/*.mdx',
          ],
          editUrl: 'https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],
  themeConfig: {
    image: 'img/docusaurus-social-card.jpg',
    navbar: {
      title: 'AI4J 文档站',
      logo: {
        alt: 'AI4J Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: '文档',
        },
        {
          href: 'https://github.com/LnYo-Cly/ai4j',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: '文档',
          items: [
            {label: '开始阅读', to: '/docs/intro'},
            {label: 'Coding Agent', to: '/docs/coding-agent/overview'},
            {label: 'AI基础能力接入', to: '/docs/ai-basics/overview'},
            {label: 'MCP', to: '/docs/mcp/overview'},
            {label: '智能体 Agent', to: '/docs/agent/overview'},
            {label: 'Flowgram', to: '/docs/flowgram/overview'},
          ],
        },
        {
          title: '资源',
          items: [
            {label: '历史博客迁移映射', to: '/docs/guides/blog-migration-map'},
            {label: 'Cloudflare Pages 部署指南', to: '/docs/deploy/cloudflare-pages'},
          ],
        },
        {
          title: '开源',
          items: [
            {label: 'GitHub', href: 'https://github.com/LnYo-Cly/ai4j'},
            {label: 'Issues', href: 'https://github.com/LnYo-Cly/ai4j/issues'},
          ],
        },
      ],
      copyright: `Copyright (c) ${new Date().getFullYear()} AI4J Contributors · 基于 Docusaurus 构建`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
