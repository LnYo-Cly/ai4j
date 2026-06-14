# AI4J CLI TUI extension projection - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### [发现主题 1] 现有 extension CLI 已经足够作为 TUI 投影源

- 背景：需要把 extension 能力投影进 TUI，但不想重复实现解析、验证和资源读取逻辑。
- 发现：`CliExtensionCommand` 已经提供 `list / inspect / plan / check / validate / run / resource`，并且输出语义完整，适合作为 TUI 的唯一执行源。
- 影响：TUI 只需要做薄适配，不需要再写一套 extension executor。
- 后续：无。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| extension TUI 执行路径 | `CodingCliSessionRunner` 复用 `CliExtensionCommand`，仅加输出捕获层 | 保证 CLI 与 TUI 行为一致，避免双实现漂移 | 在 runner 里重新实现 extension 解析 | accepted |
| extension 入口呈现 | 在 slash 补全、帮助和命令面板里同时投影 `/extensions` 与 `/extension ...` | 让发现路径和执行路径一致 | 只在某一个界面暴露入口 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要把 extension id 也做成 TUI completion supplier | 目前先不做，手动输入 id 足够完成第一波投影 | coordinator | 下一轮如果扩展数量上来再补 |
