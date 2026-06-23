# 收口记录：Agent observability enhancement

## 摘要

本任务完成 agent / coding / cli 三层观测链路收口：统一 runId / sessionId / turnId / eventId，session snapshot/store/resume 保留 runId，CLI/ACP 会话事件可追踪，Langfuse projection 保留 correlation 字段。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、`ai4j-coding`、`ai4j-cli` |
| 新增文件 | `coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-22-agent-observability-enhancement-57c03f6b/design.md` |
| 删除文件 | 无 |
| 不在范围内 | docs-site、其他未触及模块 |

## 关键修复

| 问题 | 修复 | 结果 |
| --- | --- | --- |
| CodeAct runtime 未覆盖新增 8 参数 `buildPrompt(...)` | 在 `CodeActRuntime` 补充 correlation-aware override，并传递 runId/sessionId/turnId 到 `projectItems(...)` | CodeAct runtime instructions 不再丢失 |
| auto-continue continuation prompt 污染 user memory | `CodingAgentLoopController` 不再把 hidden continuation prompt 写入 `CodingAgentRequest.input` | 内部续跑不会增加 user message |
| session restore/run correlation 断链 | snapshot/store/resume、runtime request metadata、coding result aggregate 保留 runId/sessionId/turnId | trace/session/event 可关联 |
| sandbox 事件无法进入 trace span 类型 | 新增 `TraceSpanType.SANDBOX` 并桥接 sandbox bound/updated/cleared 事件 | sandbox 状态变化可观测 |
| `clearSandbox()` 非幂等 | 无 sandbox 绑定时早返回 no-op，与 `updateSandboxStatus` null-guard 对齐 | 重复 disable 不再产生重复 `SANDBOX_CLEARED` 事件 |
| correlation 字段透传缺测试 | 6 个测试类各补 1 个 correlation/idempotency 回归 | runId/sessionId/turnId/eventId/traceId/turnEventId 透传有回归保护 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| CodeAct 回归 | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintFactoryTest#shouldMapCodeActWorkflowToCodeActRuntime" -DskipTests=false -DfailIfNoTests=false test` | PASS | BUILD SUCCESS |
| Coding loop 回归 | `mvn -pl ai4j-coding -am "-Dtest=CodingAgentLoopControllerTest" -DskipTests=false -DfailIfNoTests=false test` | PASS | 6 tests, BUILD SUCCESS |
| targeted correlation/sandbox 回归 | `mvn -pl ai4j-agent,ai4j-coding,ai4j-cli -am "-Dtest=AgentSessionSandboxBindingTest,CodingSessionTest,HeadlessCodingSessionRuntimeTest,AgentHandoffSessionEventSupportTest,AgentTeamSessionEventSupportTest,AgentTeamMessageSessionEventSupportTest,CodingCliSessionRunnerSandboxTest" -DskipTests=false -DfailIfNoTests=false test` | PASS | 33 tests, 0 failures |
| 宽模块回归 | `mvn -pl ai4j-agent,ai4j-coding,ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test` | PASS | extension-api 25、ai4j 103、agent 127、coding 63、cli 302 tests |
| Diff hygiene | `git diff --check` | PASS | 无 whitespace error；仅 Windows 行尾提示 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review + real regression | 无阻塞发现 | 通过 | `review.md` |
| reviewer subagent | 未返回 | 不作为完成证据 | `wait_agent` timeout |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| docs-site 尚未补充本轮可观测说明 | coordinator | yes | 单独文档任务处理 |
| reviewer 子 agent 未返回 | coordinator | yes | 如用户要求，再开独立只读 review pass |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否沉淀共享 lesson？ | 否；这是任务内实现细节，暂不推广为共享规范 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 发现记录 | `findings.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
