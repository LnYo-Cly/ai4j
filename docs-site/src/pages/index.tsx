import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';

const quickRoutes = [
  {
    title: '开始调用模型',
    description: '从 Maven 依赖、配置和第一段 Java 调用开始。',
    to: '/docs/getting-started/quickstart-java',
  },
  {
    title: '接入 Agent',
    description: '了解运行时、工具、权限、状态与观测边界。',
    to: '/docs/agent/overview',
  },
  {
    title: '使用 Skills',
    description: '按需发现工作说明，并保持读取权限受限。',
    to: '/docs/agent/skills',
  },
  {
    title: '连接 MCP',
    description: '选择 transport，或从 Streamable HTTP 服务接入。',
    to: '/docs/capabilities/mcp/streamable-http',
  },
  {
    title: '查 Java API',
    description: '按已发布模块和版本打开渲染后的 Javadoc。',
    to: '/docs/reference/api',
  },
];

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link className="button button--secondary button--lg" to="/docs/intro">
            开始阅读文档
          </Link>
          <Link className="button button--info button--lg margin-left--md" to="/docs/products/coding-agent/overview">
            查看 Coding Agent
          </Link>
        </div>
      </div>
    </header>
  );
}

function QuickNavigation() {
  return (
    <section className={styles.quickRoutes} aria-labelledby="quick-routes-heading">
      <div className="container">
        <Heading as="h2" id="quick-routes-heading">按任务进入</Heading>
        <p className={styles.quickRoutesIntro}>
          从一个明确入口开始；导航栏搜索可用于直接定位类型、配置项和示例。
        </p>
        <div className={styles.quickRouteGrid}>
          {quickRoutes.map((route) => (
            <Link className={styles.quickRoute} key={route.to} to={route.to}>
              <Heading as="h3">{route.title}</Heading>
              <p>{route.description}</p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title}`}
      description="AI4J 官方文档：JDK8 友好的 Java 大模型 SDK、Coding Agent、MCP 与 Agent 架构。">
      <HomepageHeader />
      <main>
        <QuickNavigation />
        <HomepageFeatures />
      </main>
    </Layout>
  );
}

