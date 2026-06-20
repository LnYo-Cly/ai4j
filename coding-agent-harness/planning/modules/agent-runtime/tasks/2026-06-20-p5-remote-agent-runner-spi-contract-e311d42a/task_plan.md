# P5 Remote Agent Runner SPI contract

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Task Package Index: required
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p5-remote-agent-runner-spi-contract-e311d42a/artifacts/preset/2026-06-20T05-41-54-401Z

## 目标

新增 `ai4j-agent` 的 Remote Agent Runner SPI contract，支持后续第三方或业务方把完整 Agent loop 运行到远端 sandbox / hosted workspace 中。

## 范围

- 做什么：`ai4j-agent` 新增 runner SPI + DTO；新增 deterministic fake runner tests；更新 docs-site 技术页和 roadmap。
- 不做什么：不新增 `ai4j-agent-runner` Maven 模块；不接真实云服务；不使用任何 token；不实现 CLI/TUI runner UX；不改变 sandbox SPI 或本地 Agent 行为。
- 主要风险：contract 过大或和 Sandbox SPI 混淆；本任务保持最小合同，区别 Host-driven sandbox tools 与 Remote Agent Runner。

## 预算选择

选择预算：complex

选择理由：这是 public-ish SDK contract + docs-site + regression 的跨面任务，需要完整 task package、fake tests 和后续 residual 记录。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | Java 8、模块边界、Harness 流程、docs-site 验证规则 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox | P2 Sandbox SPI 风格和边界 | implementer / reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint | Blueprint 与 RunnerSpec 的关系 | implementer / reviewer |
| C-004 | docs | TARGET:docs-site/docs/agent/sandbox-spi.md | 文档结构、边界和示例风格 | implementer / reviewer |
| C-005 | plan | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-final-roadmap-and-task-plan-2026-06-20.md | P5 contract-first 路线和不要做事项 | coordinator / reviewer |

## 步骤

1. 诊断现有 sandbox/blueprint/session 代码风格。
2. 设计最小 runner contract：provider/session/spec/request/result/event/status/exception/listener。
3. 实现 Java 8 DTO/SPI，保持 defensive copy 和 checked exception 风格。
4. 添加 fake runner tests，覆盖 run、stream、cancel、close、artifact、event 和 defensive copy。
5. 更新 docs-site：新增 Remote Agent Runner SPI 页，sidebar 和 Agent roadmap 可达。
6. 运行 targeted agent tests、broad agent tests、docs-site build、diff check、Harness status。
7. 提交、推送、创建 PR。

## 验收标准

- [x] `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runner` 存在最小 SPI contract。
- [x] `AgentRunnerSpiContractTest` 覆盖 fake provider/session/result/events/defensive copies。
- [x] docs-site 新增 `agent/remote-agent-runner-spi.md` 并挂到 sidebar。
- [ ] targeted regression 通过。
- [ ] broad `ai4j-agent` regression 通过。
- [ ] docs-site build 通过。
- [ ] Harness status 通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\agent-runner-spi`
- 分支：`feature/agent-runner-spi`
- Worker owner：coordinator
- Worker handoff commit required：是
- Coordinator integration branch：`dev`
- 未使用 worktree 的原因：不适用；本任务已使用 dedicated worktree。

## 审查判定

- 是否需要对抗性审查：self review + PR review。
- Reviewer：coordinator / CI / human reviewer。
- No-finding 要求：不能存在真实云依赖、secret 泄露、Java 8 破坏、Sandbox SPI 混淆或 docs 与代码不一致。

## 关联

- 相关 Regression Gate：RG-004 / agent runtime；RG-008 / docs-site build。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI 同步。
- 前置任务：P2 Sandbox SPI、P3 coding sandbox routing、P4 CLI sandbox commands。

## 模块关联

- Module：agent-runtime
- Step：P5-RUNNER-SPI-CONTRACT
- Module Plan：TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md
