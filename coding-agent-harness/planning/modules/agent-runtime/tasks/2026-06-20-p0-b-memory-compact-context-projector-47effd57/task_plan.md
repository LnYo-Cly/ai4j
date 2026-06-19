# P0-B Memory Compact Context Projector

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p0-b-memory-compact-context-projector-47effd57/artifacts/preset/2026-06-19T18-43-12-069Z
Task Package Index: required

## 目标

把 `ai4j-agent` 的 memory、compact 和 model context 分层落到可用 API：runtime prompt 构造前可按预算投影上下文；session 可执行 compact 并把结构化结果保存进 snapshot；docs-site 说明能力边界和用法。

## 范围

- 做什么：context projector API、compact policy/result API、runtime projection、session compact snapshot/save/resume、定向测试、docs-site 技术文档。
- 不做什么：不做模型供应商绑定，不使用 provider token；不做 token 级精确计数；不做 event log 到 structured fields 的模型语义抽取；不实现插件 lifecycle hooks、Sandbox SPI、Blueprint YAML、CLI `/sandbox`。
- 主要风险：Compact 被误解成智能总结器；runtime projection 不能只覆盖 ReAct；`MEMORY_COMPRESS` 事件需要进入 session event log 和 stream listener。

## 预算选择

选择预算：complex

选择理由：本任务跨 `ai4j-agent` 生产代码、测试、docs-site 和 module task package，需要完整 references/artifacts/review/walkthrough。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory | 现有 memory / compressor / snapshot 语义 | coordinator / reviewer / worker |
| C-002 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime | Prompt 构造和 runtime event 发布位置 | coordinator / reviewer / worker |
| C-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/session | P0-A session snapshot/store/event log 基础 | coordinator / reviewer / worker |
| C-004 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-complete-planning-refresh.md | 总体 Agent SDK roadmap 与 P0-B 边界 | coordinator / reviewer / worker |
| C-005 | docs | TARGET:docs-site/docs/agent/session-runtime.md | P0-A docs，需要更新 P0-B 状态 | coordinator / reviewer |
| C-006 | docs | TARGET:docs-site/docs/agent/sdk-roadmap.md | Agent SDK roadmap，需要标记 P0-B foundation | coordinator / reviewer |

## 步骤

1. 诊断现有 `AgentMemory`、runtime prompt 和 P0-A session snapshot/store 扩展点。
2. 新增 context projector 与 compact policy API。
3. 将 projector 接入 `BaseAgentRuntime` 和 `CodeActRuntime` prompt 构造。
4. 将 compact result 接入 `AgentSession` snapshot/save/resume。
5. 新增 `AgentMemoryCompactContextProjectorTest` 覆盖 projector、runtime、compact policy、session save/resume。
6. 更新 docs-site `agent/memory-compact-context.md`、`session-runtime.md`、`sdk-roadmap.md` 和 sidebar。
7. 运行 targeted Maven、broad Maven、docs-site build、Harness status。
8. 提交、推送、PR、CI、merge。

## 验收标准

- [ ] `ContextProjector` 能保留 pinned prefix 和 recent tail，并返回 `ContextReport`。
- [ ] Runtime prompt 构造会应用 context projector，且 `MEMORY_COMPRESS` 事件进入 publisher/listener/session event log。
- [ ] `StructuredSummaryCompactPolicy` 返回带 memory、summary、context report 的 `CompactResult`。
- [ ] `AgentSession.compact(...)` 能更新 memory、保存 last compact result，并随 snapshot/store/resume 保留。
- [ ] `mvn -pl ai4j-agent "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false test` 通过。
- [ ] `mvn -pl ai4j-agent -am -DskipTests=false test` 通过。
- [ ] `docs-site` build 通过。
- [ ] Harness status failures=0；dirty warning 仅为待提交工作树状态。

## 工作树（Worktree）

- 路径：TARGET:.worktrees/feature/agent-memory-compact-context
- 分支：feature/agent-memory-compact-context
- Worker owner：coordinator
- Worker handoff commit required：yes
- Coordinator integration branch：main
- 未使用 worktree 的原因：不适用；本任务已使用独立 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：已授权继续推进本路线，但本任务自身不是 long-running task。
- Stop Condition 摘要：如果转入 P0-C/plugin lifecycle、P1 Blueprint、P2 Sandbox 或真实 provider 测试，停止并另开任务。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self + PR checks
- No-finding 要求：self-review 无 material finding；CI checks 通过后再 merge。

## 关联

- 相关 Regression Gate：RG-002 `mvn -pl ai4j-agent -am -DskipTests=false test`；RG-008 docs-site build。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P0-A `MODULES/agent-runtime/2026-06-20-p0-a-agentsession-runtime-container-389dbf12`

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：P0-B-memory-compact-context-projector
- Module Plan：TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-pr
- Registry update needed：agent-runtime module plan should record P0-B active/verified status before review.
- Harness Ledger update needed：task lifecycle CLI 自动同步
- Closeout / Regression update needed：progress/review/walkthrough 记录 targeted/broad/docs/harness 证据。

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
