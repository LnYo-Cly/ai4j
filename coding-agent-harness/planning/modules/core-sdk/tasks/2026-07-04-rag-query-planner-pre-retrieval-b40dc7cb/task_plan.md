# RAG query planner pre retrieval

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/core-sdk/tasks/2026-07-04-rag-query-planner-pre-retrieval-b40dc7cb/artifacts/preset/2026-07-04T05-17-25-371Z
Task Package Index: required

## 目标

在 Core SDK 的 RAG 检索前增加可选 Query Planner，使 rewrite、multi-query、HyDE、step-back 等策略能开箱或自定义接入，同时保持 `rag.search(RagQuery)` 调用形态不变。

## 范围

- 做什么：新增 `RagQueryPlanner` / `RagQueryPlan` / `RagQueryVariant` API；新增 `ModelRagQueryPlanner`；在 `DefaultRagService` 中执行 planner、融合多 query variants、保留原 query；补 docs-site Query Planning 页面和导航。
- 不做什么：不做 Agent/tool 意图路由；不做 Spring Boot 自动配置；不默认开启额外 LLM 调用；不做 live provider 调用。
- 主要风险：命名和层级过度泛化；planner 多 query 增加成本；原 query 如果被覆盖会影响 rerank/answer grounding。

## 预算选择

选择预算：standard

选择理由：涉及 core SDK 公共 API、RAG 行为、测试和 docs-site，但范围集中在单一 RAG surface，不需要 complex 外部 artifact。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/DefaultRagService.java | RAG 检索主链路入口 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/HybridRetriever.java | 区分“query variants 融合”和“多 retriever 融合” | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/overview.md | Search & RAG 主文档入口 | coordinator |
| C-004 | standard | TARGET:docs/11-REFERENCE/testing-standard.md | 回归 gate 选择依据 | coordinator |

## 步骤

1. 创建 feature worktree 和 module task。
2. 实现 RAG-only planner API、模型 planner、DefaultRagService 集成和 AiService 工厂入口。
3. 补单元测试覆盖兼容路径、planner 执行、原 query 保留、planner fallback、模型 planner JSON 解析。
4. 更新 docs-site Query Planning 页面、overview 和 sidebar。
5. 运行 RG-001/RG-007/RG-008 gate，更新治理和 walkthrough。
6. 提交 PR，合并后删除远端分支、本地 worktree 和本地分支。

## 验收标准

- [x] 无 planner 的 `DefaultRagService` 结果和 trace 兼容旧路径。
- [x] planner 在 retriever 前执行，多 variants 被执行并融合。
- [x] rerank 和 result query 使用原 query，不被 rewrite 覆盖。
- [x] planner 异常或空结果回退原 query，并记录 `RagTrace.queryPlan.fallback`。
- [x] docs-site `query-planning.md` 可构建并从 sidebar 访问。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\rag-query-planner-rag-only`
- 分支：`feature/rag-query-planner-rag-only`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`feat/per-node-latency`
- 未使用 worktree 的原因：不适用，已使用独立 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：若 scope 扩大到 Agent/tool 路由或默认开启 LLM planner，则暂停确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：`review.md`
- Reviewer：self
- No-finding 要求：self-review 无阻塞发现；本地 gate 覆盖 core/docs/package。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：core-sdk
- Step：不适用
- Module Plan：`coding-agent-harness/planning/modules/core-sdk/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：core-sdk task 已由 harness lifecycle 同步
- Harness Ledger update needed：task-review 后由 lifecycle CLI 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | core-sdk |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/core-sdk/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/core-sdk/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/core-sdk/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
