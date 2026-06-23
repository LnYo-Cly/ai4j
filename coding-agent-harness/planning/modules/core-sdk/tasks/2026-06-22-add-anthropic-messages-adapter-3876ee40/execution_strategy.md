# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | optional | read-only | harness task policy | 2026-06-22 | current task review | n/a | allowed within this task |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | optional | 单 provider 适配器，coordinator self-review + 真实测试可覆盖；如用户要求再起只读 review。 | 执行后按需决定。 |
| Would a worker subagent materially help? | no | 单模块、约 500–1000 行，coordinator 直接写更可控。 | coordinator 单线执行。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 本任务执行 | authorized | user | 2026-06-22 | `ai4j` 新增 anthropic provider | same checkout | 用户已确认「开这个任务」。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 单线实现 + 收口。 |
| 实现路线 | 手写适配器（纯 Java + fastjson2） | 与现有 12 家 provider 同构；不引入官方 Kotlin SDK。 |
| 协议范围（首轮） | chat / stream / tool_use / system | thinking 字段作为可选透传，不强制。 |
| baseUrl 策略 | AnthropicConfig 可配 apiHost（默认 `https://api.anthropic.com/`） | coding-plan 用户改 baseUrl 指向 `open.bigmodel.cn/api/anthropic`。 |
| 鉴权 | `x-api-key` + `anthropic-version: 2023-06-01` 头 | Anthropic 线协议标准。 |
| 审查模型 | adversarial self-review | 确认映射不失真、流式事件不丢、现有回归不破。 |
| Worktree 策略 | same checkout | 单模块，dirty 工作区已存在。 |
| 证据深度 | L1 / L2 / L3 | 单测 + 真实 Maven 回归 + live 烟测 + review 收口。 |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | 静态检查 / diff review | `progress.md` | 映射断点已识别 |
| L1 | 单元测试（请求/响应/流映射，离线） | `progress.md` | `AnthropicChatServiceTest` 通过 |
| L2 | 真实 Maven 回归 | `progress.md` | `mvn -pl ai4j -DskipTests=false test` 全绿 |
| L2+ | live 烟测（coding-plan key / api.anthropic） | `progress.md` | live test 返回真实内容 |
| L3 | review / walkthrough | `review.md` 与 `walkthrough.md` | 无阻塞发现并完成 closeout |

## 暂停 / 升级条件

- Anthropic → `ChatCompletionResponse` 映射出现内容/工具调用丢失。
- SSE 事件类型解析不全导致流式丢字。
- 现有 103 测试出现回归。
- 必须引入 Kotlin/Jackson 才能继续（违背「手写、零新依赖」原则）。
- 任务范围溢出到 agent/coding/cli 层或 docs-site。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | core-sdk |
| Module Plan | coding-agent-harness/planning/modules/core-sdk/module_plan.md |

Keep shared module decisions in the module plan. Keep task-specific evidence in this task directory.
