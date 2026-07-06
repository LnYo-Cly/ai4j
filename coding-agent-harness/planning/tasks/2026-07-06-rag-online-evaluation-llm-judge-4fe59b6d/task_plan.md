# rag online evaluation llm judge

Task Contract: harness-task/v1
Task Package Index: required

## 目标

在 RAG 回答生成后提供一个显式、可替换的在线 LLM judge，用于给本次 RAG 结果记录 faithfulness / context relevance / answer relevance 分数。

## 范围

- 做什么：新增最小 `RagJudge` / `RagOnlineEvaluator` API、chat-backed 默认 judge、`RagTrace` 可选字段、`AiService` 便利入口、单测和 docs-site 用法说明。
- 不做什么：不把 judge 接入 `RagService.search(...)` 的默认执行链，不做多 provider 适配矩阵，不做批量评测平台，不做成本 pricing、streaming trace、error 结构化后续项。
- 主要风险：LLM judge 是模型判断而不是强证明；如果默认自动执行会增加延迟和成本，所以必须保持显式调用。

## 预算选择

选择预算：simple

选择理由：这是 core RAG 的小型可选能力，公共面很窄；需要 task/worktree/docs-site/回归，但不需要复杂子任务、worker handoff 或对抗审查包。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag | RAG result、trace、offline evaluator 和 query planner 的现有边界 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java | 现有 `getRagService(...)` / `getModelReranker(...)` 风格决定便利入口放置方式 | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/citations-and-trace.md | 用户侧解释 trace/citation 边界和新增在线 judge 用法 | coordinator |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md; TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 回归面和本轮 SRB 记录 | coordinator |

## 步骤

1. 在 `ai4j` RAG 包内新增 `RagJudge`、request/result DTO、`RagOnlineEvaluator` 和 `ChatRagJudge`。
2. 给 `RagTrace` 增加可选 `judgeEvaluation`，给 `AiService` 增加 `getRagOnlineEvaluator(platform, model)`。
3. 增加 deterministic unit tests 覆盖 judge 调用、trace 写回和便利入口。
4. 更新 docs-site RAG citation/trace 页面，说明回答后显式评估和边界。
5. 运行 RG-001、RG-007、RG-008 与 diff hygiene，并同步回归文档/任务 closeout。

## 验收标准

- [x] `RagService.search(...)` 不隐式调用 judge。
- [x] `RagOnlineEvaluator.evaluate(...)` 会把 judge 结果写入 `RagTrace.judgeEvaluation`。
- [x] 默认 `ChatRagJudge` 只依赖现有 `IChatService`，且用户可替换 `RagJudge`。
- [x] docs-site 给出使用示例和 LLM judge 非强证明边界。
- [x] RAG 定向、core 全量、docs-site typecheck/build、package smoke、diff hygiene 均通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\rag-online-evaluation`
- 分支：`feature/rag-online-evaluation`
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：`main`
- 未使用 worktree 的原因：不适用，已使用独立 worktree；主 checkout 保持不动。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：若需要 live provider、自动默认评估或评估平台，停止并另开任务。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：自检无 open material finding；测试和 docs gate 覆盖本轮最小 API。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：simple task 无独立 `review.md`；审查结论记录在 `walkthrough.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：RAG query planner strategy prompts / agent trace streaming error structure 后续队列中的 online evaluation 项

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle closeout after PR
- Closeout / Regression update needed：`walkthrough.md`、Regression SSoT、Cadence Ledger 已更新
