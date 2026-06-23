# Agent observability enhancement - 进度

## 状态：审查中

## 进度记录

### [2026-06-22 13:31] - execution

- 做了什么：补齐 runId/sessionId/turnId/eventId 的观测链路，修复 Langfuse projection 对 correlation 字段的保留，并把 CLI/ACP 会话事件归一化到 runId 可追踪。
- 验证结果：窄回归通过。
- 证据：command:G:\My_Project\java\ai4j-sdk:窄回归通过

### [2026-06-22 16:23] - regression fix

- 做了什么：修复 `CodeActRuntime` 在新增 correlation-aware `buildPrompt(...)` 后未覆盖 8 参数签名的问题。否则 CodeAct 运行时会落回 `BaseAgentRuntime` prompt builder，丢失 `You are a CodeAct agent` runtime instructions。
- 验证结果：`mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintFactoryTest#shouldMapCodeActWorkflowToCodeActRuntime" -DskipTests=false -DfailIfNoTests=false test` 通过，BUILD SUCCESS。
- 证据：command:G:\My_Project\java\ai4j-sdk:CodeAct blueprint regression passed

### [2026-06-22 16:26] - regression fix

- 做了什么：修复 auto-continue hidden instructions 被当作新 `CodingAgentRequest.input` 的问题；续跑提示只通过 hidden instructions 合并到 model instructions，不再额外写入 user memory。
- 验证结果：`mvn -pl ai4j-coding -am "-Dtest=CodingAgentLoopControllerTest" -DskipTests=false -DfailIfNoTests=false test` 通过，6 tests，BUILD SUCCESS。
- 证据：command:G:\My_Project\java\ai4j-sdk:CodingAgentLoopControllerTest passed

### [2026-06-22 16:28] - wide regression

- 做了什么：运行 agent/coding/cli 宽模块回归，覆盖依赖模块、agent 全量测试、coding 全量测试、cli 全量测试。
- 验证结果：`mvn -pl ai4j-agent,ai4j-coding,ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test` 通过，BUILD SUCCESS。
- 摘要：ai4j-extension-api 25 tests、ai4j 103 tests、ai4j-agent 126 tests、ai4j-coding 62 tests、ai4j-cli 298 tests，全部 0 failures / 0 errors / 0 skipped。
- 证据：command:G:\My_Project\java\ai4j-sdk:wide agent/coding/cli regression passed

### [2026-06-22 16:31] - diff hygiene

- 做了什么：运行 `git diff --check`。
- 验证结果：无空白错误；仅输出 Windows LF→CRLF 工作区提示。
- 证据：command:G:\My_Project\java\ai4j-sdk:diff check no whitespace errors

### [2026-06-22 17:10] - hardening: clearSandbox idempotency + correlation test coverage

- 做了什么：
  - 修复 `AgentSession.clearSandbox()` 幂等：当无 sandbox 绑定时直接返回（no-op），与 `updateSandboxStatus` 的 null-guard 一致，避免重复 disable/`markSandboxClosed` 产生重复 `SANDBOX_CLEARED` 事件。
  - 补齐 correlation/sandbox 回归测试覆盖（6 个测试类各 +1）：
    - `AgentSessionSandboxBindingTest#clearSandboxShouldBeIdempotentAndNoopWithoutBinding`
    - `CodingSessionTest#sandboxFacadeShouldDelegateToUnderlyingSessionAndStayIdempotent`
    - `HeadlessCodingSessionRuntimeTest#shouldPropagateCorrelationFieldsOnSessionEvents`
    - `AgentHandoffSessionEventSupportTest#toSessionEventShouldPreserveCorrelationFields`
    - `AgentTeamSessionEventSupportTest#toSessionEventShouldPreserveCorrelationFields`
    - `AgentTeamMessageSessionEventSupportTest#toSessionEventShouldPreserveCorrelationFields`
- 验证结果：
  - targeted 回归通过（33 tests，0 failures）：`mvn -pl ai4j-agent,ai4j-coding,ai4j-cli -am "-Dtest=AgentSessionSandboxBindingTest,CodingSessionTest,HeadlessCodingSessionRuntimeTest,AgentHandoffSessionEventSupportTest,AgentTeamSessionEventSupportTest,AgentTeamMessageSessionEventSupportTest,CodingCliSessionRunnerSandboxTest" -DskipTests=false -DfailIfNoTests=false test`
  - 宽模块回归 BUILD SUCCESS：extension-api 25、ai4j 103、agent 127（+1）、coding 63（+1）、cli 302（+4），全部 0 failures / 0 errors / 0 skipped。
  - `git diff --check` 无 whitespace error。
- 证据：command:G:\My_Project\java\ai4j-sdk:targeted + wide regression BUILD SUCCESS, diff check clean

## 残余

- reviewer 子 agent `019eee4c-5a30-7b12-a145-089c3af2096c` 多次等待超时，未作为完成证据采纳。
- 本轮未更新 docs-site；当前任务范围聚焦 runtime/session/trace/CLI/ACP 可观测链路。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：n/a
- Harness Ledger update needed：closeout pending after review confirmation
- 负责人：coordinator
