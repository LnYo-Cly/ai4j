# CLI memory compact command UX - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 现有 compact/checkpoint 命令已存在

- 背景：路线图建议做 “CLI `/sandbox` + `/memory` + `/compact` UX”，需要先确认当前缺口，避免重复实现昨晚已经落地的能力。
- 发现：`SlashCommandController` 已注册 `/compact`、`/compacts`、`/checkpoint`；`CodingCliSessionRunner` 已有 `/compacts`、`/checkpoint`、`/compact` dispatch 和渲染；`AcpSlashCommandSupport` 已暴露 `compacts` 与 `checkpoint`；docs-site 命令参考也已有 `/compact`、`/compacts`、`/checkpoint`。
- 影响：本任务应收窄为新增 `/memory` 一等诊断入口，并对齐已有 compact/checkpoint 命令，而不是重做 compact。
- 后续：实现时先加 `/memory`，只在必要时微调 `/compact` 输出。

### `/status` 和 `/session` 已有 memory 字段，但不是用户友好的一等入口

- 背景：用户想要类似 Codex/Claude Code 的清晰交互体验，memory/compact 是长会话稳定性的关键。
- 发现：`CodingCliSessionRunner.printStatus` 已输出 `memory`、`tokens`、`checkpointGoal`、`compact`；`printCurrentSession` 已输出 memory、tokens、checkpoint、compact token 变化。但用户需要知道 memory/compact 健康状态时，需要在多个命令中拼信息。
- 影响：`/memory` 应复用现有 snapshot 字段，提供面向用户的摘要，而不是新增底层能力。
- 后续：设计 `renderMemoryOutput`，把 `/status` 和 `/session` 中相关字段聚合成稳定摘要。

### raw memory 不能作为默认输出

- 背景：memory 可能包含用户 prompt、工具输出、路径、业务数据或未来的模型上下文摘要。
- 发现：当前 docs 和工程标准都要求不提交/不打印 secrets；CLI 命令也应避免默认泄露 raw memory。
- 影响：`/memory` 只能输出 item count、token estimate、checkpoint goal、compact 摘要和安全提示，不能列出原始 memory items。
- 后续：review 必须检查输出中没有 raw prompt/tool output。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| `/memory` 定位 | 状态诊断命令 | 补齐用户可见入口，不改变 compact 算法 | 增强 `/status` 或新增 memory editor | accepted |
| 子命令 | 只支持 `/memory` 与 `/memory status` 别名 | MVP 简洁，未来可扩展但现在不制造复杂树 | `/memory inspect/list/clear` | accepted |
| 输出内容 | 摘要字段 + 安全提示 | 有用且不泄露 raw context | 打印 memory items | accepted |
| 底层依赖 | 优先复用 `CodingSessionSnapshot` | 避免跨模块 API 膨胀 | 修改 `ai4j-coding` public API | accepted-with-stop-condition |
| 实现位置 | `ai4j-cli` 主导，docs-site 同步 | CLI host owns terminal/ACP/session command UX | 放到 `ai4j-agent` | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否必须展示 auto-compact breaker open/closed | 若现有 snapshot/export state 可取则展示，否则 `unknown` 或省略 | coordinator | 实现 `renderMemoryOutput` 时 |
| 是否更新 Regression SSoT | 如果新增 `/memory` 作为固定 slash command parity gate，应更新 | coordinator | 实现 diff 完成前 |
| 是否需要 reviewer subagent | 实现完成后建议做 read-only reviewer | coordinator | task-review 前 |
