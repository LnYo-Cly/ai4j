# P0-A AgentSession runtime container

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-a-agentsession-runtime-container-389dbf12/artifacts/preset/2026-06-19T18-02-05-025Z
Task Package Index: required

## 目标

在 `ai4j-agent` 中落地 P0-A 的 `AgentSession` 运行态容器基础，使 session 拥有稳定 id、metadata、独立 memory、事件日志、snapshot/store/resume 能力，同时保持既有 `Agent.run(...)` 行为兼容。

## 范围

- 做什么：新增 session 基础 DTO/SPI、为 `Agent.newSession()` 创建独立 event log 和 memory、支持 `AgentSession.snapshot()/restore()/save()`、支持 `AgentBuilder.sessionStore(...)` 和 `Agent.resumeSession(...)`、补 owner-module 回归测试、更新 docs-site 技术文档。
- 不做什么：不实现 compact policy/context projector，不实现 sandbox SPI，不新增远端 runner Maven 模块，不改 CLI `/sandbox`，不引入真实 provider/live token 测试。
- 主要风险：Session 容器过度扩张会污染通用 Agent SDK；事件 payload 可能包含敏感信息，文档必须提示生产 store 脱敏；默认 `AgentMemory.restore(...)` 对自定义 memory 的 summary 仅 best-effort，主要实现类已覆盖精确保留。

## 预算选择

选择预算：complex

选择理由：本任务是 agent-runtime 模块的公共 API/状态容器变更，同时触及 docs-site 和回归证据，需要完整 task package、参考设计、review 与 walkthrough。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java | 当前 session 入口和兼容 API | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/Agent.java | `newSession()`、`resumeSession()` 和 base listener 克隆位置 | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory | memory snapshot/restore 合同 | coordinator / reviewer |
| C-004 | docs | TARGET:docs-site/docs/agent/sdk-roadmap.md | P0-A 路线图状态投影 | coordinator / reviewer |
| C-005 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-a-agentsession-runtime-container-389dbf12/references/agent-session-runtime-container-design.md | 本任务设计边界和后续不做项 | coordinator / reviewer / future worker |

## 步骤

1. 诊断 `AgentSession`、`Agent`、`AgentBuilder`、memory、event publisher 的现状和兼容边界。
2. 新增 session 包：metadata、event log、snapshot、store、in-memory 实现。
3. 改造 `Agent.newSession()`，使每个 session 拥有独立 memory 和 session-scoped event publisher；新增 restore/resume/save API。
4. 补 `AgentSessionRuntimeContainerTest` 覆盖 session 隔离、event log、snapshot/restore、store resume、防御性复制。
5. 更新 docs-site Agent Session Runtime 技术文档和 roadmap 链接。
6. 跑 targeted/broad Maven、docs-site build、Harness status，并提交/PR。

## 验收标准

- [x] `AgentSession` 暴露 session id、metadata、event log、snapshot/restore/save 基础能力。
- [x] `AgentBuilder.sessionStore(...)` 和 `Agent.resumeSession(...)` 可用。
- [x] `Agent.run(...)` 一次性运行入口不改变语义。
- [x] 新增 deterministic JUnit4 测试覆盖核心合同。
- [x] docs-site 有 Agent Session Runtime 技术文档，并从 sidebar/roadmap 接入。
- [x] `mvn -pl ai4j-agent -am -DskipTests=false test` 通过。
- [x] `npm run build` in `docs-site/` 通过。
- [x] `npx --yes coding-agent-harness status --json .` 无 failure。

## 工作树（Worktree）

- 路径：TARGET:.worktrees/feature/agent-session-runtime-container
- 分支：feature/agent-session-runtime-container
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：main
- 未使用 worktree 的原因：不适用，已按要求创建 feature worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：用户已授权连续推进本系列任务，但本 P0-A 单任务不需要 long-running contract。
- Stop Condition 摘要：P0-A 代码、docs、回归和 PR 合并完成后停止；P0-B/P0-C 另开任务继续。

## 审查判定

- 是否需要对抗性审查：是，self review
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：无阻塞 API/兼容/测试/文档发现；若存在则先修复或记录 residual。

## 关联

- 相关 Regression Gate：RG-002 agent runtime module；RG-008 docs-site build
- 审查报告：TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-a-agentsession-runtime-container-389dbf12/review.md
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：TASKS/2026-06-20-ai4j-agent-sdk-implementation-decomposition-and-26846add

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：T-P0-A-AGENTSESSION-RUNTIME-CONTAINER-389DBF12
- Module Plan：TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime step active -> review/done after PR
- Harness Ledger update needed：task plan path, review path, walkthrough path after closeout
- Closeout / Regression update needed：walkthrough required；Regression SSoT/Cadence Ledger only if fixed gate semantics change，本任务仅引用 RG-002/RG-008，不新增 gate
