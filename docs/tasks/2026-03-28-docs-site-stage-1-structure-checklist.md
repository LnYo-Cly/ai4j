# docs-site 第一阶段实施清单

日期：2026-03-28

## 目标

第一阶段只做两类事情：

1. 收敛站点结构，减少用户进入官网后的路径混乱；
2. 先补最影响使用体验的空缺专题和参考页。

本阶段不追求把所有内容一次写完，而是先让官网具备稳定导航、正式专题和可继续扩展的骨架。

---

## 范围

### 1. 结构收敛

- [x] 明确 `coding-agent` 为唯一正式 Coding Agent 专题
- [x] 将旧 `agent/coding-agent-*` 页面中的有效内容迁移到新目录
- [x] 为 Flowgram 建立正式顶级栏目
- [x] 在站点配置中纳入 `flowgram/**/*`
- [x] 更新导航、首页入口、footer、intro

### 2. 首批补全文档

- [x] `Flowgram / 总览`
- [x] `Flowgram / 快速开始`
- [x] `Flowgram / API 与运行时`
- [x] `Flowgram / 内置节点`
- [x] `Flowgram / 自定义节点扩展`
- [x] `Coding Agent / 命令参考` 从清单页升级为详解页

### 3. 暂不处理

- [ ] `ai-basics` 与 `core-sdk` 的彻底合并
- [ ] 所有旧文档的删除或归档
- [ ] Flowgram 前端节点 schema 全文档
- [ ] Release / 安装体系文档

这些内容放到后续阶段处理。

---

## 本阶段设计原则

- 不引入第三套路径；
- 有内容再上导航，不挂空页；
- 页面优先回答“怎么用”和“怎么扩展”；
- 示例以现有源码、测试和 demo 为准；
- 旧页先迁移内容，再决定是否下线。

---

## 文件级变更清单

### 站点结构

- [x] `docs-site/sidebars.ts`
- [x] `docs-site/docusaurus.config.ts`
- [x] `docs-site/docs/intro.md`

### 新增 Flowgram 文档

- [x] `docs-site/docs/flowgram/overview.md`
- [x] `docs-site/docs/flowgram/quickstart.md`
- [x] `docs-site/docs/flowgram/api-and-runtime.md`
- [x] `docs-site/docs/flowgram/builtin-nodes.md`
- [x] `docs-site/docs/flowgram/custom-node-extension.md`

### 升级参考页

- [x] `docs-site/docs/coding-agent/command-reference.md`

### 路线与任务文档

- [x] `docs/plans/2026-03-28-docs-site-roadmap-design.md`
- [x] `docs/tasks/2026-03-28-docs-site-stage-1-structure-checklist.md`

---

## 验收标准

- [x] Flowgram 在官网里成为正式栏目
- [x] `Coding Agent` 命令页可直接查实际用法，而不是只有命令列表
- [x] 站点构建通过
- [x] 新增页面与实现一致
- [x] 用户从首页/intro/侧边栏都能发现新专题
