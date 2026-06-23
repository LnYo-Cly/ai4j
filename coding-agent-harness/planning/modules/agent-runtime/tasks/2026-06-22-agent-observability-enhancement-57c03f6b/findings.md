# Agent observability enhancement - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 观测链路收口

- 背景：需要把 agent / coding / cli 的观测字段收成可追踪的统一信封。
- 发现：`runId/sessionId/turnId/eventId` 已在 agent runtime、session snapshot/store、Langfuse projection、CLI/ACP session event path 贯通。
- 影响：任务可以依赖真实链路做日志、trace、session 事件关联，而不是只看局部表面字段。
- 后续：如需要跨进程恢复 run 级历史，可继续把 runId 持久化策略扩展到更完整的 session store metadata。

### CodeAct runtime prompt override 风险

- 背景：`BaseAgentRuntime` 新增了带 correlation 的 8 参数 `buildPrompt(...)`。
- 发现：`CodeActRuntime` 原本只覆盖旧签名，导致 CodeAct 执行路径落回 base prompt builder，系统提示丢失 CodeAct runtime instructions。
- 处理：补充 8 参数 override，并让旧签名委托新签名；同时 `projectItems(...)` 继续传递 runId/sessionId/turnId。
- 验证：`AgentBlueprintFactoryTest#shouldMapCodeActWorkflowToCodeActRuntime` 通过。

### Auto-continue hidden instructions 边界

- 背景：coding loop 的 auto-continue 应该是内部续跑，不应制造新的用户消息。
- 发现：如果把 continuation prompt 写入 `CodingAgentRequest.input`，会污染 memory，使 user message 数量增加。
- 处理：`CodingAgentLoopController.turnRequest(...)` 保持原 request/null，续跑提示仅通过 `hiddenInstructions` 合并到 prompt instructions。
- 验证：`CodingAgentLoopControllerTest.shouldContinueWithHiddenInstructionsWithoutAddingExtraUserMessage` 通过。

### clearSandbox 幂等缺口

- 背景：CLI `markSandboxClosed(...)` 在 disable sandbox 时先 `updateSandboxStatus(CLOSED)` 再 `clearSandbox()`；若会话本就无 sandbox 绑定（或被重复 disable），`clearSandbox()` 仍会无条件追加 `SANDBOX_CLEARED` 事件（payload=null）并 touch metadata。
- 发现：`AgentSession.updateSandboxStatus(...)` 已有 `sandboxBinding == null` 早返回，但 `clearSandbox()` 缺少同样守卫，导致幂等性不对称。
- 处理：给 `clearSandbox()` 增加 `if (sandboxBinding == null) return this;` 早返回，与 `updateSandboxStatus` 对齐；`CodingSession.clearSandbox()` 经 delegate 委托自动获得同一幂等性。
- 验证：`AgentSessionSandboxBindingTest#clearSandboxShouldBeIdempotentAndNoopWithoutBinding`、`CodingSessionTest#sandboxFacadeShouldDelegateToUnderlyingSessionAndStayIdempotent` 通过。

### correlation 字段测试覆盖缺口

- 背景：observability 信封新增了 `runId/traceId/turnEventId/eventId`，但 handoff/team/team-message 三个 `*SessionEventSupport` 的测试只覆盖 `supports/buildSummary/buildPayload`，没有覆盖 `toSessionEvent(...)` 的 correlation 字段透传；headless runtime 也只断言事件类型，未断言 correlation 字段。
- 发现：上述路径缺少"correlation 字段确实从 AgentEvent 透传到 SessionEvent"的回归保护。
- 处理：在 3 个 support test 各加 `toSessionEventShouldPreserveCorrelationFields`；在 `HeadlessCodingSessionRuntimeTest` 加 `shouldPropagateCorrelationFieldsOnSessionEvents`，断言落库事件 runId/sessionId/eventId 非空。
- 验证：targeted + 宽模块回归 BUILD SUCCESS。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| correlation envelope | 统一使用 runId/sessionId/turnId/eventId | 保持跨层一致，便于 trace/session/event 互相对齐 | 只在单层补字段 | accepted |
| Langfuse projection | 保留 correlation 字段 | 避免在 exporter 投影时丢失调试锚点 | 只输出 Langfuse 标准字段 | accepted |
| CLI session event normalization | appendEvent 统一补齐 runId/traceId/turnEventId | 防止 CLI/ACP 的历史事件继续出现 null | 让上层每次手动塞字段 | accepted |
| continuation prompt | 作为 hidden instructions，不作为 input | 避免内部续跑污染 user memory | 写成新 user message | accepted |
| clearSandbox 幂等 | 无绑定时早返回 no-op，与 updateSandboxStatus 对齐 | 避免重复 disable 产生重复 SANDBOX_CLEARED 事件 | 始终追加事件但 payload=null | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否要把 runId 持久化进完整 session store metadata | 当前不是阻塞项；核心 snapshot/store/resume 链路已保留 runId | coordinator | 后续如需要跨进程 run 级恢复时再确认 |
| 是否补 docs-site 的可观测文档 | 本任务未覆盖 | coordinator | 单独 docs-site 任务处理 |
