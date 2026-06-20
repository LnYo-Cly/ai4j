# CLI TUI status context bar - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 现有 TUI 已有 renderer 基座，不需要重写

- 背景：用户希望 AI4J CLI/TUI 更接近 Codex/Claude/OpenCode/Pi，但担心自研 renderer 成本过大。
- 发现：`ai4j-cli` 已有 `TuiSessionView`、`TuiScreenModel`、`TuiInteractionState`、ANSI/append-only runtime 和 TUI tests。
- 影响：本切片只增强 header/context row，不引入 Ink 或新 renderer。
- 后续：更复杂布局等基础体验稳定后再做。

### 状态栏应围绕现有能力呈现

- 背景：最新 `dev` 已有 `/memory`、`/compact`、`/sandbox`、`/permissions` 等命令和 session state。
- 发现：用户进入 TUI 时仍需要一眼知道当前上下文，不应每次手动查询。
- 影响：第二行 context chips 使用已有 snapshot/descriptor/interaction state；为展示 `/sandbox attach` 结果，`TuiRenderContext` 增加了非敏感 `sandboxSummary` 投影，不改 AgentSession public API。
- 后续：后续可扩展 provider latency、MCP、process 等，但本切片不做。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| TUI 路线 | 增强 JLine/TuiSessionView | 符合 Java 维护成本 | 引入 Ink/Node | accepted |
| 布局 | 双行 header：identity + context chips | 信息清楚且小改动 | 全屏多 pane renderer | accepted |
| 数据来源 | 使用 descriptor/snapshot/approval state，并在 TUI context 中投影非敏感 sandbox summary | 不改 AgentSession public API，也不暴露 secret | 新增跨模块 runtime DTO | accepted |
| docs | 更新 CLI/TUI 页面 | 用户能理解状态栏 | 只写 task package | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 全屏分区布局 | 本切片不做；先用 header/context row 提升可读性 | coordinator | 后续 TUI layout task |
| TUI render plugin | 暂缓，先稳定 command/plugin 体系 | coordinator | 后续插件/TUI 扩展任务 |
