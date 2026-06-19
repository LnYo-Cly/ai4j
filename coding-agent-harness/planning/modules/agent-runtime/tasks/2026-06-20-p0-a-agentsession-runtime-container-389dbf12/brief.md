# P0-A AgentSession runtime container - Brief

## 任务摘要

本任务实现 AI4J Agent SDK 路线图中的 P0-A：把 `AgentSession` 从薄包装升级为可保存、可恢复、可观测的运行态容器基础。

## 关键交付

- `io.github.lnyocly.ai4j.agent.session` 包：metadata、event log、snapshot、store、in-memory 实现。
- `Agent.newSession()`：创建 session-scoped event publisher，复制 base listeners，并把 runtime events 写入 session event log。
- `AgentSession`：session id、metadata、event log、snapshot/restore/save。
- `AgentBuilder.sessionStore(...)` 与 `Agent.resumeSession(...)`。
- `AgentMemory.snapshot()/restore(...)` 默认合同，现有 `InMemoryAgentMemory` / `JdbcAgentMemory` 继续提供精确保留 summary 的实现。
- JUnit4 回归：`AgentSessionRuntimeContainerTest`。
- docs-site：`agent/session-runtime` 页面和 roadmap 状态更新。

## 非目标

- 不实现 compact/context projector。
- 不实现 sandbox/provider SPI。
- 不实现 fork/rewind/checkpoint artifact。
- 不新增远端 runner。
- 不使用用户提供的 provider token 做 live test。

## 成功判定

本任务成功的证据是：owner-module tests 证明 session 隔离、事件日志、snapshot/store/resume 和防御性复制成立；docs-site build 证明技术文档可发布；Harness status 证明任务材料可审查。
