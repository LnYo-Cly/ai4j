# 节点级 I/O 捕获 + 重放 — 设计

## 关键事实（已查证）

runtime 已经把完整 I/O 发到事件总线，带全 correlation：
- BaseAgentRuntime:289 `MODEL_REQUEST` payload = 完整 `AgentPrompt`
- BaseAgentRuntime:340/349 `MODEL_RESPONSE` payload = `result.getRawResponse()`
- BaseAgentRuntime:129 `TOOL_CALL` payload = `AgentToolCall`
- BaseAgentRuntime:143/163 `TOOL_RESULT` payload = toolResult
- AgentEvent 已带 runId/sessionId/turnId/step

⇒ **不碰 runtime，只加消费者 + 重放器。**

## 本任务（Phase 1）交付

1. `NodeIoRecord`（DTO）：{runId, turnId, step, nodeType, nodeId, inputs, outputs, modelId, capturedAt, mode}。
2. `IoCaptureSink`（AgentListener）：订阅 MODEL_REQUEST/RESPONSE、TOOL_CALL/RESULT，按 runId+turnId+step 配对组装 NodeIoRecord，写 sink。
   - `InMemoryIoCaptureSink`（测试）
   - `JsonlIoCaptureSink`（durable，每行一个 NodeIoRecord JSON）
3. `NodeReplayer`：读 NodeIoRecord 重放
   - MODEL：用捕获 AgentPrompt 真实再调 AgentModelClient（live）；或返回捕获 result（deterministic/mock）
   - TOOL：重执行工具，或 mock

## 验证（真实 LLM）

GLM coding-plan key 经 `.anthropicMessages(...)`（本会话已验证可用）跑真实 turn（带 echo 工具）→ JSONL 落盘 → 重放 MODEL 节点（真实再调 GLM）+ TOOL 节点 → 断言完整。

## 边界

只做捕获 sink + 重放。不动 runtime 事件发射。幂等/防篡改/JDBC store = Phase 2-4。

## 后续阶段（独立任务）

- P2：幂等键 + 副作用跳过（鲁棒失败恢复）
- P3：durable snapshot store JDBC（大规模断点续跑）
- P4：哈希链防篡改 + 决策审计 trail（合规审计回溯）
