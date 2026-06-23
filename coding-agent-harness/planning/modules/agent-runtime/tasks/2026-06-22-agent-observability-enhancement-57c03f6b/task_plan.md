# Agent observability enhancement

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-22-agent-observability-enhancement-57c03f6b/artifacts/preset/2026-06-22T03-46-24-009Z
Task Package Index: required

## 目标

统一 agent / coding / CLI / ACP 的 observability envelope，让 runId / sessionId / turnId / eventId 在真实运行和真实测试中可贯穿追踪。

## 范围

- 做什么：修复 correlation 链路、Langfuse 投影、CLI/ACP 会话事件写入与验证。
- 不做什么：不重构 memory 算法本身，不改 docs-site 内容，不扩大到无关模块。
- 主要风险：CLI/ACP 运行时重绑定后 runId 丢失、观测字段只停留在内存未归一化。

## 预算选择

选择预算：complex

选择理由：跨 `ai4j-agent`、`ai4j-coding`、`ai4j-cli` 三层，且需要真实测试和任务收口文件同步。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java` | 统一把 correlation 注入 prompt / model / tool / memory 事件 | coordinator / worker |
| C-002 | code | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/LangfuseTraceExporter.java` | Langfuse projection 需要保留 correlation 字段 | coordinator / reviewer / worker |
| C-003 | code | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/session/DefaultCodingSessionManager.java` | CLI/ACP 会话事件需要 runId 归一化 | coordinator / worker |
| C-004 | code | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java` | CLI 重绑会话时不能丢 runId | coordinator / worker |
| C-005 | code | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpJsonRpcServer.java` | ACP 事件写入链路同样需要一致字段 | coordinator / worker |

## 步骤

1. [步骤 1]
2. [步骤 2]
3. [步骤 3]
1. 审查现有观测链路并确认断点。
2. 修复 correlation 字段贯通与投影。
3. 运行真实 Maven 回归并收口 task package。

## 验收标准

- [ ] [标准 1]
- [ ] [标准 2]
- [ ] [标准 3]
- [x] runId/sessionId/turnId/eventId 在 agent / coding / CLI / ACP 真实链路中可追踪
- [x] Langfuse projection 保留 correlation 字段
- [x] 真实 Maven 回归通过

## 工作树（Worktree）

- 路径：[worktree 路径，例如 `.worktrees/feat/xxx`]
- 分支：[分支名]
- Worker owner：[coordinator / subagent id / 不适用]
- Worker handoff commit required：[yes / no / 不适用]
- Coordinator integration branch：[分支名 / 不适用]
- 未使用 worktree 的原因：[说明]
- 路径：不使用单独 worktree
- 分支：当前工作分支
- Worker owner：coordinator + subagent
- Worker handoff commit required：no
- Coordinator integration branch：n/a
- 未使用 worktree 的原因：任务切片较小，且已有现成 dirty 工作区，避免额外分叉

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：[已授权 / 未授权 / 不适用]
- Stop Condition 摘要：[一句话说明什么时候必须停]

## 审查判定

- 是否需要对抗性审查：是
- 若是，报告文件：`review.md`
- Reviewer：subagent
- No-finding 要求：reviewer 无重要发现

## 关联

- 相关 Regression Gate：`mvn -pl ai4j-agent,ai4j-coding,ai4j-cli -am "-Dtest=LangfuseTraceExporterTest,CodingSessionTest,DefaultCodingSessionManagerTest,CodeCommandTest,AcpCommandTest,AcpSlashCommandSupportTest,AppendOnlyTuiRuntimeTest,TuiSessionViewTest" -DskipTests=false -DfailIfNoTests=false test`
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：EXEC-01
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：n/a
- Harness Ledger update needed：`task_plan.md`, `review.md`, `walkthrough.md`, closed
- Closeout / Regression update needed：n/a

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
