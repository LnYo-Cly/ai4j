# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | optional | read-only | harness task policy | 2026-06-23 | current task review | n/a | allowed within this task |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 单线抽取 + 实现。 |
| 工作树 | `.worktrees/feature/anthropic-native-surface` | 主工作区有无关未提交改动，按 worktree-standard 隔离。 |
| 抽取策略 | 原生服务在下、统一适配器委托 | 依赖方向正确，零传输重复。 |
| thinking | reasoningContent(响应) / onReasoningDelta(流) | 与 agent 层 AgentModelResult.reasoningText 对齐，为 P1.5 铺路。 |
| 异常 | 类型化 AnthropicApiException | 原生调用方可精确 catch；统一路径仍映射 CommonException。 |

## 证据计划

| 层级 | 计划 | 完成条件 |
| --- | --- | --- |
| L1 | 原生 create/stream/tool/thinking 单测 | `AnthropicMessagesServiceTest` 通过 |
| L2 | `mvn -pl ai4j -DskipTests=false test` | 全绿（含 P0 的 116） |
| L2+ | live 烟测（IMessagesService 原生路径 × coding-plan key） | 返回内容 + thinking |
| L3 | review + walkthrough | 无阻塞发现 |

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | core-sdk |
| Module Plan | coding-agent-harness/planning/modules/core-sdk/module_plan.md |
