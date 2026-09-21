---
sidebar_position: 5
title: Agent 会话运行时
description: 讲解 AgentSession 长程运行态容器：sessionId、独立 memory、event log、snapshot/restore 与 AgentSessionStore，以及它与 memory/compact、Coding Agent/CLI 的边界和生产实现建议。
tags: [concept]
---

# Agent 会话运行时
`AgentSession` 是 `ai4j-agent` 的长程运行态入口。它不是另一个模型客户端，也不是 CLI 会话的替代品，而是把一次 Agent 任务的状态收拢到一个可保存、可恢复、可观测的容器里。

## 1. 它解决什么问题

普通 `agent.run(...)` 适合一次性调用：宿主把请求交给 runtime，拿到 `AgentResult` 后结束。

`agent.newSession()` 适合长程任务：

- 每个 session 有稳定 `sessionId`。
- 每个 session 有独立 memory。
- runtime 事件会进入 session event log。
- session 可以生成 snapshot。
- 配置了 `AgentSessionStore` 后可以保存和恢复。
- 旧的 `Agent.run(...)` 语义保持不变。

这让上层产品可以把 Agent 当作一个可持续运行的任务容器，而不是一串散落的模型请求。

## 2. 当前最小能力

P0-A 先落地基础容器，不一次性塞入 sandbox、artifact、fork、rewind 等远期能力。

当前最小结构是：

```text
Agent
  ├─ run(...)                 // 兼容的一次性运行入口
  └─ newSession()
       └─ AgentSession
            ├─ sessionId / metadata
            ├─ independent AgentMemory
            ├─ AgentSessionEventLog
            ├─ snapshot / restore
            └─ optional AgentSessionStore
```

相关类：

| 类 | 职责 |
| --- | --- |
| `AgentSession` | 面向使用者的 session 运行入口 |
| `AgentSessionMetadata` | `sessionId`、创建/更新时间和业务 attributes |
| `AgentSessionEventLog` | session 内 runtime 事件日志接口 |
| `InMemoryAgentSessionEventLog` | 默认内存事件日志实现 |
| `AgentSessionSnapshot` | metadata、memory、events 的可携带快照 |
| `AgentSessionStore` | 保存、读取、删除和列出 session snapshot |
| `InMemoryAgentSessionStore` | 进程内 store，适合测试和轻量 demo |

## 3. 最小用法

```java
Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .memorySupplier(InMemoryAgentMemory::new)
    .build();

AgentSession session = agent.newSession();
session.putMetadata("project", "demo");

AgentResult result = session.run("先分析这个问题");

System.out.println(session.getSessionId());
System.out.println(result.getOutputText());
System.out.println(session.getEventLog().getEvents().size());
```

:::warning memorySupplier 必须返回独立实例
重点是 `memorySupplier(...)`：每次 `newSession()` 都应该拿到独立 memory。否则多个 session 可能共享同一个 memory 实例。
:::

## 4. 保存和恢复

如果需要让 session 跨请求或跨进程边界恢复，给 Agent 配置 `AgentSessionStore`。

```java
InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .memorySupplier(InMemoryAgentMemory::new)
    .sessionStore(store)
    .build();

AgentSession session = agent.newSession();
session.run("记住这次任务上下文");
session.save();

AgentSession resumed = agent.resumeSession(session.getSessionId());
```

:::warning InMemoryAgentSessionStore 不适用于生产
`InMemoryAgentSessionStore` 只适合本地测试和 demo。生产环境应实现自己的 `AgentSessionStore`，例如 JDBC、Redis、对象存储或业务自有 session 表。
:::

## 5. Snapshot 包含什么

`session.snapshot()` 会生成：

| 字段 | 内容 |
| --- | --- |
| metadata | session id、created/updated 时间、attributes |
| memory | `MemorySnapshot`，由当前 `AgentMemory` 提供 |
| events | `AgentSessionEvent` 列表 |

snapshot 会做防御性复制。读取 snapshot 后修改返回对象，不应该反向污染当前 session。

## 6. Event Log 和 Trace 的关系

`AgentSessionEventLog` 记录的是 session 内发生过的 runtime events，例如：

