# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | none | coordinator decision | 2026-06-20 | no worker write scope; coordinator owns schema/accessor/CLI/docs slice | `.worktrees/feature/agent-blueprint-schema-export` / `feature/agent-blueprint-schema-export` | not needed |

## Subagent Delegation Decision

任务开始时，coordinator 必须根据用户目标主动做这个判断，即使用户完全没有提到 subagent。
不要假设用户知道 subagent 或 worker 是什么。如果分工有帮助，用白话说明收益，并向用户申请一次授权。
可以直接对用户说 subagent 或 worker subagent；关键规则是 agent 不能等用户主动提出 subagent。
如果任务已经明显拆成互不重叠的独立切片，implementation 前就应判断为 `ask-user`。如果还不知道精确文件路径，先确认路径，然后立刻申请独立执行助手授权。

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本任务是窄范围 schema/accessor/CLI/docs 切片，已用 targeted tests、docs build、CLI smoke 和 self confidence challenge 覆盖；后续 PR 仍可由 GitHub/human review 补充。 | 不启动只读 subagent；保留 self review 证据。 |
| Would a worker subagent materially help? | no | 改动集中在一个 feature worktree 内，涉及 task package、`ai4j-agent`、`ai4j-cli`、docs-site 的一致性，拆 worker 会增加协调成本。 | coordinator 单线程实现并提交。 |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | no worker write scope; coordinator owns schema/accessor/CLI/docs narrow slice | `.worktrees/feature/agent-blueprint-schema-export` / `feature/agent-blueprint-schema-export` | 单一 worktree 内完成，避免拆分导致 API/docs 漂移。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 本任务无需并行 worker；self review 足以进入 PR。 |
| 审查模型 | self-check + predefined verifier | 通过 JUnit targeted tests、docs typecheck/build、CLI package smoke、Harness status 验证。 |
| Worktree 策略 | dedicated worktree | 实现发生在 `.worktrees/feature/agent-blueprint-schema-export`，base 为 `origin/dev`。 |
| 冲突控制 | coordinator owns shared files | task package、docs-site 和模块代码均由 coordinator 一次性提交，避免跨 worker 冲突。 |
| 证据深度 | L1/L2 | Java/CLI targeted tests 属于 L1；docs build 和 CLI package smoke 属于 L2。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`; sensitive-token filename scan | `progress.md` | 无 whitespace error；无用户 provider token 落盘文件 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintLoaderValidatorTest,AgentBlueprintFactoryTest,AgentBlueprintSchemasTest" -DskipTests=false -DfailIfNoTests=false test`; `mvn -pl ai4j-cli -am "-Dtest=AgentBlueprintCommandTest,AgentBlueprintRunCommandTest,Ai4jCliTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | targeted tests 全部通过 |
| L2 | `npm run typecheck`; `npm run build`; `mvn -pl ai4j-cli -am -DskipTests package`; `Ai4jCliMain blueprint schema` smoke | `progress.md`, `review.md`, `walkthrough.md` | docs 编译通过，CLI 包可打印 schema JSON |
| L3 | PR review / CI after push | PR checks and follow-up progress | PR 到 `dev` 后检查通过或记录修复 |

## 暂停 / 升级条件

- 需要发布或声明远端 schema URL 已可访问。
- 需要 live provider token、真实模型调用或外部 sandbox provider。
- 改动超出 Blueprint authoring aid，开始改变 runtime DTO / public factory 行为。
- docs-site 示例需要写尚不存在的 API。
- targeted tests 或 docs build 失败且无法在本任务范围内修复。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
