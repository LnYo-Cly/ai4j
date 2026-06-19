# P0-A AgentSession runtime container - Findings

## 研究发现

### F-001 AgentSession 当前太薄
- 背景：路线图要求 session 成为长程 Agent 任务容器。
- 发现：原 `AgentSession` 仅持有 `runtime + context`，没有 session id、metadata、event log、snapshot/store。
- 影响：需要新增最小容器基础，但不能把 sandbox/compact 等远期能力一次塞入。

### F-002 Event log 可通过 session-scoped publisher 接入
- 背景：runtime 通过 `AgentEventPublisher` 发布事件。
- 发现：`Agent.newSession()` 可以从 base publisher 复制 listener，再追加 event log listener；无需改 runtime loop。
- 影响：实现风险低，保留 trace listener 兼容。

### F-003 Memory restore 需要接口合同
- 背景：snapshot/restore 需要统一 memory 入口。
- 发现：`InMemoryAgentMemory` 和 `JdbcAgentMemory` 已有 `snapshot()/restore(...)`，接口缺少默认方法。
- 影响：在 `AgentMemory` 增加 default best-effort 方法，已有主实现继续覆盖精确保留 summary。

### F-004 docs-site 新文档被 `.gitignore` 命中
- 背景：新增 `docs-site/docs/agent/session-runtime.md`。
- 发现：根 `.gitignore` 的 `docs/` 规则会忽略该路径。
- 影响：提交时必须 `git add -f docs-site/docs/agent/session-runtime.md`。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 |
|------|------|------|----------|
| Session store | 新增 `AgentSessionStore` SPI + in-memory 实现 | 最小可恢复合同，生产实现留给使用者 | 直接绑定 JDBC/Redis，过早 |
| Event log | `AgentSessionEventLog` + session scoped publisher | 不侵入 runtime loop | 在 BaseAgentRuntime 内硬编码 session，破坏边界 |
| Builder API | `AgentBuilder.sessionStore(...)` | 保持现有 builder 风格 | 构造器-only，不易用 |
| Docs | 新增 `agent/session-runtime` 页面 | 让技术细节独立于 roadmap | 只改 roadmap，细节不足 |