- `STEP_START`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`
- `STEP_END`
- `ERROR`

它和 trace 不是同一个东西：

| 能力 | 主要用途 |
| --- | --- |
| Event Log | 恢复、调试、产品 UI 时间线、session 事实历史 |
| Trace | 可观测性、外部 tracing/exporter、性能和调用链分析 |

session 创建时会从基础 `AgentEventPublisher` 复制已有 listener，并额外挂载 event log listener。因此已有 trace listener 不会因为使用 session 而失效。

## 7. 与 Memory / Compact 的边界

P0-A 先提供 session 容器边界；P0-B 已补上基础 compact/context projection 能力。

推荐心智是：

```text
SessionEventLog = 完整事件历史
AgentMemory     = 可投喂模型的状态和历史
Compact         = 把 event / memory / artifact 投影成更短的工作上下文
```

P0-B 已补上：

- `CompactPolicy`
- `CompactResult`
- `ContextProjector`
- `ContextBudget`
- `ContextReport`
- `AgentSession.compact(...)`
- `AgentSessionSnapshot.compactResult`

使用细节见 [记忆压缩与上下文投影器](/docs/agent/memory/memory-compact-context)。

## 8. 会话事件契约（每次 memory 变更必有事件）

会话事件日志不只是观测面——它是会话状态的可重建记录。runtime 对 memory 的每一次写入都会同步发布一条事件，因此把事件流按顺序折叠（fold）就能重建出与 `memory.snapshot()` 完全一致的 `items + summary`。

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe
  src={useBaseUrl('/archify/session-event-contract.html')}
  title="会话事件契约 · 交互式架构图"
  style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}}
/>

<a href={useBaseUrl('/archify/session-event-contract.html')} target="_blank" rel="noopener noreferrer">在新窗口打开全屏大图</a>（含双主题、缩放与导出功能；右上角视角切换可分别聚焦写点事件化、restore 统一漏斗、投影与一致性）。

变更语义事件一览：

| 事件 | 触发点 | payload |
| --- | --- | --- |
| `USER_INPUT` | `memory.addUserInput` | 原始输入对象 |
| `MEMORY_ITEMS_APPENDED` | `memory.addOutputItems` | 追加的 item 列表 |
| `TOOL_RESULT` | `memory.addToolOutput`（复用既有事件） | `AgentToolResult` |
| `TOOL_OUTPUT_REPLACED` | `session.replaceToolOutput` 异步补丁 | `{callId, output}` |
| `MEMORY_COMPRESS` | `session.compact` / autoCompact | `CompactResult`（含压缩后 memory 快照） |
| `SESSION_RESTORED` | `session.restore(snapshot)`——resume、fork、harness 快照补丁的统一入口 | 安装的 `MemorySnapshot` 基线 |

配套投影器 `SessionEventProjector`：`deriveSnapshot(events)` / `deriveItems(events)` / `deriveSummary(events)` 把事件流折叠回 memory 状态，可用于审计、fork、离线分析与一致性校验（`SessionEventConsistencyTest` 断言投影==快照）。

注意两点：

- `MEMORY_COMPRESS` 有两种 payload：携带 `CompactResult` 的是真正的 memory 重写；携带投影报告的是请求级 `ContextProjection`（只影响当次 prompt，不改 memory），投影器按 payload 类型区分。
- 内存实现内部的压缩器（`InMemoryAgentMemory.setCompressor`）在 `add*` 内部重写，不单独发事件——该路径不在事件契约内。

## 9. 与 Coding Agent / CLI 的关系

`AgentSession` 是通用 SDK 层能力。

`ai4j-coding` 和 `ai4j-cli` 后续可以在它之上继续绑定：

- workspace
- file/shell/git/browser tool state
- approvals
- checkpoint
- compact
- sandbox
- TUI session timeline

但这些不应该反向污染 `ai4j-agent` 的通用运行时。SDK 层只保留通用会话、事件、memory、snapshot 和 store 合同。

## 10. 生产实现建议

生产侧实现 `AgentSessionStore` 时建议注意：

- session id 必须按租户或项目隔离。
- snapshot 中不要保存明文 provider token。
- event payload 可能包含 prompt、工具参数和模型输出，必要时应脱敏。
- store 写入应该由业务决定同步或异步，不要让每个事件都强制阻塞主 loop。
- 如果 memory 或 event 很大，应结合 compact / retention policy。

## 11. 下一步

P0-A 只是运行态容器基础。完整 Agent SDK 还会继续推进：

1. Plugin lifecycle hooks
2. YAML Agent Blueprint
3. Sandbox SPI
4. Coding Agent sandbox routing
5. CLI `/sandbox` 体验
6. 远端 Agent Runner

完整路线见 [AI4J Agent SDK 路线图](/docs/reference/about/sdk-roadmap)。
