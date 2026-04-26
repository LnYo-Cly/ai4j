import React from 'react';
import Link from '@docusaurus/Link';
import Heading from '@theme/Heading';

export default function NotFoundContent(): React.ReactElement {
  return (
    <main className="container margin-vert--xl">
      <div className="row">
        <div className="col col--8 col--offset-2 text--center">
          <Heading as="h1">页面未找到</Heading>
          <p>你访问的地址不存在，可能是链接已变更或部署路径配置不一致。</p>
          <p>
            请先返回 <Link to="/">首页</Link>，或进入 <Link to="/docs/intro">文档首页</Link> 继续浏览。
          </p>
        </div>
      </div>
    </main>
  );
}
