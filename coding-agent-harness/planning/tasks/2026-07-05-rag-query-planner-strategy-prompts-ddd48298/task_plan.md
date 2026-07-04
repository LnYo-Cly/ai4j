# rag query planner strategy prompts

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让 RAG 模型 query planner 默认只做 rewrite，并让显式多策略走独立 prompt。

## 范围

- 做什么：修改 `ModelRagQueryPlanner`、补测试、更新 docs-site Query Planning/overview、更新 RG/SRB 证据。
- 不做什么：不新增公共 API、不新增 retrieval strategy 抽象、不改 hybrid retriever、不扩展到 agent/tool routing。
- 主要风险：多策略会增加模型调用次数；文档必须明确默认轻量路径和显式多策略成本。

## 预算选择

选择预算：simple

选择理由：只修正一个已有实现类、一个测试类和两篇 docs-site 文档，不需要新架构。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/ModelRagQueryPlanner.java | 当前一次 prompt 生成所有策略的问题点 | coordinator |
| C-002 | code | TARGET:ai4j/src/test/java/io/github/lnyocly/ai4j/rag/ModelRagQueryPlannerTest.java | 固定默认 rewrite 与 per-strategy prompt 行为 | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/query-planning.md | 用户理解默认和显式多策略的主文档 | coordinator |

## 步骤

1. 修改 `ModelRagQueryPlanner` 默认策略为 `REWRITE`。
2. 显式策略列表按策略循环调用模型，并过滤非当前策略结果。
3. 更新单测、docs-site、Regression SSoT/Cadence Ledger。
4. 运行 RAG 定向、core 全量、docs-site、package smoke。

## 验收标准

- [x] 默认模型 planner 只调用一次 rewrite prompt。
- [x] 显式多策略时分别调用 rewrite / multi-query / HyDE / step-back prompt。
- [x] 不新增公共 API 或 retrieval strategy 抽象。
- [x] `mvn -pl ai4j -am -DskipTests=false test`、docs-site build、`mvn -DskipTests package` 通过。

## 工作树（Worktree）

- 路径：`.worktrees/feature/rag-query-planner-strategy-prompts`
- 分支：`feature/rag-query-planner-strategy-prompts`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`feat/per-node-latency`
- 未使用 worktree 的原因：不适用，已使用隔离 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：需要新增公共 API 或协议适配层时停止确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：RAG 定向测试覆盖行为边界。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：不适用（simple task self-check）
- Generated Ledger：由 lifecycle CLI 重建
- 前置任务：PR #176 RAG query planner pre-retrieval

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：已由 lifecycle CLI 同步
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` 已更新
