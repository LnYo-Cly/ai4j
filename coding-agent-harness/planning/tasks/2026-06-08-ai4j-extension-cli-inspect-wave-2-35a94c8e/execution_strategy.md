# 执行策略：AI4J extension CLI inspect wave 2

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | not used |
| worker subagent | not authorized | n/a | coordinator decision | 2026-06-08 | cli-extension-inspect | main | not used |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 任务范围集中在 CLI inspect/read-only 输出；使用 self-review + CLI tests 足够，人工确认仍保留。 | 在 `review.md` 记录安全边界和残余。 |
| Would a worker subagent materially help? | no | CLI 命令、测试和治理文件会集中修改，拆 worker 会增加冲突。 | coordinator 在当前 checkout 内完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-08 | cli-extension-inspect | main | 单 coordinator 执行；不创建 worker worktree。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责实现、测试、治理同步和收口。 |
| Subagent 模式 | none | 不派 worker；review 使用任务内 self-review。 |
| 审查模型 | self-check + Confidence Challenge + human confirmation | 重点审查 inspect 是否误导用户以为 extension 已启用。 |
| Worktree 策略 | same checkout | 当前主 checkout 干净；任务无并行切片。 |
| 冲突控制 | coordinator owns shared files | CLI、POM、Regression、Cadence、task package 由 coordinator 统一同步。 |
| 证据深度 | L2 | CLI 消费新 API，需要 targeted CLI tests、模块测试、package smoke、harness status。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md`、`walkthrough.md` | 无 whitespace error；CRLF warning 可接受。 |
| L1 | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DskipTests=false test` | `progress.md`、`walkthrough.md` | extension CLI routing tests 通过。 |
| L1 | `mvn -pl ai4j-extension-api -DskipTests=false test` | `progress.md`、`walkthrough.md` | Wave 1 API 合同未回归。 |
| L2 | `mvn -pl ai4j-cli -am -DskipTests=false test` | `progress.md`、`walkthrough.md` | CLI host targeted module tests 通过。 |
| L2 | `mvn -DskipTests package` | `progress.md`、`walkthrough.md` | root reactor package smoke 通过。 |
| L2 | `npx.cmd --yes coding-agent-harness status --json .` | `progress.md`、`walkthrough.md` | harness status pass 或明确 residual。 |
| L3 | 不适用 | `review.md` | 本轮不做 live provider、install、runtime jar、真实第三方发布。 |

## 暂停 / 升级条件

- 需要把 `extension enable` 写入配置或改变 runtime tool exposure。
- `inspect` 必须执行不可信第三方代码才能展示用户需要的信息。
- CLI adapter 需要修改 agent/coding runtime 行为。
- L1/L2 回归失败且无法在当前范围内修复。
