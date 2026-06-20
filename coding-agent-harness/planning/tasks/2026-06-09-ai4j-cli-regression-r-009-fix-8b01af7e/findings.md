# AI4J CLI Regression R-009 Fix - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### ACP loop-control 事件不属于 assistant 正文

- 背景：R-009 的 ACP 流式 chunk 测试发现 `Stopped after the assistant completed the current task turn.` 被追加到 `agent_message_chunk`。
- 发现：该文本来自 loop-control `AUTO_STOP` session event，不是模型正文；`session/prompt` response 已通过 `stopReason` 表达 turn 结束。
- 影响：ACP 输出层应抑制 `AUTO_CONTINUE` / `AUTO_STOP` / `BLOCKED` 到 assistant content chunk 的映射，避免客户端把状态文案拼进模型输出。
- 后续：若未来需要 ACP 状态事件，应单独设计协议字段，不在本轮修复中发明新字段。

### JLine multiline transcript 允许 ANSI-styled printAbove

- 背景：JLine 失败的 surefire XML 显示 actual 视觉文本与 expected 一致，但包含 ANSI 样式序列。
- 发现：终端渲染路径可以给 transcript line 添加主题样式；测试目标是确认 reading 状态使用 `printAbove` 且空行用空格占位。
- 影响：测试应断言 `AttributedString.fromAnsi(...).toString()` 后的视觉文本，避免把样式能力误判为行为回归。
- 后续：无。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| ACP loop-control mapping | return `null` instead of `agent_message_chunk` | stop/block/auto-continue 是运行控制状态，不是模型正文；response `stopReason` 已覆盖 turn 完成 | 新增未验证的 ACP 状态 update 字段 | accepted |
| JLine assertion | assert ANSI-stripped visual text | 保留真实终端样式，同时固定 printAbove 视觉输出合同 | 禁用样式或要求 raw string 完全无 ANSI | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要修 R-008 | 不属于本任务；已在 Regression SSoT open residual 保留 | project coordinator | 后续 R-008 修复任务 |
