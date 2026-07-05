# hybrid retriever best effort fallback

Task Contract: harness-task/v1
Task Package Index: required

## 目标

为 `HybridRetriever` 增加最小 best-effort fallback：单个子 `Retriever` 失败时跳过该路，保留成功路结果；全部失败才失败。

## 范围

- 做什么：修改 core SDK `HybridRetriever` 调用子检索器的异常处理；补 `HybridRetrieverTest` 回归；更新 docs-site hybrid retrieval 页面；同步 RG-001/RG-007/RG-008 和 Cadence Ledger。
- 不做什么：不新增 `FallbackRetriever`、`RetrievalFailurePolicy`、options/config、并行执行、重试、超时、熔断或 metrics；不改 `RagService` public API。
- 主要风险：过度抽象会增加用户使用负担；过度吞异常会掩盖 fusion 逻辑 bug。因此本轮只捕获子检索器调用/结果规范化阶段异常，fusion 仍按现有逻辑执行。

## 预算选择

选择预算：simple

选择理由：变更点集中在一个 core 类、一个测试类和一处 docs-site 文档；没有新 public API，没有跨模块运行时 wiring。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/HybridRetriever.java | 目标 fallback 行为所在类 | coordinator |
| C-002 | test | TARGET:ai4j/src/test/java/io/github/lnyocly/ai4j/rag/HybridRetrieverTest.java | 现有 hybrid fusion 回归面 | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/hybrid-retrieval.md | 用户理解 hybrid 行为的说明页 | coordinator |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md; TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 固定回归证据登记 | coordinator |

## 步骤

1. 在 `HybridRetriever` 内为每个非空子 `Retriever` 调用增加 try/catch，记录第一个失败并跳过失败路。
2. 补充单测覆盖单路失败返回成功结果、全部失败抛第一个异常、成功空结果不误抛。
3. 更新 docs-site hybrid retrieval 页面说明 best-effort 降级边界。
4. 运行 targeted/core/docs/package/diff hygiene gates 并写入 task/regression 记录。
5. 提交、PR、合并并清理 worktree/分支。

## 验收标准

- [x] 至少一路子检索器成功时，`HybridRetriever.retrieve(...)` 返回成功结果。
- [x] 所有非空子检索器失败时，抛出第一个失败异常。
- [x] 子检索器成功但返回空列表时，返回空结果而不是误判失败。
- [x] docs-site 写明 best-effort 边界和未做 retry/timeout/circuit breaker。
- [x] RG-001、RG-007、RG-008 相关本地验证通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\hybrid-retriever-best-effort`
- 分支：`feature/hybrid-retriever-best-effort`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`feat/per-node-latency`
- 未使用 worktree 的原因：不适用，已使用独立 worktree。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如果需要新 public API 或复杂故障策略，停止并回到用户设计确认。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：self-review 未发现 material finding；验证由 automated gates 覆盖。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：simple task 无独立 `review.md`；self-review 记录在 `walkthrough.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：PR #177 RAG query planner strategy prompts；PR #178 RAG retrieval usage docs

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle closeout pending
- Closeout / Regression update needed：已更新 `walkthrough.md`、Regression SSoT、Cadence Ledger
