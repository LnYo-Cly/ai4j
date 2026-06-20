# AI4J Extension Recipe and Plugin Composition UX - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 插件能力已具备，缺少使用者组合层

- 背景：F-039 已完成显式资源授权和 `extension plan`，但使用者仍需要在多个页面之间拼接 Java、Spring Boot 和 CLI 接入路径。
- 发现：`plugin-packages.md` 解释了 discover / enable / expose / allow 语义，`ask-user-plugin.md` 给出官方样板插件资源名，`ExtensionRegistry`、`AiExtensionProperties` 和 `CliExtensionCommand` 已支持 recipe 所需配置。
- 影响：本任务应补 docs-site recipe 层，而不是新增运行时 API。
- 后续：新增 `plugin-recipes.md` 并接入 sidebar。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 新增使用者 recipe 页面 | accepted | 现有页面偏概念和作者 cookbook，缺少“我怎么组装”的连续路径 | 在 `plugin-packages.md` 继续加长；容易让页面过载 | accepted |
| 不改 Java 行为 | accepted | 现有 `ExtensionRegistry` / CLI / Spring 配置已经能支撑 recipe | 新增 recipe DSL；超出本轮且会扩大 API 面 | accepted |
| 多插件组合逐个 plan | accepted | CLI `extension plan` 当前按单个插件输出 activation state，逐个检查更贴合现有实现 | 暗示有全局组合 plan；当前不存在 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 无 | 当前不需要额外用户确认 | coordinator | closeout |
