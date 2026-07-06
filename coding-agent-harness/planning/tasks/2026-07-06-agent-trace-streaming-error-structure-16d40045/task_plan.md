# agent trace streaming error structure

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在 `ai4j-agent` 中把流式模型文本与最终 raw response 分开记录，并让 `TOOL_ERROR` 默认携带 `errorType`，同时同步 docs-site 示例和本地验证。

## 范围

- 做什么：`NodeIoRecord` 增加 `outputText`，`IoCaptureAgentListener` 累积流式文本，`JsonlIoCaptureSink` round-trip 该字段，`NodeReplayer` mock replay 优先 raw response，`BaseAgentRuntime` 的 `TOOL_ERROR` 增加 `errorType`，并更新相关单测与 docs-site。
- 不做什么：live provider smoke、新的抽象层、其它 agent runtime 行为、RAG/检索改造。
- 主要风险：streaming / non-streaming 事件语义不一致、JSONL 反序列化兼容性、docs 示例与实际输出形态不一致。

## 预算选择

选择预算：simple

选择理由：单个 runtime 模块 + 少量 docs/测试，属于可在本地 deterministic 验证的窄改动。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/replay/NodeIoRecord.java | replay record 需要新增 `outputText` 字段 | coordinator |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/replay/IoCaptureAgentListener.java | 流式模型消息累积逻辑在这里 | coordinator |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java | `TOOL_ERROR` 结构在这里生成 | coordinator |
| C-004 | docs | TARGET:docs-site/docs/agent/replay-recovery-audit.md | replay 行为说明需要同步 | coordinator |

## 步骤

1. 补 replay capture / replay 数据结构与 listener 流式累积。
2. 补 `TOOL_ERROR` 的 `errorType`，并更新测试。
3. 更新 docs-site 说明、运行验证、收口 task 包。

## 验收标准

- [ ] `NodeIoCaptureReplayTest` 覆盖流式输出累积、JSONL round-trip、mock replay fallback。
- [ ] `ExtensionAgentToolsTest` 验证 `TOOL_ERROR` 带 `errorType`，且默认无 stack trace。
- [ ] `mvn -pl ai4j-agent -am -DskipTests=false test`、`npm run build` in `docs-site/`、`mvn -DskipTests package` 均通过。

## 工作树（Worktree）

- 路径：`.worktrees/fix/agent-observability-trace`
- 分支：`fix/agent-observability-trace`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`origin/main`
- 未使用 worktree 的原因：n/a

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：n/a
- 连续执行权限：不适用
- Stop Condition 摘要：若 streaming/raw response contract 与现有事件模型冲突，立即停下来确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：n/a
- Reviewer：不适用
- No-finding 要求：不适用

## 关联

- 相关 Regression Gate：RG-002、RG-007、RG-008
- 审查报告：不适用
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：不适用
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`
