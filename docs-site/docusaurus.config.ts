import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

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
            'start-here/*.md',
            'start-here/**/*.md',
            'start-here/**/*.mdx',
            'core-sdk/*.md',
            'core-sdk/**/*.md',
            'core-sdk/**/*.mdx',
            'spring-boot/*.md',
            'spring-boot/**/*.md',
            'spring-boot/**/*.mdx',
            'solutions/*.md',
            'solutions/**/*.md',
            'solutions/**/*.mdx',
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
            'reference/**/*.md',
            'reference/**/*.mdx',
            'security/**/*.md',
            'security/**/*.mdx',
            'operations/**/*.md',
            'operations/**/*.mdx',
            'migration/**/*.md',
            'migration/**/*.mdx',
            'troubleshooting/**/*.md',
            'troubleshooting/**/*.mdx',
            'comparison/**/*.md',
            'comparison/**/*.mdx',
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
  plugins: [
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          // agent/ — placeholder pages with explicit migration targets
          {from: '/docs/agent/runtimes/codeact-custom-sandbox', to: '/docs/agent/codeact-custom-sandbox'},
          {from: '/docs/agent/runtimes/codeact-runtime', to: '/docs/agent/codeact-runtime'},
          {from: '/docs/agent/runtimes/minimal-react-agent', to: '/docs/agent/minimal-react-agent'},
          {from: '/docs/agent/runtimes/runtime-implementations', to: '/docs/agent/runtime-implementations'},
          {from: '/docs/agent/orchestration/stategraph', to: '/docs/agent/workflow-stategraph'},
          {from: '/docs/agent/orchestration/subagent-handoff', to: '/docs/agent/subagent-handoff-policy'},
          {from: '/docs/agent/orchestration/teams', to: '/docs/agent/agent-teams'},
          {from: '/docs/agent/orchestration/teams-api-reference', to: '/docs/agent/agent-teams-api-reference'},
          {from: '/docs/agent/observability/trace', to: '/docs/agent/trace-observability'},
          {from: '/docs/agent/coding-agent-acp-integration', to: '/docs/coding-agent/mcp-and-acp'},
          {from: '/docs/agent/coding-agent-cli', to: '/docs/coding-agent/cli-and-tui'},
          {from: '/docs/agent/coding-agent-command-reference', to: '/docs/coding-agent/command-reference'},
          {from: '/docs/agent/multi-provider-profiles', to: '/docs/coding-agent/provider-profiles'},
          {from: '/docs/agent/custom-agent-development', to: '/docs/agent/architecture'},
          {from: '/docs/agent/memory-management', to: '/docs/agent/memory-and-state'},

          // core-sdk/chat/ → core-sdk/model-access/ and core-sdk/memory/
          {from: '/docs/core-sdk/chat/stream', to: '/docs/core-sdk/model-access/streaming'},
          {from: '/docs/core-sdk/chat/non-stream', to: '/docs/core-sdk/model-access/chat'},
          {from: '/docs/core-sdk/chat/multimodal', to: '/docs/core-sdk/model-access/multimodal'},
          {from: '/docs/core-sdk/chat/tool-calling', to: '/docs/core-sdk/tools/function-calling'},
          {from: '/docs/core-sdk/chat/chat-memory', to: '/docs/core-sdk/memory/chat-memory'},
          {from: '/docs/core-sdk/chat/chat-memory-session-management', to: '/docs/core-sdk/memory/chat-memory'},

          // core-sdk/responses/ → core-sdk/model-access/
          {from: '/docs/core-sdk/responses/chat-vs-responses', to: '/docs/core-sdk/model-access/chat-vs-responses'},
          {from: '/docs/core-sdk/responses/non-stream', to: '/docs/core-sdk/model-access/responses'},
          {from: '/docs/core-sdk/responses/stream-events', to: '/docs/core-sdk/model-access/streaming'},

          // core-sdk/mcp/ → mcp/ (protocol-capabilities stays as canonical)
          {from: '/docs/core-sdk/mcp/overview', to: '/docs/mcp/overview'},
          {from: '/docs/core-sdk/mcp/client-integration', to: '/docs/mcp/client-integration'},
          {from: '/docs/core-sdk/mcp/transport-types', to: '/docs/mcp/transport-types'},
          {from: '/docs/core-sdk/mcp/gateway-and-multi-service', to: '/docs/mcp/gateway-management'},
          {from: '/docs/core-sdk/mcp/third-party-mcp-integration', to: '/docs/mcp/third-party-mcp-integration'},
          {from: '/docs/core-sdk/mcp/tool-exposure-semantics', to: '/docs/mcp/tool-exposure-semantics'},
          {from: '/docs/core-sdk/mcp/publish-your-mcp-server', to: '/docs/mcp/build-your-mcp-server'},
          {from: '/docs/core-sdk/mcp/positioning-and-when-to-use', to: '/docs/mcp/use-cases-and-paths'},

          // core-sdk/ misc → canonical subdirectories
          {from: '/docs/core-sdk/embedding', to: '/docs/core-sdk/search-and-rag/embedding'},
          {from: '/docs/core-sdk/pinecone-rag-workflow', to: '/docs/core-sdk/search-and-rag/overview'},
          {from: '/docs/core-sdk/searxng-enhancement', to: '/docs/core-sdk/search-and-rag/online-search'},
          {from: '/docs/core-sdk/spi-http-stack', to: '/docs/core-sdk/extension/spi-http-stack'},

          // ai-basics/ → core-sdk/ canonical (37 pages)
          {from: '/docs/ai-basics/overview', to: '/docs/core-sdk/overview'},
          {from: '/docs/ai-basics/architecture-and-package-map', to: '/docs/core-sdk/architecture-and-module-map'},
          {from: '/docs/ai-basics/unified-service-entry', to: '/docs/core-sdk/service-entry-and-registry'},
          {from: '/docs/ai-basics/service-factory-and-registry', to: '/docs/core-sdk/service-entry-and-registry'},
          {from: '/docs/ai-basics/request-and-response-conventions', to: '/docs/core-sdk/model-access/request-and-response-conventions'},
          {from: '/docs/ai-basics/platform-adaptation', to: '/docs/core-sdk/platform-service-matrix'},
          {from: '/docs/ai-basics/provider-and-model-extension', to: '/docs/core-sdk/extension/provider-extension'},
          {from: '/docs/ai-basics/memory-and-tool-boundaries', to: '/docs/core-sdk/memory/memory-and-tool-boundaries'},
          {from: '/docs/ai-basics/skills', to: '/docs/core-sdk/skills/overview'},
          {from: '/docs/ai-basics/chat/stream', to: '/docs/core-sdk/model-access/streaming'},
          {from: '/docs/ai-basics/chat/non-stream', to: '/docs/core-sdk/model-access/chat'},
          {from: '/docs/ai-basics/chat/multimodal', to: '/docs/core-sdk/model-access/multimodal'},
          {from: '/docs/ai-basics/chat/tool-calling', to: '/docs/core-sdk/tools/function-calling'},
          {from: '/docs/ai-basics/chat/chat-memory', to: '/docs/core-sdk/memory/chat-memory'},
          {from: '/docs/ai-basics/chat/chat-memory-session-management', to: '/docs/core-sdk/memory/chat-memory'},
          {from: '/docs/ai-basics/responses/chat-vs-responses', to: '/docs/core-sdk/model-access/chat-vs-responses'},
          {from: '/docs/ai-basics/responses/non-stream', to: '/docs/core-sdk/model-access/responses'},
          {from: '/docs/ai-basics/responses/stream-events', to: '/docs/core-sdk/model-access/streaming'},
          {from: '/docs/ai-basics/services/audio', to: '/docs/core-sdk/audio'},
          {from: '/docs/ai-basics/services/embedding', to: '/docs/core-sdk/search-and-rag/embedding'},
          {from: '/docs/ai-basics/services/image-generation', to: '/docs/core-sdk/image-generation'},
          {from: '/docs/ai-basics/services/realtime', to: '/docs/core-sdk/realtime'},
          {from: '/docs/ai-basics/services/rerank', to: '/docs/core-sdk/search-and-rag/rerank'},
          {from: '/docs/ai-basics/rag/overview', to: '/docs/core-sdk/search-and-rag/overview'},
          {from: '/docs/ai-basics/rag/architecture-and-indexing', to: '/docs/core-sdk/search-and-rag/overview'},
          {from: '/docs/ai-basics/rag/chunking-strategies', to: '/docs/core-sdk/search-and-rag/chunking-strategies'},
          {from: '/docs/ai-basics/rag/citations-trace-and-ui-integration', to: '/docs/core-sdk/search-and-rag/citations-and-trace'},
          {from: '/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow', to: '/docs/core-sdk/search-and-rag/hybrid-retrieval'},
          {from: '/docs/ai-basics/rag/ingestion-pipeline', to: '/docs/core-sdk/search-and-rag/ingestion-pipeline'},
          {from: '/docs/ai-basics/rag/pinecone-workflow', to: '/docs/solutions/pinecone-vector-workflow'},
          {from: '/docs/ai-basics/rag/vector-store-and-storage-backends', to: '/docs/core-sdk/search-and-rag/vector-store-and-backends'},
          {from: '/docs/ai-basics/online-search/overview', to: '/docs/core-sdk/search-and-rag/online-search'},
          {from: '/docs/ai-basics/online-search/searxng', to: '/docs/core-sdk/search-and-rag/online-search'},
          {from: '/docs/ai-basics/enhancements/overview', to: '/docs/core-sdk/extension/overview'},
          {from: '/docs/ai-basics/enhancements/pinecone-rag-workflow', to: '/docs/solutions/pinecone-vector-workflow'},
          {from: '/docs/ai-basics/enhancements/searxng-enhancement', to: '/docs/core-sdk/search-and-rag/online-search'},
          {from: '/docs/ai-basics/enhancements/spi-http-stack', to: '/docs/core-sdk/extension/spi-http-stack'},

          // getting-started/ → start-here/ and core-sdk/
          {from: '/docs/getting-started/installation', to: '/docs/start-here/quickstart-java'},
          {from: '/docs/getting-started/quickstart-openai-jdk8', to: '/docs/start-here/quickstart-java'},
          {from: '/docs/getting-started/quickstart-ollama', to: '/docs/start-here/quickstart-java'},
          {from: '/docs/getting-started/quickstart-springboot', to: '/docs/start-here/quickstart-spring-boot'},
          {from: '/docs/getting-started/chat-and-responses-guide', to: '/docs/core-sdk/model-access/chat-vs-responses'},
          {from: '/docs/getting-started/coding-agent-cli-quickstart', to: '/docs/coding-agent/quickstart'},
          {from: '/docs/getting-started/multimodal-and-function-call', to: '/docs/start-here/first-tool-call'},
          {from: '/docs/getting-started/platforms-and-service-matrix', to: '/docs/core-sdk/platform-service-matrix'},
          {from: '/docs/getting-started/troubleshooting', to: '/docs/start-here/troubleshooting'},
          {from: '/docs/getting-started/modules-and-maven-central', to: '/docs/core-sdk/architecture-and-module-map'},
          {from: '/docs/getting-started/spring-boot-autoconfiguration', to: '/docs/spring-boot/auto-configuration'},
          {from: '/docs/getting-started/version-compatibility', to: '/docs/reference/version-compatibility'},

          // guides/ → solutions/ (blog-migration-map retained as canonical reference)
          {from: '/docs/guides/deepseek-stream-search-rag', to: '/docs/solutions/deepseek-stream-search-rag'},
          {from: '/docs/guides/pinecone-vector-workflow', to: '/docs/solutions/pinecone-vector-workflow'},
          {from: '/docs/guides/rag-legal-assistant', to: '/docs/solutions/legal-assistant'},
          {from: '/docs/guides/searxng-web-search', to: '/docs/solutions/searxng-web-search'},
          {from: '/docs/guides/spi-dispatcher-connectionpool', to: '/docs/solutions/spi-dispatcher-connectionpool'},
          {from: '/docs/guides/springboot-jdbc-agent-memory', to: '/docs/solutions/springboot-jdbc-agent-memory'},
          {from: '/docs/guides/springboot-mysql-chat-memory', to: '/docs/solutions/springboot-mysql-chat-memory'},
          {from: '/docs/guides/flowgram-mysql-taskstore', to: '/docs/solutions/flowgram-mysql-taskstore'},
          {from: '/docs/guides/rag-ingestion-vector-store', to: '/docs/solutions/rag-ingestion-vector-store'},

          // coding-agent/ rename
          {from: '/docs/coding-agent/release-and-installation', to: '/docs/coding-agent/install-and-release'},

          // flowgram/ typo fix
          {from: '/docs/flowgram/builtin-nodes', to: '/docs/flowgram/built-in-nodes'},
        ],
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
            {label: 'Core SDK', to: '/docs/core-sdk/overview'},
            {label: 'MCP', to: '/docs/mcp/overview'},
            {label: 'Agent', to: '/docs/agent/overview'},
            {label: 'FlowGram', to: '/docs/flowgram/overview'},
            {label: '生产检查清单', to: '/docs/operations/production-checklist'},
          ],
        },
        {
          title: '资源',
          items: [
            {label: '版本与兼容性', to: '/docs/reference/version-compatibility'},
            {label: '安全边界', to: '/docs/security/overview'},
            {label: '选型对比', to: '/docs/comparison/overview'},
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
