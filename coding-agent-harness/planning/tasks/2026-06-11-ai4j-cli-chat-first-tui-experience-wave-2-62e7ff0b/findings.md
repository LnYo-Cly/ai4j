# AI4J CLI Chat First TUI Experience Wave 2 - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### TUI 体验边界

- 背景：用户希望 CLI/TUI 体验靠近 Codex / Claude Code / Pi，但已明确不替换 JLine、不引入 Ink、不做 dashboard。
- 发现：现有 `TuiSessionView`、`JlineShellTerminalIO`、`CliThemeStyler` 已能承载单屏 chat-first 展示，只缺 provider/protocol 上下文和 slash palette 可扫读分类。
- 影响：本轮采用窄切片增强 header/status/palette，不改命令语义、不扩张 runtime。
- 后续：人工确认时可做一次真实终端 smoke；若发现配色或宽度问题，另开 TUI polish 任务。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 渲染层 | 继续使用 JLine + 现有 `TuiSessionView` 字符串渲染 | 符合用户确认的边界，成本低，风险可由现有 JUnit 覆盖 | Ink / 自研渲染层 / dashboard | accepted |
| 状态上下文 | provider/protocol/model/workspace 同时进入 TUI header 和 JLine status line | 两条终端路径都能直接看清当前运行上下文 | 仅在 `/status` 查看 | accepted |
| Slash palette | 保持 inline palette，仅增加分类提示 | 保留 chat-first 单屏体验，同时提升 `/provider`、`/model`、`/extensions`、`/extension` 可发现性 | 多面板命令中心 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 真实终端配色、宽度和按键手感是否符合预期 | 本地 JUnit 已覆盖字符串合同，但未替代人工 smoke | human / coordinator | 人工确认前 |
