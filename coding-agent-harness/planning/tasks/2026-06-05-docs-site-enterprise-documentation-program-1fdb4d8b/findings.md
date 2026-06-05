# docs-site 文档重构总任务 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001: docs-site 的主要问题是 canonical 路径未收口

- 背景：上一轮入口和模块定位已经改进，但用户指出 docs-site 质量仍不足，要求把每个特色功能讲清楚。
- 发现：三个只读审计都指向同一问题：内容量不少，但 `getting-started/`、`ai-basics/`、`guides/`、`core-sdk/mcp/`、Agent/Coding/FlowGram 迁移页造成路径分裂。
- 影响：本轮先做 documentation map 和 sidebar 正式入口，不直接删除旧页。
- 后续：后续可逐步把旧目录强内容迁到 canonical 深页，再给旧页加 legacy notice。

### F-002: 顶层 MCP 应作为正式主线

- 背景：Start Here / FAQ / Glossary 曾指向 `core-sdk/mcp/*`，sidebar 又主要挂 `mcp/*`。
- 发现：Core SDK 审计建议以 `docs-site/docs/mcp/*` 作为正式 MCP 文档，`core-sdk/mcp/*` 只保留过渡或深层参考。
- 影响：本轮修正 Start Here、FAQ、Glossary、Core overview 的 MCP 入口到 `/docs/mcp/overview`。
- 后续：迁移 `core-sdk/mcp/*` 中独有细节，完成后给旧页加 notice。

### F-003: 主入口页过度像源码解读

- 背景：Agent、Coding Agent、FlowGram 总览页原先大量展开内部类和执行链。
- 发现：这些页面对源码读者有价值，但新用户第一屏更需要“是什么、适合谁、怎么开始、边界/限制是什么”。
- 影响：本轮重写 Core、Agent、Coding Agent、FlowGram 总览页，把用户路径放前面，源码对象变成参考阅读。
- 后续：继续逐页重写 quickstart、configuration、architecture、solutions 时保持同一结构。

### F-004: 生产接入辅助页缺失

- 背景：用户希望 docs-site 更像成熟 AI SDK 项目文档，而不是只像博客和源码注释。
- 发现：原站缺少独立版本兼容、发布 artifact、安全边界、生产检查、迁移、排障、选型对比入口。
- 影响：本轮新增 `reference/`、`security/`、`operations/`、`migration/`、`troubleshooting/`、`comparison/` 页面。
- 后续：后续深页应链接这些辅助页，避免每个模块重复写同一类上线注意事项。

### F-005: `.gitignore` 会隐藏新增 docs 文件

- 背景：`git status --short` 不显示新增 docs-site docs 文件。
- 发现：`git status --ignored=matching` 显示新增目录为 `!!`，说明需要 `git add -f` 纳入本地提交。
- 影响：提交前必须显式强制添加新增 docs 文件，避免只提交 sidebar 链接而漏掉目标页面。
- 后续：最终 git status 和 commit diff 必须确认新增页面已纳入。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Legacy 目录处理 | 保留，不删除 | `ai-basics/` 和 `guides/` 仍有强内容，直接删除会损失细节 | 立即删除旧目录 | accepted |
| MCP 正式路径 | 顶层 `mcp/` | sidebar 和审计结果都支持顶层 MCP 作为正式主线 | 继续混用 `core-sdk/mcp/` | accepted |
| 生产辅助页命名 | 使用 Reference / Security / Operations / Migration / Troubleshooting / Comparison | 专业但不生硬，避免营销式表达 | 使用生硬的采用层命名 | accepted |
| 总览页写法 | 用户路径优先，源码对象后置 | 更适合大众用户和项目接入 | 保留源码链路长文 | accepted |
| 并行方式 | subagent 只读审计，coordinator 串行写入 | 避免 sidebar 和入口页冲突 | 多 worker 同时写同一导航 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否删除或隐藏 legacy 目录 | 本轮不删，只声明 canonical map | human + coordinator | 后续 legacy notice wave |
| 是否继续逐页合并 `ai-basics` 强内容 | 需要，但不阻塞本轮构建通过 | coordinator | 下一批 docs-site 深页任务 |
