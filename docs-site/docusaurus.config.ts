import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
import generatedRedirects from './redirects.generated.json';

const siteUrl = process.env.DOCS_SITE_URL ?? 'https://lnyo-cly.github.io';
const siteBaseUrl = process.env.DOCS_SITE_BASE_URL ?? '/ai4j/';

const config: Config = {
  title: 'AI4J 文档站',
  tagline: '面向 JDK8 的 Java 大模型 SDK、Coding Agent 与 Agent 框架',
  favicon: 'img/favicon.ico',
  future: {v4: true},
  url: siteUrl,
  baseUrl: siteBaseUrl,
  organizationName: 'LnYo-Cly',
  projectName: 'ai4j',
  onBrokenLinks: 'throw',
  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans', 'en'],
  },
  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: 'docs',
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/LnYo-Cly/ai4j/tree/main/docs-site/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],
  plugins: [
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          // Consolidated getting-started pages; preserve existing public links.
          {from: '/docs/start-here/five-minute-first-chat', to: '/docs/getting-started/quickstart-java'},
          {from: '/docs/start-here/first-chat', to: '/docs/capabilities/models/chat'},
          {from: '/docs/start-here/troubleshooting', to: '/docs/production/troubleshooting'},

          // agent/ — placeholder pages with explicit migration targets
          {from: '/docs/agent/orchestration/stategraph', to: '/docs/agent/runtimes/workflow-stategraph'},
          {from: '/docs/agent/orchestration/subagent-handoff', to: '/docs/agent/orchestration/subagent-handoff-policy'},
          {from: '/docs/agent/orchestration/teams', to: '/docs/agent/orchestration/agent-teams'},
          {from: '/docs/agent/orchestration/teams-api-reference', to: '/docs/agent/orchestration/agent-teams-api-reference'},
          {from: '/docs/agent/observability/trace', to: '/docs/agent/observability/trace-observability'},
          {from: '/docs/agent/coding-agent-acp-integration', to: '/docs/products/coding-agent/mcp-and-acp'},
          {from: '/docs/agent/coding-agent-cli', to: '/docs/products/coding-agent/cli-and-tui'},
          {from: '/docs/agent/coding-agent-command-reference', to: '/docs/products/coding-agent/command-reference'},
          {from: '/docs/agent/multi-provider-profiles', to: '/docs/products/coding-agent/provider-profiles'},
          {from: '/docs/agent/custom-agent-development', to: '/docs/agent/architecture'},
          {from: '/docs/agent/memory-management', to: '/docs/agent/memory/memory-and-state'},
          {from: '/docs/agent/skill', to: '/docs/agent/skills'},
          {from: '/docs/agent/skills-overview', to: '/docs/agent/skills'},

          // core-sdk/chat/ → core-sdk/model-access/ and core-sdk/memory/
          {from: '/docs/core-sdk/chat/stream', to: '/docs/capabilities/models/streaming'},
          {from: '/docs/core-sdk/chat/non-stream', to: '/docs/capabilities/models/chat'},
          {from: '/docs/core-sdk/chat/multimodal', to: '/docs/capabilities/models/multimodal'},
          {from: '/docs/core-sdk/chat/tool-calling', to: '/docs/capabilities/tools/function-calling'},
          {from: '/docs/core-sdk/chat/chat-memory', to: '/docs/capabilities/chat-memory/'},
          {from: '/docs/core-sdk/chat/chat-memory-session-management', to: '/docs/capabilities/chat-memory/'},

          // core-sdk/responses/ → core-sdk/model-access/
          {from: '/docs/core-sdk/responses/chat-vs-responses', to: '/docs/capabilities/models/chat-vs-responses'},
          {from: '/docs/core-sdk/responses/non-stream', to: '/docs/capabilities/models/responses'},
          {from: '/docs/core-sdk/responses/stream-events', to: '/docs/capabilities/models/streaming'},

          // core-sdk/mcp/ → mcp/ (protocol-capabilities stays as canonical)
          {from: '/docs/core-sdk/mcp/overview', to: '/docs/capabilities/mcp/overview'},
          {from: '/docs/core-sdk/mcp/client-integration', to: '/docs/capabilities/mcp/client-integration'},
          {from: '/docs/core-sdk/mcp/transport-types', to: '/docs/capabilities/mcp/transport-types'},
          {from: '/docs/core-sdk/mcp/gateway-and-multi-service', to: '/docs/capabilities/mcp/gateway-management'},
          {from: '/docs/core-sdk/mcp/third-party-mcp-integration', to: '/docs/capabilities/mcp/third-party-mcp-integration'},
          {from: '/docs/core-sdk/mcp/tool-exposure-semantics', to: '/docs/capabilities/mcp/tool-exposure-semantics'},
          {from: '/docs/core-sdk/mcp/publish-your-mcp-server', to: '/docs/capabilities/mcp/build-your-mcp-server'},
          {from: '/docs/core-sdk/mcp/positioning-and-when-to-use', to: '/docs/capabilities/mcp/use-cases-and-paths'},
          {from: '/docs/core-sdk/mcp/streamable-http', to: '/docs/capabilities/mcp/streamable-http'},

          // Canonical Skill and Streamable HTTP entry points.
          {from: '/docs/core-sdk/skills', to: '/docs/capabilities/skills/overview'},
          {from: '/docs/coding-agent/skill', to: '/docs/products/coding-agent/skills'},
          {from: '/docs/mcp/streamable-http-transport', to: '/docs/capabilities/mcp/streamable-http'},

          // core-sdk/ misc → canonical subdirectories
          {from: '/docs/core-sdk/embedding', to: '/docs/capabilities/rag/embedding'},
          {from: '/docs/core-sdk/pinecone-rag-workflow', to: '/docs/capabilities/rag/overview'},
          {from: '/docs/core-sdk/searxng-enhancement', to: '/docs/capabilities/rag/online-search'},
          {from: '/docs/core-sdk/spi-http-stack', to: '/docs/extending/code-level/spi-http-stack'},

          // ai-basics/ → core-sdk/ canonical (37 pages)
          {from: '/docs/ai-basics/overview', to: '/docs/capabilities/overview'},
          {from: '/docs/ai-basics/architecture-and-package-map', to: '/docs/reference/maps/architecture-and-module-map'},
          {from: '/docs/ai-basics/unified-service-entry', to: '/docs/capabilities/service-entry'},
          {from: '/docs/ai-basics/service-factory-and-registry', to: '/docs/capabilities/service-entry'},
          {from: '/docs/ai-basics/request-and-response-conventions', to: '/docs/capabilities/models/request-and-response-conventions'},
          {from: '/docs/ai-basics/platform-adaptation', to: '/docs/capabilities/models/platform-service-matrix'},
          {from: '/docs/ai-basics/provider-and-model-extension', to: '/docs/extending/code-level/provider-extension'},
          {from: '/docs/ai-basics/memory-and-tool-boundaries', to: '/docs/capabilities/chat-memory/memory-and-tool-boundaries'},
          {from: '/docs/ai-basics/skills', to: '/docs/capabilities/skills/overview'},
          {from: '/docs/ai-basics/chat/stream', to: '/docs/capabilities/models/streaming'},
          {from: '/docs/ai-basics/chat/non-stream', to: '/docs/capabilities/models/chat'},
          {from: '/docs/ai-basics/chat/multimodal', to: '/docs/capabilities/models/multimodal'},
          {from: '/docs/ai-basics/chat/tool-calling', to: '/docs/capabilities/tools/function-calling'},
          {from: '/docs/ai-basics/chat/chat-memory', to: '/docs/capabilities/chat-memory/'},
          {from: '/docs/ai-basics/chat/chat-memory-session-management', to: '/docs/capabilities/chat-memory/'},
          {from: '/docs/ai-basics/responses/chat-vs-responses', to: '/docs/capabilities/models/chat-vs-responses'},
          {from: '/docs/ai-basics/responses/non-stream', to: '/docs/capabilities/models/responses'},
          {from: '/docs/ai-basics/responses/stream-events', to: '/docs/capabilities/models/streaming'},
          {from: '/docs/ai-basics/services/audio', to: '/docs/capabilities/media/audio'},
          {from: '/docs/ai-basics/services/embedding', to: '/docs/capabilities/rag/embedding'},
          {from: '/docs/ai-basics/services/image-generation', to: '/docs/capabilities/media/image-generation'},
          {from: '/docs/ai-basics/services/realtime', to: '/docs/capabilities/media/realtime'},
          {from: '/docs/ai-basics/services/rerank', to: '/docs/capabilities/rag/rerank'},
          {from: '/docs/ai-basics/rag/overview', to: '/docs/capabilities/rag/overview'},
          {from: '/docs/ai-basics/rag/architecture-and-indexing', to: '/docs/capabilities/rag/overview'},
          {from: '/docs/ai-basics/rag/chunking-strategies', to: '/docs/capabilities/rag/chunking-strategies'},
          {from: '/docs/ai-basics/rag/citations-trace-and-ui-integration', to: '/docs/capabilities/rag/citations-and-trace'},
          {from: '/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow', to: '/docs/capabilities/rag/hybrid-retrieval'},
          {from: '/docs/ai-basics/rag/ingestion-pipeline', to: '/docs/capabilities/rag/ingestion-pipeline'},
          {from: '/docs/ai-basics/rag/pinecone-workflow', to: '/docs/integrations/solutions/pinecone-vector-workflow'},
          {from: '/docs/ai-basics/rag/vector-store-and-storage-backends', to: '/docs/capabilities/rag/vector-store-and-backends'},
          {from: '/docs/ai-basics/online-search/overview', to: '/docs/capabilities/rag/online-search'},
          {from: '/docs/ai-basics/online-search/searxng', to: '/docs/capabilities/rag/online-search'},
          {from: '/docs/ai-basics/enhancements/overview', to: '/docs/extending/overview'},
          {from: '/docs/ai-basics/enhancements/pinecone-rag-workflow', to: '/docs/integrations/solutions/pinecone-vector-workflow'},
          {from: '/docs/ai-basics/enhancements/searxng-enhancement', to: '/docs/capabilities/rag/online-search'},
          {from: '/docs/ai-basics/enhancements/spi-http-stack', to: '/docs/extending/code-level/spi-http-stack'},

          // getting-started/ → start-here/ and core-sdk/
          {from: '/docs/getting-started/installation', to: '/docs/getting-started/quickstart-java'},
          {from: '/docs/getting-started/quickstart-openai-jdk8', to: '/docs/getting-started/quickstart-java'},
          {from: '/docs/getting-started/quickstart-ollama', to: '/docs/getting-started/quickstart-java'},
          {from: '/docs/getting-started/quickstart-springboot', to: '/docs/getting-started/quickstart-spring-boot'},
          {from: '/docs/getting-started/chat-and-responses-guide', to: '/docs/capabilities/models/chat-vs-responses'},
          {from: '/docs/getting-started/coding-agent-cli-quickstart', to: '/docs/products/coding-agent/quickstart'},
          {from: '/docs/getting-started/multimodal-and-function-call', to: '/docs/getting-started/first-tool-call'},
          {from: '/docs/getting-started/platforms-and-service-matrix', to: '/docs/capabilities/models/platform-service-matrix'},
          {from: '/docs/getting-started/troubleshooting', to: '/docs/production/troubleshooting'},
          {from: '/docs/getting-started/modules-and-maven-central', to: '/docs/reference/maps/architecture-and-module-map'},
          {from: '/docs/getting-started/spring-boot-autoconfiguration', to: '/docs/integrations/spring-boot/auto-configuration'},
          {from: '/docs/getting-started/version-compatibility', to: '/docs/reference/version-compatibility'},

          // guides/ → solutions/ (blog-migration-map retained as canonical reference)
          {from: '/docs/guides/deepseek-stream-search-rag', to: '/docs/integrations/solutions/deepseek-stream-search-rag'},
          {from: '/docs/guides/pinecone-vector-workflow', to: '/docs/integrations/solutions/pinecone-vector-workflow'},
          {from: '/docs/guides/rag-legal-assistant', to: '/docs/integrations/solutions/legal-assistant'},
          {from: '/docs/guides/searxng-web-search', to: '/docs/integrations/solutions/searxng-web-search'},
          {from: '/docs/guides/spi-dispatcher-connectionpool', to: '/docs/integrations/solutions/spi-dispatcher-connectionpool'},
          {from: '/docs/guides/springboot-jdbc-agent-memory', to: '/docs/integrations/solutions/springboot-jdbc-agent-memory'},
          {from: '/docs/guides/springboot-mysql-chat-memory', to: '/docs/integrations/solutions/springboot-mysql-chat-memory'},
          {from: '/docs/guides/flowgram-mysql-taskstore', to: '/docs/integrations/solutions/flowgram-mysql-taskstore'},
          {from: '/docs/guides/rag-ingestion-vector-store', to: '/docs/integrations/solutions/rag-ingestion-vector-store'},

          // coding-agent/ rename
          {from: '/docs/coding-agent/release-and-installation', to: '/docs/products/coding-agent/install-and-release'},

          // flowgram/ typo fix
          {from: '/docs/flowgram/builtin-nodes', to: '/docs/products/flowgram/built-in-nodes'},

          // 2026-08 IA restructure — generated from restructure-map.tsv
          ...generatedRedirects,
        ],
      },
    ],
  ],
  themes: [
    [
      require.resolve('@easyops-cn/docusaurus-search-local'),
      {
        hashed: true,
        language: ['zh', 'en'],
        indexDocs: true,
        indexBlog: false,
        docsRouteBasePath: 'docs',
        searchResultLimits: 10,
        searchResultContextMaxLength: 80,
      },
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
          type: 'search',
          position: 'right',
        },
        {
          type: 'localeDropdown',
          position: 'right',
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
            {label: 'Coding Agent', to: '/docs/products/coding-agent/overview'},
            {label: 'Core SDK', to: '/docs/capabilities/overview'},
            {label: 'MCP', to: '/docs/capabilities/mcp/overview'},
            {label: 'Agent', to: '/docs/agent/overview'},
            {label: 'FlowGram', to: '/docs/products/flowgram/overview'},
            {label: '生产检查清单', to: '/docs/production/production-checklist'},
          ],
        },
        {
          title: '资源',
          items: [
            {label: 'API Reference', to: '/docs/reference/api'},
            {label: '版本与兼容性', to: '/docs/reference/version-compatibility'},
            {label: '安全边界', to: '/docs/production/security'},
            {label: '选型对比', to: '/docs/reference/about/comparison'},
            {label: '历史博客迁移映射', to: '/docs/reference/maintainers/blog-migration-map'},
            {label: 'Cloudflare Pages 部署指南', to: '/docs/reference/maintainers/cloudflare-pages'},
          ],
        },
        {
          title: '开源',
          items: [
            {label: 'GitHub', href: 'https://github.com/LnYo-Cly/ai4j'},
            {label: 'Issues', href: 'https://github.com/LnYo-Cly/ai4j/issues'},
            {label: '贡献指南', to: '/docs/reference/about/contributing'},
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
