# Documentation Site 模块

## 模块 Key

`docs-site`

## 创建日期

2026-06-04

## 一句话结果

维护 `docs-site/` 的 Docusaurus 文档站点和文档构建 surface。

## 完成后能得到什么

该模块让 docs site 内容、导航和构建配置从 Java 模块中分离。涉及 Docusaurus 配置、站点页面、文档站构建或前端展示文档时，任务应落到 `docs-site`，同时确认业务文档 SSoT 是否需要同步。

## 交付物

- 可见产物：Docusaurus 页面、配置、静态站点构建结果。
- 负责范围：`docs-site/`
- 验证证据：在 `docs-site/` 运行 `npm run build`。

## 第一眼应该看什么

先读 `module_plan.md`，再读 `docs-site/` 的 package/config 和受影响 docs。

## 模块职责

负责文档站点呈现，不替代 repo 根 `docs/` 下的 harness/reference SSoT。

## 边界

- 负责：`docs-site/**`
- 共享面：根 `docs/**`、README、版本发布说明。
- 不负责：Java 生产代码、harness governance 文档的事实来源。

## 完成判断

- docs-site 任务明确区分展示层和 SSoT。
- 构建或变更验证记录在任务 progress。
- 影响 docs SSoT 时由 coordinator 同步。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
