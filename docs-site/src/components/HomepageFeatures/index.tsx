import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  Svg: React.ComponentType<React.ComponentProps<'svg'>>;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'JDK8 友好落地',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        为存量 Java 系统准备的工程化路径：从最小示例到生产化治理，不要求先升级技术栈。
      </>
    ),
  },
  {
    title: 'MCP 全链路能力',
    Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
    description: (
      <>
        覆盖 MCP Client、MCP Server、Gateway 与动态服务管理，支持“传什么工具就用什么工具”。
      </>
    ),
  },
  {
    title: 'Coding Agent 交付入口',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        内置 CLI、TUI、ACP 三种交付入口，同时可继续扩展到 CodeAct、SubAgent、StateGraph 与 Agent Teams。
      </>
    ),
  },
];

function Feature({title, Svg, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
