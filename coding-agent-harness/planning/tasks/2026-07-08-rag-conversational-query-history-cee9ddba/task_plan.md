# rag conversational query history

Task Contract: harness-task/v1
Task Package Index: required

## 目标

让 RAG Query Planning 可选地使用已有 core `ChatMemoryItem` 对话历史完成 conversational rewrite，不新增 RAG 专用 memory。

## 范围

- 做什么：给 `RagQuery` 增加 `history`；让 `ModelRagQueryPlanner` 将 history 写入策略 prompt；`DefaultRagService` 复制 planned query 时保留 history；更新 tests/docs/regression 记录。
- 不做什么：不改 `AgentMemory`，不新增 `RagMemory` / `ConversationalRagMemory`，不接 live provider。
- 主要风险：history 过长会增加 planner prompt token；交给已有 `ChatMemoryPolicy` 控制窗口/摘要。

## 预算选择

选择预算：simple

选择理由：复用已有 core memory 模型，改动集中在 RAG query/planner/docs/test。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag | RAG query planning 主链路 | coordinator |
| C-002 | code | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/memory | 复用已有 `ChatMemoryItem` / `InMemoryChatMemory` | coordinator |
| C-003 | docs | TARGET:docs-site/docs/core-sdk/search-and-rag/query-planning.md | 用户使用说明 | coordinator |
| C-004 | governance | TARGET:docs/05-TEST-QA/Regression-SSoT.md; TARGET:docs/05-TEST-QA/Cadence-Ledger.md | 固定回归证据登记 | coordinator |

## 步骤

1. 复用 `ChatMemoryItem` 给 `RagQuery` 增加 optional history。
2. 更新 planner prompt 和 planned query copy 逻辑。
3. 更新 tests/docs/regression/task closeout。

## 验收标准

- [x] `ModelRagQueryPlanner` prompt 包含 `RagQuery.history`。
- [x] planned variant retrieval 不丢失 history。
- [x] docs-site 展示 `InMemoryChatMemory` 使用方式，并明确不新增 RAG memory。
- [x] RG-001 / RG-007 / RG-008 本地通过。

## 工作树（Worktree）

- 路径：`G:\My_Project\java\ai4j-sdk\.worktrees\feature\rag-history-query-planner`
- 分支：`feature/rag-history-query-planner`
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：`origin/feat/per-node-latency`
- 未使用 worktree 的原因：不适用

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：本地验证或提交阻塞时停止。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：本地 tests/docs/package gates 通过。

## 关联

- 相关 Regression Gate：RG-001、RG-007、RG-008
- 审查报告：不适用（simple task self-check）
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：PR #176 / SRB-062 RAG query planner pre-retrieval

## 模块关联（启用模块并行时填写）

- Module：不适用
- Step：不适用
- Module Plan：不适用

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task complete 时同步
- Closeout / Regression update needed：已更新 `docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`、`walkthrough.md`
